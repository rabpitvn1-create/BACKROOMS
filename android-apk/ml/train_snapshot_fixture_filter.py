#!/usr/bin/env python3
"""Train a tiny LiteRT filter that answers one question: fixture or not-fixture.

The deterministic Snapshot detector remains authoritative for candidate geometry and
appearance measurements. HAKU supplies semantic fixture/not-fixture labels only. The
student learns from normalized detector features, splits by source-image SHA to prevent
leakage, and exports a tiny TFLite logistic classifier.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import pathlib
from collections import defaultdict
from dataclasses import dataclass
from typing import Any, Iterable

SEED = 20260906
MODEL_VERSION = "snapshot-fixture-filter-v0"
FEATURE_NAMES = (
    "x",
    "y",
    "w",
    "h",
    "log_aspect",
    "avg_contrast",
    "avg_luma",
    "detector_confidence",
    "fill",
    "extent_area",
)


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

        for key in ("x", "y", "w", "h", "avg_contrast", "avg_luma", "detector_confidence", "fill"):
            value = _finite_number(candidate.get(key), f"candidate.{key}")
            if not 0.0 <= value <= 1.0:
                raise TrainingError(f"candidate.{key} out of range at line {line_number}")
        aspect = _finite_number(candidate.get("aspect"), "candidate.aspect")
        if aspect <= 0:
            raise TrainingError(f"candidate.aspect must be positive at line {line_number}")
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
    sample_list = samples if isinstance(samples, list) else list(samples)
    positives = sum(sample.label for sample in sample_list)
    return positives, len(sample_list) - positives


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
    negative_shas = [sha for sha in order if any(sample.label == 0 for sample in groups[sha])]
    positive_shas = [sha for sha in order if any(sample.label == 1 for sample in groups[sha])]
    if not negative_shas or not positive_shas:
        raise TrainingError("cannot build holdout with both fixture and negative examples")

    test_shas: list[str] = []
    # A single negative-bearing source makes specificity effectively one-image luck.
    # When the corpus permits it, hold out two distinct negative-bearing images while
    # always leaving at least one such image in training.
    negative_holdouts = min(2, max(1, len(negative_shas) - 1), target_groups)
    test_shas.extend(negative_shas[:negative_holdouts])

    positive_sha = next((sha for sha in positive_shas if sha not in test_shas), None)
    if positive_sha is not None and len(test_shas) < target_groups:
        test_shas.append(positive_sha)

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


def candidate_features(candidate: dict[str, Any]) -> list[float]:
    aspect = max(1e-6, float(candidate["aspect"]))
    return [
        float(candidate["x"]),
        float(candidate["y"]),
        float(candidate["w"]),
        float(candidate["h"]),
        max(-3.0, min(3.0, math.log(aspect))) / 3.0,
        float(candidate["avg_contrast"]),
        float(candidate["avg_luma"]),
        float(candidate["detector_confidence"]),
        float(candidate["fill"]),
        float(candidate["w"]) * float(candidate["h"]),
    ]


def build_feature_arrays(samples: list[Sample]) -> tuple[Any, Any]:
    try:
        import numpy as np
    except ImportError as error:
        raise TrainingError("numpy is required") from error
    x = np.asarray([candidate_features(sample.candidate) for sample in samples], dtype=np.float32)
    y = np.asarray([float(sample.label) for sample in samples], dtype=np.float32)
    return x, y


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


def _fit_logistic_student(x_train: Any, y_train: Any) -> tuple[Any, Any, dict[str, Any]]:
    try:
        import numpy as np
        from sklearn.linear_model import LogisticRegression
        from sklearn.preprocessing import StandardScaler
    except ImportError as error:
        raise TrainingError("numpy and scikit-learn are required for training") from error

    scaler = StandardScaler()
    normalized = scaler.fit_transform(x_train)
    classifier = LogisticRegression(
        C=0.1,
        class_weight="balanced",
        max_iter=2000,
        random_state=SEED,
        solver="lbfgs",
    )
    classifier.fit(normalized, y_train.astype(np.int32))

    scale = np.where(scaler.scale_ == 0.0, 1.0, scaler.scale_)
    normalized_coef = classifier.coef_[0].astype(np.float64)
    raw_coef = normalized_coef / scale
    raw_intercept = float(classifier.intercept_[0] - np.dot(normalized_coef, scaler.mean_ / scale))
    metadata = {
        "estimator": "balanced_logistic_regression",
        "C": 0.1,
        "solver": "lbfgs",
        "max_iter": 2000,
    }
    return raw_coef.astype(np.float32), np.asarray([raw_intercept], dtype=np.float32), metadata


def train(args: argparse.Namespace) -> dict[str, Any]:
    try:
        import numpy as np
        import tensorflow as tf
    except ImportError as error:
        raise TrainingError("tensorflow and numpy are required for TFLite export") from error

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
    x_train, y_train = build_feature_arrays(train_samples)
    x_test, y_test = build_feature_arrays(test_samples)
    raw_coef, raw_intercept, training_metadata = _fit_logistic_student(x_train, y_train)

    model = tf.keras.Sequential(
        [
            tf.keras.layers.Input(shape=(len(FEATURE_NAMES),), name="snapshot_candidate_features"),
            tf.keras.layers.Dense(1, activation="sigmoid", name="fixture_probability"),
        ],
        name=MODEL_VERSION,
    )
    model.layers[-1].set_weights([raw_coef.reshape((-1, 1)), raw_intercept])

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
    for features in x_test:
        interpreter.set_tensor(
            input_info["index"],
            np.expand_dims(features, axis=0).astype(input_info["dtype"]),
        )
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

    test_pos, test_neg = _class_counts(test_samples)
    report = {
        "model_version": MODEL_VERSION,
        "seed": SEED,
        "input_shape": [1, len(FEATURE_NAMES)],
        "input_features": list(FEATURE_NAMES),
        "output": "fixture_probability",
        "corpus": corpus,
        "training": training_metadata,
        "split": {
            "train_unique_images": len({sample.sha256 for sample in train_samples}),
            "test_unique_images": len({sample.sha256 for sample in test_samples}),
            "train_candidates": len(train_samples),
            "test_candidates": len(test_samples),
            "test_fixtures": test_pos,
            "test_negatives": test_neg,
            "image_level_split": True,
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
