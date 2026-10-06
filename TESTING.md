# Android and AI verification

Recorded on 6 October 2026. This verifies implementation behavior, not clinical
accuracy. Condition suggestions remain disabled because held-out performance is weak.

## Completed

| Check | Result |
| --- | --- |
| `assembleDebug`, `assembleDebugAndroidTest` | Passed |
| `testDebugUnitTest` | 8 passed |
| `lintDebug` | No errors; 14 warnings (12 dependency updates, 2 API/style suggestions) |
| Python `pytest -q ml/tests` | 5 passed |
| API 35 x86_64 emulator, normal display | 7 instrumentation tests passed |
| Same emulator, 320dp width and 200% font scale | 7 instrumentation tests passed |
| Home, consent, and result screenshots | Captured; large-text header/navigation fix visually checked |

The emulator had airplane mode enabled and Wi-Fi/mobile data disabled. The app
has no internet permission. The tests cover:

- Exact Python-to-Android model input/output parity using a public SCIN fixture.
- Flat-photo rejection before inference.
- Consent, prepared photo import, activity recreation, all questionnaire fields,
  real inference reaching the reliability gate, journal persistence/deletion,
  and app-owned photo cleanup.
- Scrollable consent and single-line navigation at large text size.
- EXIF rotation correction and removal of GPS metadata.
- Camera and system photo-picker cancellation contracts, using stubbed responses.
- Text-only share chooser construction, without actually sending anything.

The workflow fixture is synthetic and enters through the production photo
preparation path. Cancellation tests intercept external intents: they do not test
real camera hardware or the internals of the system picker. Activity recreation
is covered; process death is not equivalent and remains outstanding.

See [device evidence](artifacts/device-tests/README.md),
[AI methods](ml/README.md), and [model evaluation](ml/reports/MODEL_CARD.md).

## Reproduce

With JDK 17 and Android SDK 35:

```sh
./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
adb -s YOUR_TEST_DEVICE install -r app/build/outputs/apk/debug/app-debug.apk
adb -s YOUR_TEST_DEVICE install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s YOUR_TEST_DEVICE shell am instrument -w -r com.dermassist.app.test/androidx.test.runner.AndroidJUnitRunner
```

Use a dedicated test installation: instrumentation clears its local journal.
Large-text tests used `wm size 640x1280`, `wm density 320`, and
`settings put system font_scale 2.0`. Restore display settings afterward.
Python instructions and pinned packages are in `ml/README.md`.

## Still required before release

- Physical Android phone: actual camera capture/import, revoked URIs, repeated
  large photos, memory pressure, heat, battery use, and end-to-end latency.
- API 26 compatibility, navigation modes, landscape, and process death/recovery.
- TalkBack reading order and interaction; broader accessibility review.
- Mirrored EXIF cases, missing camera handlers, actual system-picker selections,
  and external share-app behavior.
- Independent clinical evaluation, reviewed guidance, representative skin tones,
  and intended-population validation before enabling condition suggestions.

There is no connected physical-phone test result. The technical photo guard is
heuristic, not a validated skin/photo-quality classifier. No diagnostic or treatment
claim follows from successful software tests.
