# DermAssist for Android

Native Kotlin / Jetpack Compose foundation based on `PLAN.md` and `DESIGN.md`.
This is a student research prototype, **not an operational screening tool**. A
trained MobileNet model runs offline on Android. Its held-out accuracy was only
**43.0% (37/86)**, so a deployment gate disables condition suggestions and the
app returns **unable to assess**. The failed experiment and baselines are preserved.

Start with [AI coursework and fundamentals](ml/README.md), the guided
[AI lab notebook](ml/DermAssist_AI_Lab.ipynb), and the
[model card and evaluation](ml/reports/MODEL_CARD.md).

## Open and run

1. Install Android Studio. In SDK Manager install **Android SDK Platform 35**,
   Build Tools **35.0.0**, and Platform Tools.
2. Open this repository as a project and let Gradle sync. Set the Gradle JDK to
   **17** (Settings → Build Tools → Gradle); the build targets JVM 17.
3. Select the `app` configuration, then run on an emulator or USB-connected phone
   running **Android 8.0 / API 26** or newer. Enable USB debugging on the phone.
4. For a command-line build, set `JAVA_HOME` to JDK 17 and `ANDROID_HOME` to your
   SDK directory, or add `sdk.dir=/your/sdk/path` to an untracked `local.properties`.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
Windows users can run the equivalent commands with `gradlew.bat`.

Pinned toolchain: AGP 8.9.2, Gradle 8.11.1, Kotlin/Compose compiler 2.1.20,
Compose BOM 2025.04.01. The AGP/Gradle pairing follows the
[Android compatibility table](https://developer.android.com/build/releases/agp-8-9-0-release-notes).
Compose uses the [Kotlin Compose compiler plugin](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler).

## What is implemented

- A native, scrollable home screen and bottom navigation.
- Explicit consent before photo processing.
- Android camera intent and system photo picker; no broad storage permissions.
- Bounded photo decoding, EXIF orientation correction, and re-encoding without
  location metadata. Photos under 320 pixels on either edge are rejected.
- Manual photo confirmation plus technical exposure/detail heuristics. These are
  not a learned skin detector or validated photo-quality assessment.
- Location, duration, itch, pain, and spread questionnaire with required answers.
- Real, hash-checked LiteRT CPU inference with calibrated scores and embedding
  distance rejection. A separate reliability gate disables condition suggestions.
- Explicit **unable to assess** results, model version, and measured inference time.
- Optional, private, local text journal with individual and bulk deletion.
- User-initiated Android share sheet containing notes and prototype limitations,
  without the photo.
- Saved draft state across activity recreation; temporary photos removed when a
  check is closed and orphaned cache photos removed on the next fresh launch.
- No internet permission, backend, analytics, or account. App photos never feed
  training; the separate Python coursework pipeline uses public SCIN data.

## Design adaptation

The supplied reference informs the parchment `#fefffc` canvas, paper cards,
green-gray `#dee2de` borders, serif headlines, and blue `#41a1cf` action outlines.
Blue borders use dark labels for legibility. Android system serif/sans fonts are
used until licensed brand fonts are supplied. Lightweight botanical line art
provides an offline illustration without image downloads or animation costs.

Layouts cap reading width at 600dp, scroll on compact screens, respect system
insets, support font scaling, and provide at least 48dp interactive targets.
The app is intentionally light-themed. Current copy is English; localization is
still pending. Android Studio includes a `WelcomePreview` composable.

## Source map

- `DermApp.kt`: screens, system activity-result integration, reusable UI.
- `Theme.kt`: Compose colors and typography from the design reference.
- `DermViewModel.kt`: draft lifecycle, navigation, and journal operations.
- `PhotoStore.kt`: bounded image preparation and temporary-file ownership.
- `JournalStore.kt`: private, text-only JSON persistence.
- `Assessment.kt`: questionnaire, report format, and photo resolution rule.
- `ScreeningEngine.kt` / `Screening.kt`: inference, quality checks, and uncertainty.
- `ml/`: data audit, classical baselines, neural training, calibration, evaluation,
  reproducible manifests, model card, learning curves, and coursework notebook.

## Privacy details

Only explicitly saved notes are persisted in app-private SharedPreferences.
This is Android sandbox storage, **not application-level encryption**. Cloud
backup and device transfer are excluded. Screenshots/recents previews are blocked
by `FLAG_SECURE`. A camera provider may retain its own original photo; DermAssist
only manages its own cached copies. A shared text report is controlled by the
receiving app after the user sends it. Uninstalling deletes local app data.

## Evaluation and next steps

The 86-case test set gives neural accuracy 43.0% and macro-F1 0.376. kNN achieved
54.7% and 0.435; majority accuracy was 55.8%. Even after calibrated rejection,
only 6 of 10 accepted cases were correct. No test examples had Monk tones 7–10.
These results do not justify exposing condition suggestions. Symptoms are recorded
observations; they do not influence this image-only model.

Improve data and hypotheses on train/tune partitions, then use a new untouched
final evaluation set. Independent population validation and clinical review are
needed before enabling screening. Physical-phone benchmarking, TalkBack, and
API 26 testing remain outstanding. See [Android verification](TESTING.md).

SCIN data and derived weights are used for this research experiment; see the
[complete bundled data license](app/src/main/assets/licenses/SCIN-LICENSE.txt)
and the model card for attribution and limitations.
