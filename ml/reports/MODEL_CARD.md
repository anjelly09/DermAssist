# DermAssist research model — 2026-10-06

**Status: research experiment, not screening-ready. Condition suggestions are disabled in the app.**
The bundled model still executes locally; the reliability gate returns an
unable-to-assess result instead of exposing an unreliable condition suggestion.

## Training and provenance

- SCIN public dataset, released case IDs and weighted dermatologist differential labels.
- Classes: Eczema, Urticaria, Folliculitis. 565 supported cases after exclusions.
- 338 train / 84 tune / 57 calibration / 86 final test cases.
- Other conditions: 142 calibration / 95 final test cases, not classifier training data.
- One photo per case; 10 duplicate/near-duplicate cases excluded; one undersized image excluded.
- ImageNet MobileNetV2 alpha 0.35; 160×160 RGB inputs normalized to [-1,1].
- Frozen-feature ReLU/dropout head beat fine-tuning on tune loss; frozen version retained.
- Class weighting, mild augmentation, early stopping, L2 regularization, Adam.
- Temperature scaling fitted on calibration cases, not on final test cases.
- Complete metadata, split hashes, counts, and parameters: `data_audit.json`,
  `split_manifest.json`, `selection.json`, and `training_history.json`.

## Final held-out results

| Model | Accuracy | Macro-F1 |
| --- | ---: | ---: |
| Majority baseline | 55.8% | 0.239 |
| Handcrafted features + logistic regression | 37.2% | 0.303 |
| Handcrafted features + kNN | 54.7% | 0.435 |
| MobileNet transfer classifier | **43.0%** | **0.376** |

The neural model did **not** beat the majority baseline on accuracy or kNN on
macro-F1. Its accuracy Wilson 95% interval is approximately 33.1–53.6%.
A model is not successful merely because training accuracy is high or its neural
architecture is more sophisticated. These results demonstrate limited generalization.

The frozen confidence/distance policy accepted 10 of 86 supported test cases
(11.6% coverage); only 6 of those 10 were correct (60%, Wilson interval 31.3–83.2%).
It also accepted 10 of 95 unfamiliar-condition cases (10.5% false acceptance).
Confidence alone separated supported from unfamiliar cases poorly (AUROC 0.479).
Calibration does not turn a weak classifier into a reliable diagnostic system.

## Deployment decision

The calibration policy and learned weights are preserved unchanged for research
analysis. After observing poor test generalization, a separate deployment gate
was added to disable user-facing condition suggestions. This is a release decision,
not a threshold adjustment designed to improve reported test metrics. Neither the
model nor the test results are represented as clinically validated.

The app includes no treatment advice, diagnostic conclusion, healthy-skin claim,
or patient-specific probability. It preserves the photo/symptom journal workflow.

## Representation and fairness limits

The final test cases include 72 with Monk tones 1–3, 14 with tones 4–6, and **zero**
with tones 7–10. This cannot establish performance across skin tones. The source
population is US internet contributors, not the intended rural target population.
Case IDs are split-disjoint; independent person-level separation cannot be proven
because a stable contributor identifier is not available. Differential labels are
not equivalent to confirmed clinical diagnoses. Near-duplicate removal is imperfect.

## Export and systems

The exported float16 model is about 977 KiB. Top-class agreement with the TensorFlow
model was 100% on these 86 test cases; maximum absolute logit difference was 0.0943.
Desktop CPU interpreter median was 0.63 ms and p95 0.81 ms, excluding image decode,
model loading, and preprocessing. **These are not phone latency measurements.**
See device reports for independently measured Android runtime parity and timing.

Blur and exposure experiments in `metrics.json` report model-only rejection before
the separate heuristic photo-quality guard. The guard checks extreme exposure and
low detail; it is not a learned skin detector or validated image-quality assessment.

## Coursework conclusions and next experiment

1. Keep the failed experiment and all baselines; do not hide unfavorable metrics.
2. Inspect training/tune curves, class imbalance, label ambiguity, and error types.
3. Improve the data/representation or compare a justified multimodal model using
   train/tune partitions. Choose hypotheses from domain knowledge and training
   analysis, rather than adjusting parameters to this test set.
4. Treat this test set as consumed. A subsequent performance claim needs a new,
   untouched evaluation set or properly nested evaluation protocol.
5. Obtain independent external data and clinical review before enabling screening.

Data source and attribution: [SCIN](https://github.com/google-research-datasets/scin),
Google Research and collaborators. [SCIN Data Use License](https://github.com/google-research-datasets/scin/blob/main/LICENSE).
No endorsement is implied. A complete license is bundled with the app.
