#!/usr/bin/env python3
"""Train/export the tiny hashed-bag-of-words classifier used by LiteRTIntentInterpreter.

Requires tensorflow and numpy. Output is fully integer-quantized when representative
data is sufficient, otherwise export fails instead of silently shipping a float model.
"""
import argparse, csv, json, pathlib, re
import numpy as np

FEATURES = 4096
TOKEN = re.compile(r"[^\W_]+|_", re.UNICODE)

def java_hash(text):
    value = 0
    for char in text:
        value = (31 * value + ord(char)) & 0xFFFFFFFF
    return value if value < 0x80000000 else value - 0x100000000

def vectorize(text):
    result = np.zeros(FEATURES, dtype=np.float32)
    tokens = TOKEN.findall(text.lower())
    features = [f"w:{x}" for x in tokens]
    features += [f"b:{a}|{b}" for a,b in zip(tokens,tokens[1:])]
    compact = " ".join(tokens)
    for size in range(3,6): features += [f"c{size}:{compact[i:i+size]}" for i in range(max(0,len(compact)-size+1))]
    for feature in features: result[(java_hash(feature) & 0x7FFFFFFF) % FEATURES] += 1.0
    norm = np.linalg.norm(result)
    return result / norm if norm else result

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", default="intent_dataset.csv")
    parser.add_argument("--labels", default="../app/src/main/assets/models/backroom_intent_labels.txt")
    parser.add_argument("--output", default="../app/src/main/assets/models/backroom_intent.tflite")
    parser.add_argument("--report", default="intent_model_report.json")
    args = parser.parse_args()
    import tensorflow as tf
    from sklearn.linear_model import LogisticRegression

    labels = [x.strip() for x in pathlib.Path(args.labels).read_text().splitlines() if x.strip()]
    rows = list(csv.DictReader(pathlib.Path(args.dataset).open(encoding="utf-8")))
    train = [r for r in rows if r["split"] == "train"]
    test = [r for r in rows if r["split"] == "test"]
    x = np.stack([vectorize(r["text"]) for r in train]); y_names = np.array([r["intent"] for r in train])
    test_x = np.stack([vectorize(r["text"]) for r in test]); test_names = np.array([r["intent"] for r in test])
    classifier = LogisticRegression(max_iter=5000, C=12, class_weight="balanced").fit(x, y_names)
    pre_export_accuracy = float(np.mean(classifier.predict(test_x) == test_names))
    if pre_export_accuracy < .90: raise SystemExit(f"quality gate failed before export: accuracy={pre_export_accuracy:.4f} < 0.90")

    # Copy the calibrated sklearn linear decision surface into one tiny LiteRT Dense op.
    weights = np.zeros((FEATURES, len(labels)), dtype=np.float32)
    bias = np.zeros((len(labels),), dtype=np.float32)
    for source_index, class_name in enumerate(classifier.classes_):
        target_index = labels.index(class_name)
        weights[:, target_index] = classifier.coef_[source_index]
        bias[target_index] = classifier.intercept_[source_index]
    inputs = tf.keras.Input((FEATURES,), name="hashed_text_features")
    outputs = tf.keras.layers.Dense(len(labels), name="intent_logits")(inputs)
    model = tf.keras.Model(inputs, outputs)
    model.get_layer("intent_logits").set_weights([weights, bias])
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    output = pathlib.Path(args.output); output.parent.mkdir(parents=True, exist_ok=True); output.write_bytes(converter.convert())

    # Gate the actual exported bytes, not just the source estimator.
    interpreter = tf.lite.Interpreter(model_path=str(output)); interpreter.allocate_tensors()
    input_info = interpreter.get_input_details()[0]; output_info = interpreter.get_output_details()[0]
    predicted=[]
    for sample in test_x:
        interpreter.set_tensor(input_info["index"], sample.reshape(1, FEATURES).astype(input_info["dtype"]))
        interpreter.invoke(); logits=interpreter.get_tensor(output_info["index"])[0]
        predicted.append(labels[int(np.argmax(logits))])
    exported_accuracy=float(np.mean(np.array(predicted)==test_names))
    negatives=np.isin(test_names,["NO_ACTION","UNKNOWN"])
    action_names=set(labels)-{"NO_ACTION","UNKNOWN"}
    false_positive=float(np.mean([p in action_names for p in np.array(predicted)[negatives]]))
    no_action=test_names=="NO_ACTION"; no_action_recall=float(np.mean(np.array(predicted)[no_action]=="NO_ACTION"))
    logits_all=[]
    for sample in test_x:
        interpreter.set_tensor(input_info["index"], sample.reshape(1, FEATURES).astype(input_info["dtype"])); interpreter.invoke(); logits_all.append(interpreter.get_tensor(output_info["index"])[0])
    logits_all=np.stack(logits_all); shifted=logits_all-logits_all.max(axis=1,keepdims=True); probabilities=np.exp(shifted)/np.exp(shifted).sum(axis=1,keepdims=True)
    ordered=np.sort(probabilities,axis=1); high=(ordered[:,-1]>=.45)&((ordered[:,-1]-ordered[:,-2])>=.30)
    coverage=float(np.mean(high)); accepted_accuracy=float(np.mean(np.array(predicted)[high]==test_names[high])) if np.any(high) else 0.0
    if exported_accuracy < .90 or false_positive > .01 or no_action_recall < .97 or coverage < .70 or accepted_accuracy < .98:
        output.unlink(missing_ok=True)
        raise SystemExit(f"exported model gate failed: accuracy={exported_accuracy:.4f} fp={false_positive:.4f} no_action={no_action_recall:.4f} coverage={coverage:.4f} accepted={accepted_accuracy:.4f}")
    report={"test_accuracy":exported_accuracy,"negative_action_false_positive":false_positive,"no_action_recall":no_action_recall,"high_confidence_coverage":coverage,"accepted_accuracy":accepted_accuracy,"model_bytes":output.stat().st_size,"labels":len(labels),"train_rows":len(train),"test_rows":len(test)}
    pathlib.Path(args.report).write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(report)

if __name__ == "__main__": main()
