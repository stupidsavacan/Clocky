# Google Clock parity matrix

This file is the source-of-truth checklist for the product goal:

> **Google Clock-like default experience + Clocky-only customisation controls.**

Status legend:

- ✅ implemented in current Clocky MVP
- 🟡 partial / foundation exists
- ⬜ not implemented yet
- 🔎 reference behaviour still needs measurement/verification

## Application features

| Area | Google-parity target | Clocky status | Next work |
|---|---|---:|---|
| Alarm list | alarms visible/editable, enable/disable, repeat/label/sound/vibration flows | ⬜ | port AOSP alarm domain first |
| Alarm firing | notification/full-screen alarm flow, dismiss/snooze behaviour | ⬜ | identify public-API replacement for platform assumptions |
| Timer | create/run/pause/reset/delete timers | ⬜ | port AOSP timer domain |
| Stopwatch | start/pause/reset + laps | ⬜ | port AOSP stopwatch domain |
| World clock | city/time-zone list | ⬜ | port AOSP world-clock domain |
| Settings | Clock settings and defaults | ⬜ | map Google UX to AOSP settings model |
| Screensaver | clock screensaver behaviour where appropriate | ⬜ | defer until primary parity is stable |

## Widget families

Reference APK inventory currently tracks these user-facing widget families:

| Widget | Parity | Clocky extension layer |
|---|---:|---|
| Digital | 🟡 | ✅ existing custom widget foundation |
| Digital Stacked | ⬜ | ⬜ |
| Digital Cities | ⬜ | ⬜ |
| Analog | ⬜ | ⬜ |
| Stopwatch | ⬜ | ⬜ |

## Digital widget detail

| Behaviour | Status | Notes |
|---|---:|---|
| Current time display | ✅ | existing MVP foundation |
| Date display | ✅ | show/hide exists |
| Widget-local settings | ✅ | keyed per `appWidgetId` in MVP design |
| 4×2 profile | ✅ | baseline profile |
| compact/4×1 behaviour | 🟡 | foundation exists; parity measurement still needed |
| system 12/24h following | 🟡 | must be explicitly parity-tested |
| next-alarm display | ⬜ | Google reference behaviour to measure |
| resize breakpoints | 🟡 | host-size-aware direction exists; breakpoint parity incomplete |
| click destinations | 🔎 | measure each visible element and empty-state behaviour |
| launcher restore/rebind | 🔎 | test later on device |
| accessibility/content descriptions | 🔎 | add to parity tests |

## Google-like default visual preset

The default preset must reproduce the **behavioural geometry** of the reference as closely as practical without copying proprietary assets.

Record/verify for every relevant size:

- time baseline and bounding box;
- date baseline and bounding box;
- horizontal/vertical alignment;
- internal padding;
- background shape/radius/opacity when present;
- visibility breakpoints;
- minimum/target resize cells;
- type scale;
- type weight;
- 12/24h differences;
- next-alarm placement;
- touch targets;
- RTL behaviour;
- font-scale behaviour;
- dark/light theme behaviour.

## Clocky-only controls

These are deliberately **not** parity requirements. They layer on top of the Google-like preset.

| Control | Target |
|---|---|
| Time font family | independent |
| Date font family | independent |
| Time weight | 100–900 |
| Date weight | 100–900 |
| Time size | independent |
| Date size | independent |
| Letter spacing | independent |
| Colour / opacity | independent |
| X/Y offset | independent |
| Alignment | left / centre / right |
| Background | colour / opacity / radius / padding |
| Size override | settings may differ by widget size/profile |
| Presets | Google-like baseline + editable Clocky presets |

## Definition of “parity”

Parity does **not** mean byte-for-byte APK similarity or copied assets. A parity item is complete when a normal user can perform the equivalent task and the default screen/widget behaves substantially the same in ordinary use.

## Test protocol to add later

For each parity row:

1. Capture reference behaviour on the same Android version/launcher where possible.
2. Write expected state transitions before implementation.
3. Implement from AOSP/public Android APIs/Clocky code.
4. Compare side-by-side screenshots and interactions.
5. Record intentional deviations.
6. Mark parity only after real-device verification.
