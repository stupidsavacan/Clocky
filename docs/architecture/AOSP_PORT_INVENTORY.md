# AOSP DeskClock standalone-port inventory

This is the first dependency/porting map for bringing AOSP DeskClock functionality into a normal standalone Clocky Gradle application.

## Upstream snapshot strategy

Use a tagged AOSP snapshot as the archival baseline rather than depending on the continued existence of the live `main` branch.

Preferred first baseline:

- tag: `android-17.0.0_r1`
- DeskClock commit: `1f6ebf36d0c14f5e16265d80022cb6068d97cebd`

Also record the inspected `main` revision:

- `04e481f37e0b52b74c5a5c7b78b662d1f94e3478`

Reason: upstream's own README says DeskClock is no longer actively supported, is retained as reference source, and may eventually be removed from the source manifest. Clocky should therefore vendor/adapt a pinned snapshot rather than depend on AOSP DeskClock as a moving external dependency.

## Upstream domain/package inventory

The current source tree separates major domains into:

```text
actionbarmenu/
alarms/
controller/
data/
events/
provider/
ringtone/
settings/
stopwatch/
timer/
uidata/
widget/
worldclock/
```

This is a useful boundary for Clocky. We should port domain/data functionality before replacing presentation with Google-Clock-parity UI.

## Upstream build model

AOSP DeskClock is not a ready-made standalone Android Studio project. Its `Android.bp` declares:

- Soong `android_app` build;
- `sdk_version: current`;
- upstream `target_sdk_version: 30`;
- Kotlin source under `src/**/*.kt`;
- generated Java under `gen/**/*.java`;
- AOSP-named AndroidX/Material static libraries;
- `product_specific: true`;
- legacy aapt flag.

### Standalone Gradle mapping

| AOSP/Soong concept | Clocky standalone direction |
|---|---|
| `src/**/*.kt` | normal `src/main/java` or `src/main/kotlin` source set |
| `gen/**/*.java` | inspect and either vendor generated data with provenance or replace with generation task |
| `androidx.*` static libs | Maven AndroidX dependencies |
| `com.google.android.material_material` | Maven Material Components dependency |
| `product_specific: true` | remove; not meaningful for Play/sideload standalone app |
| `aaptflags: ["--legacy"]` | do not carry automatically; validate resources under modern AGP/aapt2 |
| package `com.android.deskclock` | migrate Clocky-owned code/resources to Clocky namespace |

## Manifest/component inventory

The AOSP manifest includes more than a launcher Activity. Porting must account for:

### Permissions

```text
RECEIVE_BOOT_COMPLETED
WAKE_LOCK
VIBRATE
DISABLE_KEYGUARD
READ_EXTERNAL_STORAGE
FOREGROUND_SERVICE
USE_FULL_SCREEN_INTENT
SCHEDULE_EXACT_ALARM
MODIFY_AUDIO_SETTINGS
POST_NOTIFICATIONS
```

Each permission must be re-reviewed against Clocky's target SDK instead of copied blindly. In particular, storage, notifications, exact alarms and full-screen alarm UX have changed across newer Android releases.

### Main app / settings / selection Activities

- main DeskClock Activity;
- ringtone picker;
- city selection;
- settings;
- shortcut/API handling;
- alarm UI;
- expired-timer UI;
- screensaver UI.

### Background components

- alarm provider;
- boot/time/timezone/package-change receiver;
- alarm state receiver;
- alarm service;
- timer receiver/service;
- stopwatch service;
- dream/screensaver service.

### AppWidget components

AOSP includes:

- analog AppWidget provider;
- digital AppWidget provider;
- digital-city RemoteViews service.

The AOSP digital widget listens for widget update plus next-alarm, date, locale, screen, time/timezone and city-change events. Clocky should preserve the behavioural intent but re-evaluate which broadcasts are necessary/allowed for a modern standalone target.

## First-pass dependency classes

### Class A — likely reusable with adaptation

- domain models/state machines;
- timer/stopwatch arithmetic;
- formatting utilities;
- city/time-zone data logic;
- settings models;
- public-API alarm scheduling logic where still current.

### Class B — reuse concept, rewrite integration

- services/receivers tied to target-SDK behaviour;
- notification/full-screen alarm integration;
- storage/ringtone access;
- manifest declarations;
- backup/direct-boot handling;
- AppWidget host/update plumbing.

### Class C — intentionally replace

- AOSP visual design where Google Clock parity is the target;
- launcher branding/icons;
- package/authority identifiers;
- platform/product build assumptions;
- any resource whose licensing/provenance is unsuitable for Clocky distribution.

## Source-port order

1. `data/`, shared clock/date/time-zone utilities and persistence contracts.
2. `timer/` and `stopwatch/` core state logic (lower Android-system coupling).
3. `worldclock/` data and city-selection logic.
4. `alarms/`, provider and scheduling infrastructure.
5. notification, exact-alarm and full-screen alarm integrations for Clocky's actual target SDK.
6. app API/shortcut integration.
7. AOSP AppWidget behaviour needed as functional fallback/reference.
8. Google Clock parity UI layer.
9. Clocky customisation layer.

## Questions that must be answered before first real build

- Which `gen/` files are required and how are they generated?
- Which AOSP resources reference platform-only/internal values?
- Which manifest attributes/permissions are obsolete or restricted at Clocky's target SDK?
- Which alarm/full-screen flows need runtime user-granted special access?
- What package/authority names need data migration from the existing MVP?
- Which existing Clocky widget settings can remain schema-compatible?
- Which AOSP widgets are useful functional code versus easier to rewrite from Clocky's current RemoteViews base?

## Rule for porting

Do not mechanically make compile errors disappear. For every platform dependency, decide whether the standalone app should:

1. use a public Android API;
2. use an AndroidX equivalent;
3. implement a small Clocky abstraction;
4. omit the platform-only behaviour with a documented reason.
