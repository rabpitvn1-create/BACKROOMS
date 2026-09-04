#!/usr/bin/env python3
"""scikit-learn 1.8 compatible entrypoint for WorldDirector V2 candidate training.

The V2 implementation originally selected liblinear+multi_class=ovr. scikit-learn 1.8 removed the
multi_class argument and no longer permits liblinear for 4-class LogisticRegression. Keep the V2
training/evaluation/export logic unchanged while replacing only the estimator construction with the
current multinomial-capable lbfgs solver pinned by android-apk/ml/requirements.txt.
"""
from __future__ import annotations

import numpy as np
from sklearn.linear_model import LogisticRegression

import train_world_director_v2_candidate as impl


def compatible_fit(rows: list[dict], feature_count: int, weights: list[float] | None = None):
    x = impl.vectorize_many([row["text"] for row in rows], feature_count)
    y = np.asarray([row["intent"] for row in rows])
    classifier = LogisticRegression(
        max_iter=5000,
        C=8.0,
        class_weight="balanced",
        solver="lbfgs",
    )
    classifier.fit(x, y, sample_weight=np.asarray(weights, dtype=np.float64) if weights is not None else None)
    return classifier


def main() -> int:
    impl.fit = compatible_fit
    return impl.main()


if __name__ == "__main__":
    raise SystemExit(main())
