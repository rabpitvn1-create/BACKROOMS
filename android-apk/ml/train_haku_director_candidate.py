#!/usr/bin/env python3
"""Train an artifact-only LiteRT WorldDirector candidate from Haku V1 labels.

The candidate keeps the exact 4096 hashed-feature input and four proposal outputs used by
LiteRTWorldDirectorPolicy. It is never copied into the production asset path by this script.
"""
from __future__ import annotations

import argparse
import csv
import json
import pathlib
import re
from collections import Counter

import numpy as np

FEATURES = 4096
LABELS = ["NONE", "MAZE_PRESSURE", "ENTITY_PRESSURE", "ITEM_OPPORTUNITY"]
TOKEN = re.compile(r"[\w]+", re.UNICODE)


def java_hash(text: str) -> int:
    value = 0
    for char in text:
        value = (31 * value + ord(char)) & 0xFFFFFFFF
    return value if value < 0x80000000 else value - 0x100000000


def vectorize(text: str) -> np.ndarray:
    result = np.zeros(FEATURES, dtype=np.float32)
    tokens = TOKEN.findall(text.lower())
    features = [f"w:{token}" for token in tokens]
    features += [f"b:{left}|{right}" for left, right in zip(tokens, tokens[1:])]
    compact = " ".join(tokens)
    for size in range(3, 6):
        features += [f"c{size}:{compact[index:index + size]}" for index in range(max(0, len(compact) - size + 1))]
    for feature in features:
        result[(java_hash(feature) & 0x7FFFFFFF) % FEATURES] += 1.0
    norm = np.linalg.norm(result)
    return result / norm if norm else result


def read_rows(path: pathlib.Path) -> list[dict]:
    return list(csv.DictReader(path.open(encoding="utf-8")))


def metrics(classifier, rows: list[dict]) -> dict:
    if not rows:
        return {"rows": 0, "accuracy": 0.0, "perLabelRecall": {}}
    x = np.stack([vectorize(row["text"]) for row in rows])
    y = np.array([row["intent"] for row in rows])
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


def fit(rows: list[dict], weights: list[float] | None = None):
    from sklearn.linear_model import LogisticRegression

    x = np.stack([vectorize(row["text"]) for row in rows])
    y = np.array([row["intent"] for row in rows])
    classifier = LogisticRegression(max_iter=5000, C=10, class_weight="balanced")
    classifier.fit(x, y, sample_weight=np.array(weights) if weights is not None else None)
    return classifier


def export_tflite(classifier, output: pathlib.Path) -> None:
    import tensorflow as tf

    weights = np.zeros((FEATURES, len(LABELS)), dtype=np.float32)
    bias = np.zeros((len(LABELS),), dtype=np.float32)
    for source_index, class_name in enumerate(classifier.classes_):
        target_index = LABELS.index(class_name)
        weights[:, target_index] = classifier.coef_[source_index]
        bias[target_index] = classifier.intercept_[source_index]

    inputs = tf.keras.Input((FEATURES,), name="director_hashed_features")
    outputs = tf.keras.layers.Dense(len(LABELS), name="director_logits")(inputs)
    model = tf.keras.Model(inputs, outputs)
    model.get_layer("director_logits").set_weights([weights, bias])
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(converter.convert())


def verify_export(output: pathlib.Path, test_rows: list[dict]) -> dict:
    import tensorflow as tf

    interpreter = tf.lite.Interpreter(model_path=str(output))
    interpreter.allocate_tensors()
    input_info = interpreter.get_input_details()[0]
    output_info = interpreter.get_output_details()[0]
    if list(input_info["shape"]) != [1, FEATURES] or int(output_info["shape"][-1]) != len(LABELS):
        raise SystemExit("candidate LiteRT tensor contract mismatch")

    x = np.stack([vectorize(row["text"]) for row in test_rows])
    expected = np.array([row["intent"] for row in test_rows])
    predicted = []
    confidence = []
    margins = []
    for sample in x:
        interpreter.set_tensor(input_info["index"], sample.reshape(1, FEATURES).astype(input_info["dtype"]))
        interpreter.invoke()
        logits = interpreter.get_tensor(output_info["index"])[0].astype(np.float64)
        shifted = logits - logits.max()
        probs = np.exp(shifted) / np.exp(shifted).sum()
        order = np.sort(probs)
        predicted.append(LABELS[int(np.argmax(probs))])
        confidence.append(float(order[-1]))
        margins.append(float(order[-1] - order[-2]))

    predicted = np.array(predicted)
    high = (np.array(confidence) >= 0.40) & (np.array(margins) >= 0.15)
    return {
        "accuracy": float(np.mean(predicted == expected)),
        "highConfidenceCoverage": float(np.mean(high)),
        "acceptedAccuracy": float(np.mean(predicted[high] == expected[high])) if np.any(high) else 0.0,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--haku-dataset", required=True)
    parser.add_argument("--seed-dataset", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--report", required=True)
    args = parser.parse_args()

    haku = read_rows(pathlib.Path(args.haku_dataset))
    seed = read_rows(pathlib.Path(args.seed_dataset))
    haku_train = [row for row in haku if row["split"] == "train"]
    haku_test = [row for row in haku if row["split"] == "test"]
    seed_train = [row for row in seed if row["split"] == "train"]
    seed_test = [row for row in seed if row["split"] == "test"]
    if not haku_train or not haku_test or not seed_train or not seed_test:
        raise SystemExit("candidate training requires Haku and seed train/test rows")

    baseline = fit(seed_train)
    baseline_haku = metrics(baseline, haku_test)
    baseline_seed = metrics(baseline, seed_test)

    haku_only = fit(haku_train)
    haku_only_haku = metrics(haku_only, haku_test)
    haku_only_seed = metrics(haku_only, seed_test)

    # Continued-training blend: Haku teacher rows dominate 4:1 while the old deterministic
    # seed contract remains an anchor for backwards behavior coverage.
    blend_rows = haku_train + seed_train
    blend_weights = [4.0] * len(haku_train) + [1.0] * len(seed_train)
    blend = fit(blend_rows, blend_weights)
    blend_haku = metrics(blend, haku_test)
    blend_seed = metrics(blend, seed_test)

    def score(haku_metrics: dict, seed_metrics: dict) -> float:
        return haku_metrics["accuracy"] + 0.25 * seed_metrics["accuracy"]

    candidates = [
        ("haku_only", haku_only, haku_only_haku, haku_only_seed),
        ("haku_seed_blend", blend, blend_haku, blend_seed),
    ]
    selected_name, selected, selected_haku, selected_seed = max(
        candidates, key=lambda item: score(item[2], item[3])
    )

    output = pathlib.Path(args.output)
    export_tflite(selected, output)
    exported = verify_export(output, haku_test)

    per_label = selected_haku.get("perLabelRecall") or {}
    min_recall = min(per_label.values()) if per_label else 0.0
    promotion_eligible = (
        exported["accuracy"] >= 0.80
        and exported["acceptedAccuracy"] >= 0.90
        and selected_seed["accuracy"] >= 0.90
        and min_recall >= 0.55
        and output.stat().st_size <= 5 * 1024 * 1024
    )

    report = {
        "contract": "WORLD_DIRECTOR_PRESSURE_V1_HAKU_CANDIDATE",
        "selected": selected_name,
        "modelBytes": output.stat().st_size,
        "promotionEligible": promotion_eligible,
        "productionAssetModified": False,
        "hakuRows": len(haku),
        "seedRows": len(seed),
        "baselineSeedOnly": {"hakuTest": baseline_haku, "seedTest": baseline_seed},
        "hakuOnly": {"hakuTest": haku_only_haku, "seedTest": haku_only_seed},
        "hakuSeedBlend": {"hakuTest": blend_haku, "seedTest": blend_seed},
        "exportedCandidate": exported,
    }
    pathlib.Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
