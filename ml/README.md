# DermAssist AI coursework

This directory is the reproducible learning and evaluation implementation. It
uses existing public SCIN photographs, never app users' photographs. This is a
small educational experiment, not clinical validation.

## Reproduce

Use Python **3.11**, about 4 GB working RAM, and several GB of free disk space.
A GPU is optional; the recorded run uses CPU. Run commands from the repository root.

```sh
python3.11 -m venv ml/.venv
source ml/.venv/bin/activate
pip install -r ml/requirements.txt
export AI_DATA_DIR=/path/with/free/space/dermassist-ai
export AI_PYTHON="$PWD/ml/.venv/bin/python"
./ml/run.sh
```

Preparation downloads metadata and selected images from the public SCIN bucket.
It caches resized JPEGs with raw-byte and pixel hashes. Re-running uses that cache.
Training writes an actual `screening.tflite` and its versioned `screening.json`
contract into the Android assets. Reports and trained Keras weights go into the
specified data directory. Do not edit the thresholds after inspecting test results.

`DermAssist_AI_Lab.ipynb` is a guided coursework companion. The scripts are the
executable source of truth; the notebook calls them rather than implementing a
second, subtly different pipeline.

## Data design

- The task is three-class supervised image classification: **Eczema, Urticaria,
  Folliculitis**. These classes were selected after an audit, not a promised scope.
- The top SCIN weighted dermatologist label must have weight at least 0.5, exceed
  the next label by at least 0.15, and have a sufficient-quality dermatologist
  annotation. These are dataset inclusion rules, not clinical certainty rules.
- Take one image per case. Drop all members of duplicate groups identified through
  shared image paths, exact normalized pixel hashes, or 64-bit difference hashes
  within Hamming distance 2. This conservative procedure can exclude unrelated
  low-detail images and cannot prove that all near-duplicates have been found.
- The released data supplies case IDs, not a stable contributor identifier. We
  enforce case separation and image de-duplication; we cannot claim independently
  verified person-level separation across distinct submissions.
- Deterministic, class-stratified partitions: 60% train, 15% tune, 10% calibration,
  and the remaining ~15% test. Integer rounding is recorded in `audit.json`.
- Other dermatologist-labeled conditions are sampled separately: 60% for rejection
  calibration and 40% for final OOD testing. They never train the supported-class
  classifier. This is within-dataset OOD evaluation, **not external validation**.
- Skin-tone annotations are used only for evaluation, not as predictor inputs.
  Missing tones remain unknown. Small subgroup counts are explicitly flagged.

## Experiments and AI fundamentals

| Concept | Actual application and evidence |
| --- | --- |
| Problem formulation | Supervised classification with a finite label space and a separate abstention decision |
| Data quality and sampling | Label ambiguity filters, missingness audit, duplicate removal, frozen split manifest |
| Features and representations | RGB histograms, 8×8 grayscale layout, mean/variance/edge statistics versus CNN embeddings |
| Scaling and leakage | StandardScaler fits only training examples inside each sklearn pipeline |
| Linear classification | Class-weighted logistic regression; C chosen using tune macro-F1 |
| Instance-based learning | Distance-weighted kNN; k chosen on the same tune partition |
| Baseline reasoning | Majority classifier, logistic regression, kNN, then neural comparison on identical held-out cases |
| Neural networks | Convolutional MobileNetV2 features, a ReLU hidden layer, and class logits |
| Transfer learning | ImageNet-pretrained backbone first frozen, then final convolution blocks fine-tuned |
| Optimization | Adam, mini-batches, backpropagation, sparse cross-entropy, staged learning rates |
| Regularization | Dropout, L2 penalty, mild training-only augmentation, early stopping, frozen batch normalization |
| Class imbalance | Inverse-frequency training weights; macro-F1 and per-class recall rather than accuracy alone |
| Model selection | Baseline hyperparameters and frozen/fine-tuned choice use tune data only |
| Bias and variance | Training versus tune loss curves; compare simple and expressive classifiers |
| Probability calibration | Temperature chosen by calibration-set negative log-likelihood; report ECE, NLL, and Brier score |
| Abstention and unfamiliar inputs | Joint calibrated-score and cosine-distance rules; evaluate errors and coverage separately |
| Statistical uncertainty | Wilson accuracy intervals and a deterministic 500-resample macro-F1 bootstrap |
| Evaluation | Confusion matrix; per-class precision/recall/F1; final held-out supported and unsupported cases |
| Robustness | Held-out blur, darkening, and brightening experiments, without tuning on their outcomes |
| Fairness | Held-out Monk tone groups with sample counts; insufficient-evidence flags below 20 cases |
| Model compression | Float16 weight conversion; compare exported versus training-runtime predictions |
| Systems and deployment | Offline CPU inference, measured latency, hash-checked model contract, Android golden-vector parity |
| Rule-based safeguards | Separate technical exposure/detail checks; these rules do not diagnose diseases |

Search algorithms, game-playing, reinforcement learning, and symbolic medical
reasoning are not claimed as implemented. They should only be added if the course
requires a defensible application; padding this classifier with unrelated
algorithms would not demonstrate meaningful learning. Map this table to the
actual syllabus before calling course coverage complete.

## Key equations

Logistic/softmax classification: `p(k|x) = exp(z_k) / sum_j exp(z_j)`.
Training loss: `-w_y log p(y|x) + lambda ||W||²`; dropout operates during training.
Temperature scaling: `p_T(k|x) = softmax(z/T)`, where only calibration data fits `T`.
Visual distance: `d = 1 - max_c cosine(embedding, training_centroid_c)`.
Accept only when the calibrated top score and distance both pass the frozen policy.

The policy maximizes calibration coverage subject to >=75% **observed** accepted
accuracy, >=8 accepted calibration cases, and <=10% observed OOD acceptance.
These small-sample classroom constraints are not clinical targets or guarantees.
If no setting meets them, the exported policy rejects everything. Low confidence
or large embedding distance alone cannot guarantee unfamiliar-condition detection.

## What symptoms do

The five questionnaire answers are recorded and shared as observations. They do
not change this image-only model. No fabricated rule-based disease scores are
mixed in. Multimodal learning remains a separately testable future experiment.

## Attribution and limits

Data: Google Research SCIN and collaborators, [dataset](https://github.com/google-research-datasets/scin),
[SCIN Data Use License](https://github.com/google-research-datasets/scin/blob/main/LICENSE).
The complete license is bundled in Android assets. Images are resized, EXIF is
removed, labels are filtered, and weights are learned. No affiliation or endorsement
is implied. Do not attempt to identify dataset subjects.

Training follows the two-stage approach in the [TensorFlow transfer learning guide](https://www.tensorflow.org/tutorials/images/transfer_learning).
The app uses the offline CPU [LiteRT Interpreter API](https://developers.google.com/edge/litert/android).
ImageNet initialization comes from Keras MobileNetV2. Redistribution and medical-use
review remain separate release gates; this coursework run is not a public-health release.

The dataset is US-sourced and small, labels are uncertain differential assessments,
and dark skin tones are sparse. There is no independent external test dataset in
this run. No clinical referral protocol or condition-specific treatment guidance
has been validated. Uncertainty intervals do not repair these limitations.
