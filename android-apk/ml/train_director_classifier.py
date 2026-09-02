#!/usr/bin/env python3
"""Train and evaluate 3 experiment tracks for LiteRT BackroomsDirector:
1. synthetic bootstrap only
2. real telemetry only
3. synthetic + real telemetry mixed

Candidate model is saved ONLY to backrooms_director_candidate.tflite if real telemetry passes all quality gates.
The production model (backrooms_director.tflite) is NEVER touched or overwritten.
"""
import argparse
import csv
import json
import pathlib
import re
from typing import Dict, List, Any, Optional

import numpy as np

FEATURES = 4096
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


def evaluate_dataset_track(
    track_name: str,
    rows: List[Dict[str, str]],
    labels: List[str],
    candidate_model_path: Optional[pathlib.Path] = None,
    real_held_out_rows: Optional[List[Dict[str, str]]] = None,
) -> Dict[str, Any]:
    import tensorflow as tf
    from sklearn.linear_model import LogisticRegression

    train_rows = [r for r in rows if r["split"] in ("train", "val")]
    test_rows = [r for r in rows if r["split"] == "test"]
    unique_sessions = len({r["sessionId"] for r in rows if "sessionId" in r and r["sessionId"]})

    class_distribution = {}
    for label in labels:
        class_distribution[label] = sum(1 for r in rows if r["intent"] == label)

    if len(train_rows) < 4 or len(test_rows) < 1:
        return {
            "track_name": track_name,
            "status": "DATA_BLOCKED_INSUFFICIENT_ROWS",
            "total_rows": len(rows),
            "train_rows": len(train_rows),
            "test_rows": len(test_rows),
            "unique_sessions": unique_sessions,
            "class_distribution": class_distribution,
        }

    train_x = np.stack([vectorize(r["text"]) for r in train_rows])
    train_y = np.array([r["intent"] for r in train_rows])
    test_x = np.stack([vectorize(r["text"]) for r in test_rows])
    test_y = np.array([r["intent"] for r in test_rows])

    # Ensure all target classes present in training set before fitting
    if len(set(train_y)) < 2:
        return {
            "track_name": track_name,
            "status": "DATA_BLOCKED_SINGLE_CLASS",
            "total_rows": len(rows),
            "train_rows": len(train_rows),
            "test_rows": len(test_rows),
            "unique_sessions": unique_sessions,
            "class_distribution": class_distribution,
        }

    classifier = LogisticRegression(max_iter=5000, C=10, class_weight="balanced").fit(train_x, train_y)
    pre_export_acc = float(np.mean(classifier.predict(test_x) == test_y))

    # Convert to TFLite model in-memory or save candidate
    weights = np.zeros((FEATURES, len(labels)), dtype=np.float32)
    bias = np.zeros((len(labels),), dtype=np.float32)
    for source_index, class_name in enumerate(classifier.classes_):
        if class_name in labels:
            target_index = labels.index(class_name)
            weights[:, target_index] = classifier.coef_[source_index]
            bias[target_index] = classifier.intercept_[source_index]

    inputs = tf.keras.Input((FEATURES,), name="director_hashed_features")
    outputs = tf.keras.layers.Dense(len(labels), name="director_logits")(inputs)
    model = tf.keras.Model(inputs, outputs)
    model.get_layer("director_logits").set_weights([weights, bias])
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_bytes = converter.convert()

    # Interpreter evaluation
    interpreter = tf.lite.Interpreter(model_content=tflite_bytes)
    interpreter.allocate_tensors()
    input_info = interpreter.get_input_details()[0]
    output_info = interpreter.get_output_details()[0]

    def run_eval(eval_x, eval_y):
        logits_all = []
        predicted = []
        for sample in eval_x:
            interpreter.set_tensor(input_info["index"], sample.reshape(1, FEATURES).astype(input_info["dtype"]))
            interpreter.invoke()
            logits = interpreter.get_tensor(output_info["index"])[0]
            logits_all.append(logits)
            predicted.append(labels[int(np.argmax(logits))])
        predicted = np.array(predicted)
        acc = float(np.mean(predicted == eval_y))
        logits_all = np.stack(logits_all)
        shifted = logits_all - logits_all.max(axis=1, keepdims=True)
        probs = np.exp(shifted) / np.exp(shifted).sum(axis=1, keepdims=True)
        ordered = np.sort(probs, axis=1)
        high = (ordered[:, -1] >= 0.40) & ((ordered[:, -1] - ordered[:, -2]) >= 0.15)
        cov = float(np.mean(high))
        acc_acc = float(np.mean(predicted[high] == eval_y[high])) if np.any(high) else 0.0
        per_label = {
            label: float(np.mean(predicted[eval_y == label] == label))
            for label in labels
            if np.any(eval_y == label)
        }
        return acc, cov, acc_acc, per_label

    acc, cov, acc_acc, per_label = run_eval(test_x, test_y)

    real_held_out_metrics = None
    if real_held_out_rows:
        real_test_x = np.stack([vectorize(r["text"]) for r in real_held_out_rows])
        real_test_y = np.array([r["intent"] for r in real_held_out_rows])
        r_acc, r_cov, r_acc_acc, r_per_label = run_eval(real_test_x, real_test_y)
        real_held_out_metrics = {
            "test_accuracy": r_acc,
            "high_confidence_coverage": r_cov,
            "accepted_accuracy": r_acc_acc,
            "per_label_recall": r_per_label,
            "test_rows": len(real_held_out_rows),
        }

    # Check quality gates
    gate_pass = bool(
        acc >= 0.95 and
        cov >= 0.75 and
        acc_acc >= 0.98 and
        (not per_label or min(per_label.values()) >= 0.90) and
        unique_sessions >= 5
    )

    if gate_pass and candidate_model_path:
        candidate_model_path.parent.mkdir(parents=True, exist_ok=True)
        candidate_model_path.write_bytes(tflite_bytes)

    return {
        "track_name": track_name,
        "status": "PASS" if gate_pass else "FAIL",
        "total_rows": len(rows),
        "train_rows": len(train_rows),
        "test_rows": len(test_rows),
        "unique_sessions": unique_sessions,
        "class_distribution": class_distribution,
        "pre_export_accuracy": pre_export_acc,
        "test_accuracy": acc,
        "high_confidence_coverage": cov,
        "accepted_accuracy": acc_acc,
        "per_label_recall": per_label,
        "model_bytes": len(tflite_bytes),
        "quality_gates_passed": gate_pass,
        "comparison_on_real_held_out": real_held_out_metrics,
    }


def main():
    parser = argparse.ArgumentParser(description="Multi-track LiteRT BackroomsDirector trainer and evaluator.")
    parser.add_argument("--synthetic", default="director_dataset.csv", help="Synthetic bootstrap dataset CSV")
    parser.add_argument("--telemetry", default="director_telemetry_dataset.csv", help="Real telemetry dataset CSV")
    parser.add_argument("--telemetry-stats", default="director_telemetry_stats.json", help="Real telemetry stats JSON")
    parser.add_argument("--labels", default="../app/src/main/assets/models/backrooms_director_labels.txt")
    parser.add_argument("--candidate-output", default="../app/src/main/assets/models/backrooms_director_candidate.tflite")
    parser.add_argument("--report", default="director_experiment_report.json")
    args = parser.parse_args()

    labels = [line.strip() for line in pathlib.Path(args.labels).read_text().splitlines() if line.strip()]

    # Load synthetic dataset
    synth_path = pathlib.Path(args.synthetic)
    synth_rows = list(csv.DictReader(synth_path.open(encoding="utf-8"))) if synth_path.exists() else []

    # Load real telemetry dataset
    telem_path = pathlib.Path(args.telemetry)
    telem_rows = list(csv.DictReader(telem_path.open(encoding="utf-8"))) if telem_path.exists() else []

    # Load telemetry stats
    stats_path = pathlib.Path(args.telemetry_stats)
    telem_stats = json.loads(stats_path.read_text(encoding="utf-8")) if stats_path.exists() else {"status": "NO_TELEMETRY_STATS"}

    real_held_out = [r for r in telem_rows if r.get("split") == "test"]

    # Track 1: Synthetic only
    track1_report = evaluate_dataset_track("synthetic_bootstrap_only", synth_rows, labels, real_held_out_rows=real_held_out)

    # Track 2: Real telemetry only
    candidate_path = pathlib.Path(args.candidate_output)
    track2_report = evaluate_dataset_track("real_telemetry_only", telem_rows, labels, candidate_model_path=candidate_path, real_held_out_rows=real_held_out)

    # Track 3: Synthetic + Real mixed
    mixed_rows = synth_rows + telem_rows
    track3_report = evaluate_dataset_track("synthetic_plus_real_mixed", mixed_rows, labels, candidate_model_path=candidate_path if not track2_report.get("quality_gates_passed") else None, real_held_out_rows=real_held_out)

    candidate_produced = candidate_path.exists()

    report = {
        "telemetry_data_status": telem_stats.get("status", "DATA_BLOCKED"),
        "telemetry_stats": telem_stats,
        "candidate_model_produced": candidate_produced,
        "candidate_model_path": str(candidate_path) if candidate_produced else None,
        "production_model_path_untouched": "../app/src/main/assets/models/backrooms_director.tflite",
        "tracks": {
            "synthetic_bootstrap_only": track1_report,
            "real_telemetry_only": track2_report,
            "synthetic_plus_real_mixed": track3_report,
        }
    }

    pathlib.Path(args.report).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
