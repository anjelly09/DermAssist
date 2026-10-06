# DermAssist — High-Level Project Plan

## 1. Project overview

DermAssist is an Android app that uses a photograph of a visible skin concern,
along with a short symptom questionnaire, to suggest possible skin conditions
and help users understand their next step. The project focuses on access for
people in rural areas and demonstrates concepts learned in an AI course.

The initial deliverable is a student screening prototype. It does not confirm a
diagnosis, predict future diseases, prescribe medication, or establish that a
user is healthy. Public clinical use requires additional validation and review.

## 2. Goals

- Make photographing and describing a skin concern simple.
- Demonstrate a trained computer vision model running in an Android app.
- Provide understandable, clinician-reviewed care and referral information.
- Support limited connectivity and inexpensive phones where feasible.
- Evaluate model limitations, including differences across skin tones.

## 3. MVP scope

### Included

- Guided camera capture and optional photo selection.
- Checks for poor image quality, with instructions to retake a photo.
- A short questionnaire covering location, duration, itching, pain, and spread.
- Screening for approximately 3–5 conditions, chosen after a dataset audit.
- Possible-condition results with an explicit uncertain/unable-to-assess path.
- Reviewed information explaining next steps and when to seek care.
- Optional local assessment history and a user-initiated shareable report.
- Clear consent, privacy information, and deletion controls.

### Deferred

- Screening for every skin disease or making cancer exclusion claims.
- Automated prescriptions, treatment plans, or a medical chatbot.
- Accounts, cloud photo storage, teleconsultations, and appointment booking.
- Automatic prediction of disease progression or future skin conditions.

## 4. Intended user journey

1. Read a brief explanation of the screening and consent to photo processing.
2. Take or select a photo using framing and lighting instructions.
3. Retake the photo if it is unsuitable for assessment.
4. Answer a small number of symptom questions.
5. Receive possible conditions or an unable-to-assess result.
6. Read reviewed next-step guidance and referral advice.
7. Optionally save or share the assessment with a health worker.

Referral rules should be reviewed by a clinician. Concerning symptoms must not
be overridden by a reassuring model prediction. Avoid presenting raw model
scores as a patient's probability of having a disease.

## 5. Dataset strategy

Use existing publicly available online datasets only. Creating a local dataset,
recruiting patients, collecting training images from app users, and commissioning
new clinical labels are outside the project scope.

Start by auditing [Google SCIN](https://github.com/google-research-datasets/scin),
which includes user-submitted images, symptom information, dermatologist labels,
and skin-tone annotations.

- Check the current data-use licence and permitted app/model distribution.
- Count usable examples and distinct cases for candidate conditions.
- Examine ambiguous labels, image quality, duplicates, and class imbalance.
- Select classes based on data quality and clinical relevance. Acne, eczema,
  psoriasis, and fungal infections are candidates to investigate, not commitments.
- Keep all photos from the same contributor/case in the same dataset split.
- Maintain separate training, validation, and untouched test sets.
- Record data sources, exclusions, label mappings, and preprocessing decisions.
- Include examples outside the chosen classes to evaluate rejection behaviour.

SCIN's US-sourced images do not establish performance for the intended rural
population. Evaluate on held-out public data and, where suitable licensed data
is available, an independent public dataset. Document the population mismatch
as a limitation rather than claiming locally validated performance.

Photos captured during app use are inference inputs, not training data. Saving
an assessment is optional and does not add its image to a training dataset.

## 6. AI approach and coursework applications

| Concept | Planned application |
| --- | --- |
| Transfer learning | Fine-tune a lightweight pretrained image classifier |
| Baseline comparison | Compare against a simpler model or frozen-feature classifier |
| Preprocessing and augmentation | Standardize input and test realistic image variation |
| Classification | Predict a small set of supported screening categories |
| Multimodal learning | Compare images alone with images plus symptoms, if time permits |
| Calibration and uncertainty | Evaluate confidence and choose abstention thresholds |
| Fairness evaluation | Compare results across available skin-tone groups |
| Mobile deployment | Measure inference latency, model size, and resource use |

Begin with an image-only baseline. Keep referral logic and reviewed guidance
separate from model predictions. Add symptom fusion only if it improves measured
performance and sufficient labelled metadata is available.

Low confidence alone does not reliably detect unfamiliar conditions. Evaluate
the rejection mechanism explicitly on unsuitable and unsupported inputs.

## 7. Proposed technical architecture

- **Android app:** Kotlin with Jetpack Compose as the initial proposed stack.
- **Capture:** Camera integration with consistent image preparation.
- **Training:** Python with a selected machine learning framework.
- **Inference:** An Android-compatible on-device model, subject to export and
  device feasibility checks.
- **Guidance:** Versioned, clinician-reviewed content and referral rules.
- **Storage:** Minimal local data, with optional saving and user deletion.
- **Backend:** Not required for the initial MVP.

Select the final libraries and runtime during setup. Verify current official
documentation and compatibility before implementation.

Prefer on-device processing to support offline use and reduce photo uploads.
Assess performance on a representative low-cost Android phone early.

## 8. Evaluation and acceptance criteria

### AI evaluation

- Report per-class precision, recall, F1, and a confusion matrix.
- Report case counts and uncertainty intervals where feasible.
- Compare the baseline and final model on the same held-out cases.
- Use existing online datasets for all model development and evaluation; use
  independent public test data where compatible labels and licences permit.
- Evaluate calibration, rejected-case rate, and errors among accepted results.
- Test different lighting, blur, skin tones, and unsupported conditions.
- Analyse important false negatives and false positives with clinical input.
- Document dataset limitations and avoid claiming clinical accuracy from a
  classroom test set.

### App evaluation

- Complete capture → questionnaire → result → guidance on a physical phone.
- Demonstrate poor-photo rejection and unable-to-assess behaviour.
- Verify offline behaviour, save/delete controls, and report sharing.
- Check readability, accessibility, inference time, and memory use.
- Ensure error messages give an understandable recovery action.

Set numerical targets after measuring the baseline and discussing the intended
use with a clinical reviewer; do not invent an accuracy promise in advance.

## 9. Delivery phases

| Phase | Deliverable / completion gate |
| --- | --- |
| 1. Scope and data audit | Confirm existing online dataset access, licences, supported classes, and evaluation split |
| 2. Baseline model | Reproducible training and first held-out evaluation report |
| 3. Android foundation | Capture, questionnaire, and results flow using mock predictions |
| 4. Model integration | Real inference on a phone with measured resource use |
| 5. Guidance and usability | Reviewed content, uncertainty handling, and offline experience |
| 6. Assignment demo | Working APK, model report, limitations, and presentation materials |
| 7. Public-release readiness | Clinical validation, policy review, privacy review, and release testing |

Phases 2 and 3 can proceed in parallel once model inputs/outputs are agreed.

## 10. Assignment demonstration

Use consented or appropriately licensed example images. Demonstrate:

1. A supported example producing a screening result and next steps.
2. A poor-quality photo prompting a retake.
3. An unsupported/uncertain example that does not receive a forced diagnosis.
4. Offline inference on Android.
5. Baseline versus final model metrics and observed failure cases.

Deliver the source code, APK, reproducible training instructions, dataset summary,
evaluation report/model card, and a short explanation of the AI concepts used.

## 11. Play Store release path

Treat classroom completion and public clinical deployment as separate milestones.
Before publishing for patient use:

- Confirm dataset, model, and dependency distribution rights.
- Obtain clinical review of claims, guidance, referral behaviour, and validation.
- Review applicable medical-device requirements in the intended markets.
- Check the current [Google Play health-app policy](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en).
- Complete required health declarations, privacy policy, data-safety disclosures,
  and permission disclosures.
- Run appropriate release testing and provide a feedback/support route.
- Make store descriptions match the app's validated capabilities.

A disclaimer alone does not establish that a screening tool is safe or compliant.

## 12. Decisions to resolve first

- Assignment deadline, team roles, and AI syllabus requirements.
- Final supported conditions after inspecting the dataset.
- Availability of a clinician to review guidance and errors.
- Target languages, region, and representative Android devices.
- Whether the first release is a classroom prototype or a clinically validated
  public screening service.
