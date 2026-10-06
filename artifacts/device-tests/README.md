# Android emulator evidence — 2026-10-06

Device: Android API 35 Google APIs x86_64, `sdk_gphone64_x86_64`, AVD
`DermAssist_API35`, KVM acceleration and software graphics. Tests ran offline.

- [Normal display: 7 tests passed](api35-instrumentation.txt)
- [320dp / 200% font scale: 7 tests passed](api35-large-text-instrumentation.txt)
- [AI Python tests: 5 passed](python-tests.txt)
- [Interpreter benchmark and parity](api35-benchmark.json)

The final normal-display run measured a **1 ms median, 5 ms maximum** over ten
warm interpreter calls. Maximum absolute Android/Python output difference was
**0.00001347** across logits and embedding values for the golden fixture.
This excludes decoding, model loading, and preprocessing. Emulator timing is not
a physical-phone performance result and should not be generalized to low-cost phones.

## Screenshots

| Screen | Normal | 320dp / 200% text |
| --- | --- | --- |
| Home | [Image](api35-home.png) | [Image](api35-large-text-home.png) |
| Consent, scrolled to agreement | [Image](api35-consent.png) | [Image](api35-large-text-consent.png) |
| Research result | [Image](api35-result.png) | [Image](api35-large-text-result.png) |

Visual review caught a header wrapping defect despite the first interaction tests
passing. The layout now hides the optional device badge at narrow/large-text sizes,
keeps the brand to one line, and gives navigation labels more room. Regression
assertions check header height and the Journal label's line count. Brand truncation
in the consent header at 200% preserves the Back and Close controls.

The model runs in the complete flow test, but a separate reliability gate returns
unable to assess. Software success does not override the poor evaluation results.
Camera/picker cancellations use intercepted intents; the photo flow uses a
synthetic fixture through production preparation. No physical camera or phone was
tested. See [verification scope](../../TESTING.md) and
[model card](../../ml/reports/MODEL_CARD.md).
