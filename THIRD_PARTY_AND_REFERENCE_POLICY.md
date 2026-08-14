# Third-party and reference policy

## AOSP DeskClock

Clocky plans to adapt portions of Android Open Source Project DeskClock.

- Upstream project: `platform/packages/apps/DeskClock`
- Initial pinned revision: `04e481f37e0b52b74c5a5c7b78b662d1f94e3478`
- License identified by upstream build metadata/source headers: Apache License 2.0

When AOSP source is actually imported, preserve required copyright/license notices and add an appropriate copy of the Apache License 2.0 / NOTICE information alongside the imported/adapted source.

## Google Clock reference APK

The Google Clock APK and its split APKs are **reference inputs only** and must not be committed to this repository or redistributed as Clocky source/assets.

Known local reference hashes:

```text
base.apk
SHA-256 58fc4c98cfc80786d81a927fed6b87852b8367734f758523c8264be5a2ded21d

split_config.xhdpi.apk
SHA-256 65ca09d76f4e7b5dc174eee36be60c1670ae5578352df55fd2570b12fbad9191
```

Allowed repository outputs from reference analysis:

- measurements;
- behavioural descriptions;
- state-transition notes;
- independently written parity tests/specifications;
- screenshots only where separately permitted/appropriate for internal reference documentation.

Do not copy into Clocky:

- proprietary Google source code;
- extracted Google image/vector/font assets;
- Google signing material;
- compiled Google resources as shipping Clocky resources.

## Clocky identity

Clocky remains a separately signed application. Its package/application identity must not rely on Google or platform signing keys.
