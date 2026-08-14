# Existing Clocky MVP → AOSP DeskClock integration

## Goal

Preserve the customization ideas already proven by the Clocky MVP while replacing its minimal clock-only functional core with the vendored AOSP DeskClock domain/application base.

## Source roles

```text
third_party/aosp-deskclock/  immutable upstream provenance
app/                         standalone AOSP adaptation + Clocky product code
legacy/mvp-app-skeleton/     snapshot of pre-port Gradle/app skeleton
ci/Clocky_MVP_source.zip     previous runnable MVP source bundle retained for reference
```

## MVP capability mapping

| Existing MVP concept | New owner | Integration rule |
|---|---|---|
| per-appWidgetId settings | Clocky customization store | preserve semantic model; migrate keys when old schema is decoded |
| Digital time/date | AOSP Digital provider + Clocky render layer | AOSP supplies lifecycle/data; Clocky controls presentation |
| 4×2 baseline | size profile | becomes explicit profile override |
| compact/4×1 foundation | size profile | host-size-driven override, no hard-coded launcher assumption |
| time/date independent size | Clocky model | retained |
| font-family selection | Clocky typography layer | retained; only redistributable/system fonts |
| alignment | Clocky layout layer | retained |
| colors/background | Clocky style layer | retained |
| signed MVP pipeline | release tooling | keep signing identity policy separate from source port |

## Integration boundaries

AOSP owns:

- alarms and scheduling;
- timer state;
- stopwatch state;
- world-clock/city data;
- main clock lifecycle;
- baseline Digital/Analog widget update mechanics.

Clocky owns:

- Google-like visual parity layer;
- widget settings/presets;
- typography including requested Weight 100–900;
- independent time/date styling;
- size-specific widget composition overrides;
- future preset/editor UX.

## Do not merge responsibilities

- Do not create a second timer/stopwatch data store for widgets.
- Do not modify `third_party/aosp-deskclock/`; changes live in `app/`.
- Do not let Google-parity defaults become hard-coded obstacles to Clocky customization.
- Do not use old MVP code to replace more complete AOSP domain behavior unless a measured regression is documented.

## Migration order after first compile

1. Make AOSP app tree compile with public SDK dependencies.
2. Verify Alarm/Timer/Stopwatch/World Clock baseline state survives process recreation.
3. Verify AOSP Digital/Analog providers register and update.
4. Introduce `WidgetSettingsStore` without changing default visuals.
5. Add Google-like preset geometry.
6. Layer old MVP presentation controls one at a time.
7. Add Weight renderer with requested/effective weight disclosure.
8. Add Stacked/Cities/Stopwatch reference families.

## Migration compatibility

The new model uses schema-versioned JSON per `appWidgetId`. A future migration reader may import the MVP's earlier primitive SharedPreferences keys; until that reader is added, the old source bundle remains the source of truth for old key names.

## Completion condition

Integration design is complete when every MVP capability has one new owner and there is no duplicate domain state between AOSP and Clocky. Runtime integration is intentionally deferred to compile/device phases.
