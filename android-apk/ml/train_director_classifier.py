#!/usr/bin/env python3
"""Train/export the tiny hashed-feature classifier used by LiteRTWorldDirectorPolicy.

Contract: this trainer is ONLY for WorldDirector pressure proposals:
NONE / MAZE_PRESSURE / ENTITY_PRESSURE / ITEM_OPPORTUNITY.
BackroomsDirector evidence-selection telemetry uses a different semantic contract and must never be
adapted into these labels by heuristic mapping.
"""
import argparse
import csv
import json
import pathlib
import re

import numpy as np

FEATURES = 4096
EXPECTED_LABELS = ["NONE", "MAZE_PRESSURE", "ENTITY_PRESSURE", "ITEM_OPPORTUNITY"]
# Keep underscore-bearing structured feature tokens intact, matching the Kotlin tokenizer.
TOKEN = re.compile(r"[\w]+", re.UNICODE)


def java_hash(text):
    value = 0
    for char in text:
        value = (31 * value + ord(char)) & 0xFFFFFFFF
    return value if value < 0x80000000 else value - 0x100000000


def vectorize(text):
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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", default="director_dataset.csv")
    parser.add_argument("--labels", default="../app/src/main/assets/models/backrooms_director_labels.txt")
    parser.add_argument("--output", default="../app/src/main/assets/models/backrooms_director.tflite")
    parser.add_argument("--report", default="director_model_report.json")
    args = parser.parse_args()

    import tensorflow as tf
    from sklearn.linear_model import LogisticRegression

    labels = [line.strip() for line in pathlib.Path(args.labels).read_text().splitlines() if line.strip()]
    if labels != EXPECTED_LABELS:
        raise SystemExit(
            "WorldDirector label contract mismatch: "
            f"expected={EXPECTED_LABELS!r} actual={labels!r}. "
            "Do not feed BackroomsDirector evidence labels/telemetry into this trainer."
        )

    rows = list(csv.DictReader(pathlib.Path(args.dataset).open(encoding="utf-8")))
    required_columns = {"text", "intent", "split"}
    if not rows or not required_columns.issubset(rows[0]):
        raise SystemExit("WorldDirector dataset must contain text,intent,split columns")
    dataset_labels = {row["intent"] for row in rows}
    if dataset_labels != set(EXPECTED_LABELS):
        raise SystemExit(
            "WorldDirector dataset label contract mismatch: "
            f"expected={sorted(EXPECTED_LABELS)!r} actual={sorted(dataset_labels)!r}"
        )

    train = [row for row in rows if row["split"] == "train"]
    test = [row for row in rows if row["split"] == "test"]
    if not train or not test:
        raise SystemExit("director dataset must contain train and test rows")

    train_x = np.stack([vectorize(row["text"]) for row in train])
    train_y = np.array([row["intent"] for row in train])
    test_x = np.stack([vectorize(row["text"]) for row in test])
    test_y = np.array([row["intent"] for row in test])

    classifier = LogisticRegression(max_iter=5000, C=10, class_weight="balanced").fit(train_x, train_y)
    pre_export_accuracy = float(np.mean(classifier.predict(test_x) == test_y))
    if pre_export_accuracy < .95:
        raise SystemExit(f"director quality gate failed before export: accuracy={pre_export_accuracy:.4f} < 0.95")

    weights = np.zeros((FEATURES, len(labels)), dtype=np.float32)
    bias = np.zeros((len(labels),), dtype=np.float32)
    for source_index, class_name in enumerate(classifier.classes_):
        target_index = labels.index(class_name)
        weights[:, target_index] = classifier.coef_[source_index]
        bias[target_index] = classifier.intercept_[source_index]

    inputs = tf.keras.Input((FEATURES,), name="director_hashed_features")
    outputs = tf.keras.layers.Dense(len(labels), name="director_logits")(inputs)
    model = tf.keras.Model(inputs, outputs)
    model.get_layer("director_logits").set_weights([weights, bias])
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    output = pathlib.Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(converter.convert())

    interpreter = tf.lite.Interpreter(model_path=str(output))
    interpreter.allocate_tensors()
    input_info = interpreter.get_input_details()[0]
    output_info = interpreter.get_output_details()[0]
    logits_all = []
    predicted = []
    for sample in test_x:
        interpreter.set_tensor(input_info["index"], sample.reshape(1, FEATURES).astype(input_info["dtype"]))
        interpreter.invoke()
        logits = interpreter.get_tensor(output_info["index"])[0]
        logits_all.append(logits)
        predicted.append(labels[int(np.argmax(logits))])

    predicted = np.array(predicted)
    exported_accuracy = float(np.mean(predicted == test_y))
    logits_all = np.stack(logits_all)
    shifted = logits_all - logits_all.max(axis=1, keepdims=True)
    probabilities = np.exp(shifted) / np.exp(shifted).sum(axis=1, keepdims=True)
    ordered = np.sort(probabilities, axis=1)
    high = (ordered[:, -1] >= .40) & ((ordered[:, -1] - ordered[:, -2]) >= .15)
    coverage = float(np.mean(high))
    accepted_accuracy = float(np.mean(predicted[high] == test_y[high])) if np.any(high) else 0.0

    per_label = {
        label: float(np.mean(predicted[test_y == label] == label))
        for label in labels
        if np.any(test_y == label)
    }
    if exported_accuracy < .95 or coverage < .75 or accepted_accuracy < .98 or min(per_label.values()) < .90:
        output.unlink(missing_ok=True)
        raise SystemExit(
            "director exported model gate failed: "
            f"accuracy={exported_accuracy:.4f} coverage={coverage:.4f} "
            f"accepted={accepted_accuracy:.4f} per_label={per_label}"
        )

    report = {
        "contract": "WORLD_DIRECTOR_PRESSURE_V1",
        "test_accuracy": exported_accuracy,
        "high_confidence_coverage": coverage,
        "accepted_accuracy": accepted_accuracy,
        "per_label_recall": per_label,
        "model_bytes": output.stat().st_size,
        "labels": labels,
        "train_rows": len(train),
        "test_rows": len(test),
    }
    pathlib.Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(report)


if __name__ == "__main__":
    main()
