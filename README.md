# Clocky

Clocky is an Android home-screen clock widget focused on typography and fine-grained visual customization.

The first goal is simple: keep the reliability and low-maintenance behavior of a normal Android clock widget, while giving the user much more control over how the time and date look.

## Project status

**Planning / pre-MVP**

The repository is being initialized from an analysis of the Google Clock Digital widget currently used on a Motorola launcher. Clocky will be an independent implementation; no Google Clock source code or bundled visual assets are copied into this project.

## MVP direction

The MVP starts with a **4×2 home-screen widget** and adds **4×1 support immediately after the 4×2 baseline is stable**.

Core MVP goals:

- Android home-screen `AppWidget`
- Kotlin implementation
- `RemoteViews` / classic AppWidget architecture rather than Glance, so typography can remain flexible
- transparent-first visual design
- time and date treated as independently configurable elements
- 12-hour / 24-hour clock behavior
- multiple font families grouped by clearly different visual styles instead of exposing many near-duplicates as separate top-level choices
- fine control over time size, date size, weight, spacing, alignment, position, color, opacity and background
- per-widget-instance settings
- presets that are editable parameter sets rather than locked templates
- low-update-cost clock behavior; avoid unnecessary periodic full-widget refreshes

## Initial size targets

Reference device measurements used during planning:

| Item | Value |
|---|---:|
| Display | 720 × 1600 px |
| Density | 280 dpi / 1.75 px per dp |
| Logical display | ~411 × 914 dp |
| Launcher grid | 4 columns |
| Reference widget | Google Clock Digital |
| Reference AppWidget ID | 11 |
| Current span | 4 × 2 |
| Minimum launcher span | 2 × 1 |
| Measured 4×2 host bounds | 692 × 484 px |
| Measured 4×2 host size | ~395.4 × 276.6 dp |
| Expected 4×1 host size on this launcher | ~395.4 × 138.3 dp |

These values are **reference measurements, not universal hard-coded dimensions**. Clocky must use AppWidget host-provided size information rather than assuming every launcher uses the same cell geometry.

## Customization model

Clocky separates the clock and date into independent design objects.

### Time

- font family
- style / variant
- size
- weight
- letter spacing
- color
- opacity
- horizontal alignment
- X/Y offset
- 12h / 24h mode
- leading zero behavior

### Date

- show / hide
- font family (same as clock or independent)
- size
- weight
- letter spacing
- color
- opacity
- date format
- horizontal alignment
- X/Y offset
- spacing relative to time

### Background / layout

- transparent / filled background
- background opacity
- corner radius
- padding
- global alignment
- global vertical position
- optional per-size layout tuning

## Font organization

Clocky should not present a huge flat list of nearly identical fonts. Fonts will be grouped into recognizable families such as:

- Modern
- Rounded
- Serif
- Condensed
- Mono
- Display / Experimental

Each family may contain a small number of meaningful variants. Presets can refer to these families and variants.

Fonts added to the application must have licensing terms compatible with redistribution in the APK. Apple proprietary fonts are not to be bundled simply to imitate iOS.

## Presets

Presets are parameter bundles, not immutable designs. A user can select a preset and then change any individual property.

Example preset categories may include:

- Clean
- Lock-screen inspired
- Editorial
- Minimal
- Retro
- Tech

## Size strategy

### Phase 1 — 4×2

Build and stabilize the complete customization pipeline at 4×2.

### Phase 2 — 4×1

Add 4×1 before significantly expanding the font/preset library. 4×1 should have its own layout profile where necessary rather than simply scaling the 4×2 design to half height.

Shared properties should normally remain shared across sizes, while size-sensitive properties can have overrides:

- clock size
- date size
- X/Y offsets
- clock/date arrangement
- margins and spacing

## Technical direction

Planned architecture:

```text
Clocky app
├─ Widget configuration UI
├─ WidgetSettings repository
│  └─ settings keyed by appWidgetId
├─ ClockWidgetProvider
├─ ClockWidgetRenderer
│  ├─ 4x2 profile
│  └─ 4x1 profile
├─ RemoteViews layouts
├─ bundled redistributable font resources
└─ preset definitions
```

The widget should rely on platform clock-capable views/formatting where practical, and only trigger full RemoteViews updates when settings, date-dependent content, locale/timezone, widget options, or other relevant state changes require it.

## Documents

- [`MVP_計画書.md`](./MVP_計画書.md) — MVP scope, architecture, acceptance criteria and implementation decisions
- [`実装予定表.md`](./実装予定表.md) — ordered implementation phases and completion gates

## Non-goals for the first MVP

- replacing the Android system lock-screen clock
- requiring root / SystemUI modification
- arbitrary user-imported font files
- seconds display with an expensive custom per-second update loop
- exhaustive launcher-specific pixel matching
- copying proprietary Google or Apple assets

## Lock-screen note

Clocky is initially a **home-screen widget**. Replacing the system lock-screen clock is outside the normal permissions of a third-party Android application. If supported lock-screen widget hosting becomes available on a target device/OS, that can be investigated separately without making it a dependency of the MVP.

## License

TBD.
