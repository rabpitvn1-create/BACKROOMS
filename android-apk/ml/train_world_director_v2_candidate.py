#!/usr/bin/env python3
"""Train an artifact-only WorldDirector V2 LiteRT candidate from Gemini + Haku teacher labels.

V2 expands the hashed feature space to 16,384 dimensions while keeping the same four proposal
outputs. The current V1 production model is treated as a baseline: V2 must demonstrate a measurable
held-out teacher gain while preserving the deterministic seed contract before it can be considered
promotion-eligible. This script never writes the production model asset.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
import pathlib
import re
from collections import Counter

import numpy as np
from scipy import sparse

V1_FEATURES = 4096
V2_FEATURES = 16384
LABELS = ["NONE", "MAZE_PRESSURE", "ENTITY_PRESSURE", "ITEM_OPPORTUNITY"]
TOKEN = re.compile(r"[\w]+", re.UNICODE)
V2_MARKER = " contract_world_director_pressure_v2"


def java_hash(text: str) -> int:
    value = 0
    for char in text:
        value = (31 * value + ord(char)) & 0xFFFFFFFF
    return value if value < 0x80000000 else value - 0x100000000


def feature_counts(text: str, feature_count: int) -> dict[int, float]:
    tokens = TOKEN.findall(text.lower())
    features = [f"w:{token}" for token in tokens]
    features += [f"b:{left}|{right}" for left, right in zip(tokens, tokens[1:])]
    compact = " ".join(tokens)
    for size in range(3, 6):
        features += [f"c{size}:{compact[index:index + size]}" for index in range(max(0, len(compact) - size + 1))]
    counts: dict[int, float] = {}
    for feature in features:
        index = (java_hash(feature) & 0x7FFFFFFF) % feature_count
        counts[index] = counts.get(index, 0.0) + 1.0
    norm = math.sqrt(sum(value * value for value in counts.values())) or 1.0
    return {index: value / norm for index, value in counts.items()}


def vectorize_many(texts: list[str], feature_count: int) -> sparse.csr_matrix:
    rows = []
    cols = []
    data = []
    for row_index, text in enumerate(texts):
        for col, value in feature_counts(text, feature_count).items():
            rows.append(row_index)
            cols.append(col)
            data.append(value)
    return sparse.csr_matrix(
        (np.asarray(data, dtype=np.float32), (rows, cols)),
        shape=(len(texts), feature_count),
        dtype=np.float32,
    )


def vectorize_one(text: str, feature_count: int) -> np.ndarray:
    result = np.zeros(feature_count, dtype=np.float32)
    for index, value in feature_counts(text, feature_count).items():
        result[index] = value
    return result


def read_rows(path: pathlib.Path) -> list[dict]:
    return list(csv.DictReader(path.open(encoding="utf-8")))


def extract_v1(text: str) -> str:
    return text.split(V2_MARKER, 1)[0].strip()


def fit(rows: list[dict], feature_count: int, weights: list[float] | None = None):
    from sklearn.linear_model import LogisticRegression

    x = vectorize_many([row["text"] for row in rows], feature_count)
    y = np.asarray([row["intent"] for row in rows])
    classifier = LogisticRegression(
        max_iter=5000,
        C=8.0,
        class_weight="balanced",
        solver="liblinear",
    )
    classifier.fit(x, y, sample_weight=np.asarray(weights, dtype=np.float64) if weights is not None else None)
    return classifier


def metrics(classifier, rows: list[dict], feature_count: int, text_transform=lambda value: value) -> dict:
    if not rows:
        return {"rows": 0, "accuracy": 0.0, "perLabelRecall": {}, "predictionCounts": {}}
    x = vectorize_many([text_transform(row["text"]) for row in rows], feature_count)
    y = np.asarray([row["intent"] for row in rows])
    predicted = classifier.predict(x)
    per_label = {}
    for label in LABELS:
        mask = y == label
        if np.any(mask):
            per_label[label] = float(np.mean(predicted[mask] == y[mask]))
    return {
        "rows": len(rows),
        "accuracy": float(np.mean(predicted == y)),
        "perLabelRecall": per_label,
        "predictionCounts": dict(Counter(predicted.tolist())),
    }


def source_metrics(classifier, rows: list[dict]) -> dict:
    sources = sorted({row.get("teacher_source", "UNKNOWN") for row in rows})
    return {source: metrics(classifier, [row for row in rows if row.get("teacher_source") == source], V2_FEATURES) for source in sources}


def export_tflite(classifier, output: pathlib.Path) -> None:
    import tensorflow as tf

    weights = np.zeros((V2_FEATURES, len(LABELS)), dtype=np.float32)
    bias = np.zeros((len(LABELS),), dtype=np.float32)
    for source_index, class_name in enumerate(classifier.classes_):
        target_index = LABELS.index(class_name)
        weights[:, target_index] = classifier.coef_[source_index]
        bias[target_index] = classifier.intercept_[source_index]

    inputs = tf.keras.Input((V2_FEATURES,), name="director_v2_hashed_features")
    outputs = tf.keras.layers.Dense(len(LABELS), name="director_v2_logits")(inputs)
    model = tf.keras.Model(inputs, outputs)
    model.get_layer("director_v2_logits").set_weights([weights, bias])
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(converter.convert())


def verify_export(output: pathlib.Path, rows: list[dict]) -> dict:
    import tensorflow as tf

    interpreter = tf.lite.Interpreter(model_path=str(output))
    interpreter.allocate_tensors()
    input_info = interpreter.get_input_details()[0]
    output_info = interpreter.get_output_details()[0]
    if list(input_info["shape"]) != [1, V2_FEATURES] or int(output_info["shape"][-1]) != len(LABELS):
        raise SystemExit("WorldDirector V2 LiteRT tensor contract mismatch")

    expected = np.asarray([row["intent"] for row in rows])
    predicted = []
    confidences = []
    margins = []
    for row in rows:
        sample = vectorize_one(row["text"], V2_FEATURES)
        interpreter.set_tensor(input_info["index"], sample.reshape(1, V2_FEATURES).astype(input_info["dtype"]))
        interpreter.invoke()
        logits = interpreter.get_tensor(output_info["index"])[0].astype(np.float64)
        shifted = logits - logits.max()
        probabilities = np.exp(shifted) / np.exp(shifted).sum()
        ordered = np.sort(probabilities)
        predicted.append(LABELS[int(np.argmax(probabilities))])
        confidences.append(float(ordered[-1]))
        margins.append(float(ordered[-1] - ordered[-2]))

    predicted = np.asarray(predicted)
    high = (np.asarray(confidences) >= 0.40) & (np.asarray(margins) >= 0.15)
    per_label = {}
    for label in LABELS:
        mask = expected == label
        if np.any(mask):
            per_label[label] = float(np.mean(predicted[mask] == expected[mask]))
    return {
        "accuracy": float(np.mean(predicted == expected)),
        "highConfidenceCoverage": float(np.mean(high)),
        "acceptedAccuracy": float(np.mean(predicted[high] == expected[high])) if np.any(high) else 0.0,
        "perLabelRecall": per_label,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--teacher-dataset", required=True)
    parser.add_argument("--seed-dataset", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--report", required=True)
    args = parser.parse_args()

    teacher = read_rows(pathlib.Path(args.teacher_dataset))
    seed = read_rows(pathlib.Path(args.seed_dataset))
    teacher_train = [row for row in teacher if row["split"] == "train"]
    teacher_test = [row for row in teacher if row["split"] == "test"]
    seed_train = [row for row in seed if row["split"] == "train"]
    seed_test = [row for row in seed if row["split"] == "test"]
    if not teacher_train or not teacher_test or not seed_train or not seed_test:
        raise SystemExit("V2 training requires teacher and seed train/test rows")

    # Current V1 policy baseline on the exact V1 prefix of each V2 teacher sample.
    v1_baseline = fit(seed_train, V1_FEATURES)
    baseline_teacher = metrics(v1_baseline, teacher_test, V1_FEATURES, extract_v1)
    baseline_seed = metrics(v1_baseline, seed_test, V1_FEATURES)

    teacher_weights = [float(row.get("sample_weight") or 1.0) for row in teacher_train]
    teacher_only = fit(teacher_train, V2_FEATURES, teacher_weights)

    blend_rows = teacher_train + seed_train
    blend_weights = teacher_weights + [1.0] * len(seed_train)
    blend = fit(blend_rows, V2_FEATURES, blend_weights)

    strong_rows = teacher_train + seed_train
    strong_weights = teacher_weights + [2.0] * len(seed_train)
    strong = fit(strong_rows, V2_FEATURES, strong_weights)

    candidates = []
    for name, classifier in (
        ("teacher_only", teacher_only),
        ("teacher_seed_anchor", blend),
        ("teacher_seed_strong_anchor", strong),
    ):
        teacher_metrics = metrics(classifier, teacher_test, V2_FEATURES)
        seed_metrics = metrics(classifier, seed_test, V2_FEATURES)
        agreement_rows = [row for row in teacher_test if row.get("teacher_source") == "GEMINI_HAKU_AGREE"]
        agreement_metrics = metrics(classifier, agreement_rows, V2_FEATURES)
        score = teacher_metrics["accuracy"] + 0.30 * seed_metrics["accuracy"] + 0.15 * agreement_metrics["accuracy"]
        candidates.append((score, name, classifier, teacher_metrics, seed_metrics, agreement_metrics))

    _, selected_name, selected, selected_teacher, selected_seed, selected_agreement = max(candidates, key=lambda item: item[0])
    output = pathlib.Path(args.output)
    export_tflite(selected, output)
    exported = verify_export(output, teacher_test)
    teacher_gain = selected_teacher["accuracy"] - baseline_teacher["accuracy"]
    min_recall = min(exported["perLabelRecall"].values()) if exported["perLabelRecall"] else 0.0

    promotion_eligible = (
        teacher_gain >= 0.02
        and exported["accuracy"] >= 0.90
        and exported["acceptedAccuracy"] >= 0.95
        and exported["highConfidenceCoverage"] >= 0.70
        and selected_seed["accuracy"] >= 0.95
        and min_recall >= 0.80
        and (selected_agreement["rows"] == 0 or selected_agreement["accuracy"] >= 0.90)
        and output.stat().st_size <= 5 * 1024 * 1024
    )

    report = {
        "contract": "WORLD_DIRECTOR_PRESSURE_V2_MULTITEACHER_CANDIDATE",
        "featureCount": V2_FEATURES,
        "labels": LABELS,
        "selected": selected_name,
        "modelBytes": output.stat().st_size,
        "promotionEligible": promotion_eligible,
        "productionAssetModified": False,
        "teacherRows": len(teacher),
        "seedRows": len(seed),
        "baselineV1": {"teacherTest": baseline_teacher, "seedTest": baseline_seed},
        "selectedV2": {
            "teacherTest": selected_teacher,
            "seedTest": selected_seed,
            "agreementTest": selected_agreement,
            "sourceMetrics": source_metrics(selected, teacher_test),
        },
        "teacherAccuracyGainOverV1": teacher_gain,
        "exportedCandidate": exported,
    }
    pathlib.Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
