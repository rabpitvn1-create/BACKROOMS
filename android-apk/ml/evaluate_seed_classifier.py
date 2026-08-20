#!/usr/bin/env python3
import csv, pathlib, sys
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import confusion_matrix
from train_intent_classifier import vectorize

def main():
    rows=list(csv.DictReader(pathlib.Path(__file__).with_name("intent_dataset.csv").open(encoding="utf-8")))
    train=[r for r in rows if r["split"]=="train"]; test=[r for r in rows if r["split"]=="test"]
    model=LogisticRegression(max_iter=5000,C=12,class_weight="balanced").fit(np.stack([vectorize(r["text"]) for r in train]),[r["intent"] for r in train])
    test_x=np.stack([vectorize(r["text"]) for r in test]); truth=np.array([r["intent"] for r in test]); prediction=model.predict(test_x)
    probabilities=model.predict_proba(test_x); ordered=np.sort(probabilities,axis=1); high=(ordered[:,-1]>=.45)&((ordered[:,-1]-ordered[:,-2])>=.30)
    accuracy=float(np.mean(prediction==truth))
    action_classes={x for x in truth if x not in {"NO_ACTION","UNKNOWN"}}
    negative=[i for i,x in enumerate(truth) if x in {"NO_ACTION","UNKNOWN"}]
    false_positive=sum(prediction[i] in action_classes for i in negative)/max(1,len(negative))
    no_action=[i for i,x in enumerate(truth) if x=="NO_ACTION"]
    no_action_recall=sum(prediction[i]=="NO_ACTION" for i in no_action)/max(1,len(no_action))
    coverage=float(np.mean(high)); accepted_accuracy=float(np.mean(prediction[high]==truth[high])) if np.any(high) else 0.0
    print({"train":len(train),"test":len(test),"accuracy":accuracy,"negative_action_false_positive":false_positive,"no_action_recall":no_action_recall,"high_confidence_coverage":coverage,"accepted_accuracy":accepted_accuracy})
    for row,pred in zip(test,prediction):
        if row["intent"]!=pred: print("MISS",row["intent"],"->",pred,":",row["text"])
    passed=accuracy>=.90 and false_positive<=.01 and no_action_recall>=.97 and coverage>=.70 and accepted_accuracy>=.98
    print("QUALITY_GATE", "PASS" if passed else "FAIL")
    return 0 if passed else 1

if __name__=="__main__": sys.exit(main())
