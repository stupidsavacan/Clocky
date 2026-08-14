# AOSP DeskClock adoption plan

Clocky will use **AOSP DeskClock as the open-source functional base/reference** and keep the existing Clocky widget customisation layer as a separate product layer.

## Upstream pin

- Project: `platform/packages/apps/DeskClock`
- Preferred archival baseline: tag `android-17.0.0_r1`
- Pinned DeskClock commit for the first porting pass: `1f6ebf36d0c14f5e16265d80022cb6068d97cebd`
- Also inspected current `refs/heads/main`: `04e481f37e0b52b74c5a5c7b78b662d1f94e3478`
- Upstream build system: Soong (`Android.bp`)
- Upstream application id/package namespace: `com.android.deskclock`
- Upstream license: Apache License 2.0

The pin is intentional: parity work must be reproducible. Updating it is a separate reviewable change.

Upstream's own README says DeskClock is no longer actively supported and is retained as reference source, with possible future removal from the source manifest. Clocky must therefore **vendor/adapt a pinned snapshot** instead of relying on this repository remaining a maintained dependency.

## Why this is not a blind copy into `app/`

AOSP DeskClock is built as part of the Android platform and its `Android.bp` currently declares platform/AOSP dependencies. Clocky is a standalone Gradle application. The first porting task is therefore a **Gradle/standalone adaptation layer**, not simply copying the directory and hoping it builds.

## Functional areas to port/reuse

AOSP separates clock concerns into useful packages including:

- `alarms/`
- `timer/`
- `stopwatch/`
- `worldclock/`
- `settings/`
- `data/`
- `provider/`
- `controller/`
- `widget/`

Clocky should preserve those domain boundaries where practical so that Google-Clock-parity UI work does not become tangled with alarm/timer state handling.

## Target architecture

```text
Clocky
├─ core-clock/              # time/date formatting and shared models
├─ core-alarm/              # alarms, scheduling, notifications
├─ core-timer/              # timer domain
├─ core-stopwatch/          # stopwatch domain
├─ core-worldclock/         # city/time-zone domain
├─ feature-clock-ui/        # Google Clock parity UI surface
├─ feature-alarm-ui/
├─ feature-timer-ui/
├─ feature-stopwatch-ui/
├─ feature-worldclock-ui/
└─ feature-widget/
   ├─ google-parity/        # default behaviour/presets matching reference UX
   └─ clocky-custom/        # fonts, weight, position, spacing, colour, presets
```

This is the target separation. The first standalone port may remain in one Gradle module while package boundaries are established; splitting into Gradle modules is not a prerequisite for functional parity.

## Google Clock relationship

The proprietary Google Clock APK is treated as a **behavioural/UI reference only**:

- observe screens, layout, resize behaviour, states and transitions;
- record measurements and behaviour in repository documentation;
- independently implement equivalent behaviour;
- do not copy proprietary Google source, images, fonts, compiled resources or signing material into Clocky.

The intended user experience is: **Google Clock familiarity by default, Clocky customisation when the user asks for more control.**

## Migration sequence

1. Freeze/pin the AOSP DeskClock upstream revision.
2. Inventory AOSP functional packages and platform-only dependencies.
3. Adapt the needed core code to a standalone Gradle app and rename Clocky-owned namespaces.
4. Preserve existing Clocky widget settings concepts (`appWidgetId`-scoped settings).
5. Reach Google Clock parity screen-by-screen and widget-by-widget.
6. Add Clocky extensions on top, never inside parity behaviour unless needed.
7. Only after parity is stable, expand presets/fonts and advanced visual tooling.

## Non-goals during the port

- replacing SystemUI or Android lock-screen clock;
- pretending Clocky is signed by Google/AOSP platform keys;
- depending on proprietary Google Play services solely to imitate Google Clock;
- carrying AOSP platform-only build assumptions into the standalone app when Android public APIs can replace them.

## Done criteria for the foundation phase

The foundation phase is complete when:

- upstream commit and license are pinned/documented;
- parity checklist exists;
- Google-parity and Clocky-extension concerns are explicitly separated;
- font-weight behaviour is specified;
- source-import/Gradle-adaptation tasks are ordered before UI polish;
- APK build is not required to review these decisions.
