#!/usr/bin/env python3
"""Train a tiny LiteRT filter that answers one question: fixture or not-fixture.

The deterministic Snapshot detector remains responsible for geometry. HAKU supplies
semantic labels only. This trainer reopens the original image, crops context around
accepted detector candidates, splits by image SHA to prevent leakage, and exports a
small TFLite binary classifier.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import pathlib
import random
from collections import defaultdict
from dataclasses import dataclass
from typing import Any, Iterable

SEED = 20260906
INPUT_SIZE = 48
MODEL_VERSION = "snapshot-fixture-filter-v0"


class TrainingError(RuntimeError):
    pass


@dataclass(frozen=True)
class Sample:
    image: pathlib.Path
    sha256: str
    candidate: dict[str, Any]
    label: int
    confidence: float


def _finite_number(value: Any, name: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise TrainingError(f"{name} must be numeric")
    result = float(value)
    if not math.isfinite(result):
        raise TrainingError(f"{name} must be finite")
    return result


def _load_records(path: pathlib.Path, min_teacher_confidence: float) -> list[Sample]:
    if not path.is_file():
        raise TrainingError(f"labels file not found: {path}")
    samples: list[Sample] = []
    image_hash_cache: dict[pathlib.Path, str] = {}
    for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not raw.strip():
            continue
        try:
            record = json.loads(raw)
        except json.JSONDecodeError as error:
            raise TrainingError(f"invalid JSONL at line {line_number}: {error}") from error
        if record.get("status") != "accepted":
            continue
        teacher = record.get("teacher")
        candidate = record.get("candidate")
        if not isinstance(teacher, dict) or not isinstance(candidate, dict):
            raise TrainingError(f"accepted record missing teacher/candidate at line {line_number}")
        fixture = teacher.get("fixture")
        if not isinstance(fixture, bool):
            raise TrainingError(f"teacher.fixture must be boolean at line {line_number}")
        confidence = _finite_number(teacher.get("confidence"), "teacher.confidence")
        if not 0.0 <= confidence <= 1.0:
            raise TrainingError(f"teacher confidence out of range at line {line_number}")
        if confidence < min_teacher_confidence:
            continue
        for key in ("x", "y", "w", "h"):
            value = _finite_number(candidate.get(key), f"candidate.{key}")
            if not 0.0 <= value <= 1.0:
                raise TrainingError(f"candidate.{key} out of range at line {line_number}")
        if candidate["w"] <= 0 or candidate["h"] <= 0:
            raise TrainingError(f"candidate extent must be positive at line {line_number}")
        image = pathlib.Path(str(record.get("image", "")))
        if not image.is_file():
            raise TrainingError(f"source image missing for line {line_number}: {image}")
        expected_sha = str(record.get("sha256", ""))
        actual_sha = image_hash_cache.get(image)
        if actual_sha is None:
            actual_sha = hashlib.sha256(image.read_bytes()).hexdigest()
            image_hash_cache[image] = actual_sha
        if actual_sha != expected_sha:
            raise TrainingError(f"source image SHA mismatch at line {line_number}: {image}")
        samples.append(
            Sample(
                image=image,
                sha256=actual_sha,
                candidate=dict(candidate),
                label=1 if fixture else 0,
                confidence=confidence,
            )
        )
    if not samples:
        raise TrainingError("no accepted teacher samples survived confidence filtering")
    return samples


def _class_counts(samples: Iterable[Sample]) -> tuple[int, int]:
    positives = sum(sample.label for sample in samples)
    total = sum(1 for _ in samples) if not isinstance(samples, list) else len(samples)
    return positives, total - positives


def _stable_image_order(shas: Iterable[str]) -> list[str]:
    return sorted(set(shas), key=lambda value: hashlib.sha256(f"{SEED}:{value}".encode()).hexdigest())


def split_by_image(samples: list[Sample], test_fraction: float = 0.20) -> tuple[list[Sample], list[Sample]]:
    groups: dict[str, list[Sample]] = defaultdict(list)
    for sample in samples:
        groups[sample.sha256].append(sample)
    if len(groups) < 4:
        raise TrainingError("need at least four distinct source images for image-level split")

    order = _stable_image_order(groups)
    target_groups = max(2, int(round(len(order) * test_fraction)))
    test_shas: list[str] = []

    positive_sha = next((sha for sha in order if any(s.label == 1 for s in groups[sha])), None)
    negative_sha = next((sha for sha in order if any(s.label == 0 for s in groups[sha]) and sha != positive_sha), None)
    if positive_sha is None or negative_sha is None:
        raise TrainingError("cannot build holdout with both fixture and negative examples")
    test_shas.extend([positive_sha, negative_sha])
    for sha in order:
        if len(test_shas) >= target_groups:
            break
        if sha not in test_shas:
            test_shas.append(sha)

    test_set = set(test_shas)
    train = [sample for sample in samples if sample.sha256 not in test_set]
    test = [sample for sample in samples if sample.sha256 in test_set]
    train_pos, train_neg = _class_counts(train)
    test_pos, test_neg = _class_counts(test)
    if min(train_pos, train_neg, test_pos, test_neg) == 0:
        raise TrainingError(
            f"split lost a class: train=({train_pos},{train_neg}) test=({test_pos},{test_neg})"
        )
    return train, test


def _bounded_interval(center: float, span: float) -> tuple[float, float]:
    span = min(1.0, max(0.02, span))
    start = center - span / 2.0
    end = center + span / 2.0
    if start < 0.0:
        end -= start
        start = 0.0
    if end > 1.0:
        start -= end - 1.0
        end = 1.0
    return max(0.0, start), min(1.0, end)


def crop_candidate(image: Any, candidate: dict[str, Any], size: int = INPUT_SIZE) -> Any:
    from PIL import Image

    x = float(candidate["x"])
    y = float(candidate["y"])
    w = float(candidate["w"])
    h = float(candidate["h"])
    crop_w = min(0.95, max(0.18, w * 2.2))
    crop_h = min(0.95, max(0.18, h * 3.0))
    x0, x1 = _bounded_interval(x, crop_w)
    y0, y1 = _bounded_interval(y, crop_h)
    width, height = image.size
    box = (
        int(round(x0 * width)),
        int(round(y0 * height)),
        max(1, int(round(x1 * width))),
        max(1, int(round(y1 * height))),
    )
    resampling = getattr(Image, "Resampling", Image)
    return image.crop(box).resize((size, size), resample=resampling.BILINEAR).convert("RGB")


def _augment_train_crop(crop: Any) -> list[Any]:
    from PIL import ImageEnhance, ImageFilter

    return [
        crop,
        ImageEnhance.Brightness(crop).enhance(0.78),
        ImageEnhance.Brightness(crop).enhance(1.20),
        ImageEnhance.Contrast(crop).enhance(0.78),
        ImageEnhance.Contrast(crop).enhance(1.18),
        crop.filter(ImageFilter.GaussianBlur(radius=0.55)),
    ]


def build_arrays(samples: list[Sample], *, augment: bool) -> tuple[Any, Any]:
    try:
        import numpy as np
        from PIL import Image
    except ImportError as error:
        raise TrainingError("numpy and Pillow are required") from error

    xs: list[Any] = []
    ys: list[float] = []
    cache: dict[pathlib.Path, Any] = {}
    try:
        for sample in samples:
            image = cache.get(sample.image)
            if image is None:
                image = Image.open(sample.image).convert("RGB")
                cache[sample.image] = image
            crop = crop_candidate(image, sample.candidate)
            variants = _augment_train_crop(crop) if augment else [crop]
            for variant in variants:
                xs.append(np.asarray(variant, dtype=np.float32) / 255.0)
                ys.append(float(sample.label))
    finally:
        for image in cache.values():
            image.close()
    return np.stack(xs, axis=0), np.asarray(ys, dtype=np.float32)


def _metrics(labels: Any, probabilities: Any, threshold: float = 0.5) -> dict[str, float | int]:
    labels_list = [int(value) for value in labels]
    predictions = [1 if float(value) >= threshold else 0 for value in probabilities]
    tp = sum(1 for y, p in zip(labels_list, predictions) if y == 1 and p == 1)
    tn = sum(1 for y, p in zip(labels_list, predictions) if y == 0 and p == 0)
    fp = sum(1 for y, p in zip(labels_list, predictions) if y == 0 and p == 1)
    fn = sum(1 for y, p in zip(labels_list, predictions) if y == 1 and p == 0)
    total = max(1, len(labels_list))
    recall = tp / max(1, tp + fn)
    specificity = tn / max(1, tn + fp)
    precision = tp / max(1, tp + fp)
    return {
        "samples": len(labels_list),
        "tp": tp,
        "tn": tn,
        "fp": fp,
        "fn": fn,
        "accuracy": (tp + tn) / total,
        "precision": precision,
        "fixture_recall": recall,
        "negative_specificity": specificity,
        "false_positive_rate": fp / max(1, fp + tn),
        "balanced_accuracy": (recall + specificity) / 2.0,
    }


def train(args: argparse.Namespace) -> dict[str, Any]:
    try:
        import numpy as np
        import tensorflow as tf
    except ImportError as error:
        raise TrainingError("tensorflow and numpy are required for training") from error

    random.seed(SEED)
    np.random.seed(SEED)
    tf.keras.utils.set_random_seed(SEED)

    samples = _load_records(pathlib.Path(args.labels), args.min_teacher_confidence)
    positives, negatives = _class_counts(samples)
    unique_images = len({sample.sha256 for sample in samples})
    corpus = {
        "accepted_samples": len(samples),
        "fixtures": positives,
        "negatives": negatives,
        "unique_images": unique_images,
        "min_teacher_confidence": args.min_teacher_confidence,
    }

    failures: list[str] = []
    if len(samples) < args.min_samples:
        failures.append(f"accepted_samples {len(samples)} < {args.min_samples}")
    if positives < args.min_per_class:
        failures.append(f"fixtures {positives} < {args.min_per_class}")
    if negatives < args.min_per_class:
        failures.append(f"negatives {negatives} < {args.min_per_class}")
    if unique_images < args.min_images:
        failures.append(f"unique_images {unique_images} < {args.min_images}")
    if failures:
        raise TrainingError("Snapshot corpus is not broad enough yet: " + "; ".join(failures))

    train_samples, test_samples = split_by_image(samples)
    x_train, y_train = build_arrays(train_samples, augment=True)
    x_test, y_test = build_arrays(test_samples, augment=False)

    model = tf.keras.Sequential(
        [
            tf.keras.layers.Input(shape=(INPUT_SIZE, INPUT_SIZE, 3), name="snapshot_crop"),
            tf.keras.layers.Conv2D(8, 3, strides=2, padding="same", activation="relu"),
            tf.keras.layers.MaxPooling2D(2),
            tf.keras.layers.Conv2D(16, 3, strides=2, padding="same", activation="relu"),
            tf.keras.layers.GlobalAveragePooling2D(),
            tf.keras.layers.Dense(8, activation="relu"),
            tf.keras.layers.Dense(1, activation="sigmoid", name="fixture_probability"),
        ],
        name=MODEL_VERSION,
    )
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
        loss=tf.keras.losses.BinaryCrossentropy(),
        metrics=[tf.keras.metrics.BinaryAccuracy(name="accuracy")],
    )

    train_pos = max(1, int(y_train.sum()))
    train_neg = max(1, int(len(y_train) - y_train.sum()))
    total = train_pos + train_neg
    class_weight = {0: total / (2.0 * train_neg), 1: total / (2.0 * train_pos)}
    model.fit(
        x_train,
        y_train,
        validation_data=(x_test, y_test),
        epochs=args.epochs,
        batch_size=min(args.batch_size, len(y_train)),
        verbose=2,
        class_weight=class_weight,
        callbacks=[
            tf.keras.callbacks.EarlyStopping(
                monitor="val_loss", patience=8, restore_best_weights=True, min_delta=1e-4
            )
        ],
    )

    output = pathlib.Path(args.output)
    report_path = pathlib.Path(args.report)
    output.parent.mkdir(parents=True, exist_ok=True)
    report_path.parent.mkdir(parents=True, exist_ok=True)

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()
    output.write_bytes(tflite_model)

    interpreter = tf.lite.Interpreter(model_content=tflite_model, num_threads=1)
    interpreter.allocate_tensors()
    input_info = interpreter.get_input_details()[0]
    output_info = interpreter.get_output_details()[0]
    probabilities: list[float] = []
    for image in x_test:
        interpreter.set_tensor(input_info["index"], np.expand_dims(image, axis=0).astype(input_info["dtype"]))
        interpreter.invoke()
        probabilities.append(float(interpreter.get_tensor(output_info["index"]).reshape(-1)[0]))

    metrics = _metrics(y_test, probabilities)
    size_bytes = len(tflite_model)
    gate_failures: list[str] = []
    if metrics["balanced_accuracy"] < args.min_balanced_accuracy:
        gate_failures.append(
            f"balanced_accuracy {metrics['balanced_accuracy']:.3f} < {args.min_balanced_accuracy:.3f}"
        )
    if metrics["fixture_recall"] < args.min_fixture_recall:
        gate_failures.append(f"fixture_recall {metrics['fixture_recall']:.3f} < {args.min_fixture_recall:.3f}")
    if metrics["negative_specificity"] < args.min_negative_specificity:
        gate_failures.append(
            f"negative_specificity {metrics['negative_specificity']:.3f} < {args.min_negative_specificity:.3f}"
        )
    if size_bytes > args.max_model_bytes:
        gate_failures.append(f"model size {size_bytes} > {args.max_model_bytes}")

    report = {
        "model_version": MODEL_VERSION,
        "seed": SEED,
        "input_shape": [1, INPUT_SIZE, INPUT_SIZE, 3],
        "output": "fixture_probability",
        "corpus": corpus,
        "split": {
            "train_unique_images": len({sample.sha256 for sample in train_samples}),
            "test_unique_images": len({sample.sha256 for sample in test_samples}),
            "train_candidates": len(train_samples),
            "test_candidates": len(test_samples),
            "augmented_train_examples": int(len(y_train)),
        },
        "tflite": {
            "size_bytes": size_bytes,
            "sha256": hashlib.sha256(tflite_model).hexdigest(),
            "metrics": metrics,
        },
        "gates_passed": not gate_failures,
        "gate_failures": gate_failures,
    }
    report_path.write_text(json.dumps(report, indent=2, sort_keys=True), encoding="utf-8")
    print(json.dumps(report, sort_keys=True))
    if gate_failures:
        raise TrainingError("Snapshot fixture filter failed quality gates: " + "; ".join(gate_failures))
    return report


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    parser.add_argument("--labels", default="snapshot_teacher/haku_snapshot_candidate_labels.jsonl")
    parser.add_argument("--output", default="snapshot_teacher/backroom_snapshot_filter_v0.tflite")
    parser.add_argument("--report", default="snapshot_teacher/backroom_snapshot_filter_v0_report.json")
    parser.add_argument("--min-teacher-confidence", type=float, default=0.65)
    parser.add_argument("--min-samples", type=int, default=60)
    parser.add_argument("--min-per-class", type=int, default=18)
    parser.add_argument("--min-images", type=int, default=16)
    parser.add_argument("--epochs", type=int, default=60)
    parser.add_argument("--batch-size", type=int, default=24)
    parser.add_argument("--min-balanced-accuracy", type=float, default=0.70)
    parser.add_argument("--min-fixture-recall", type=float, default=0.70)
    parser.add_argument("--min-negative-specificity", type=float, default=0.70)
    parser.add_argument("--max-model-bytes", type=int, default=524288)
    return parser


def main() -> None:
    args = build_parser().parse_args()
    if not 0.0 <= args.min_teacher_confidence <= 1.0:
        raise SystemExit("--min-teacher-confidence must be in [0,1]")
    try:
        train(args)
    except TrainingError as error:
        raise SystemExit(str(error)) from error


if __name__ == "__main__":
    main()
