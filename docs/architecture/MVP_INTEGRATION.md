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
| per-appWidgetId settings | Clocky customization store | preserve semantic model; import only after each old persisted semantic has an exact current representation |
| Digital time/date | AOSP Digital provider + Clocky render layer | AOSP supplies lifecycle/data; Clocky controls presentation |
| 4×2 baseline | size profile | becomes explicit profile override |
| compact/4×1 foundation | size profile | host-size-driven override, no hard-coded launcher assumption |
| time/date independent size | Clocky model | retained |
| font-family selection | Clocky typography layer | retained; only redistributable/system fonts |
| alignment | Clocky layout layer | retained once RTL semantics are explicit |
| colors/background | Clocky style layer | retained once alpha/background semantics are explicit |
| signed MVP pipeline | release tooling | keep signing identity policy separate from source port |

## Integration boundaries

AOSP owns alarms/scheduling, timer state, stopwatch state, world-clock/city data, main clock lifecycle, and baseline widget update mechanics.

Clocky owns Google-like visual parity, widget settings/presets, typography including requested Weight 100–900, independent time/date styling, size-specific widget composition overrides, and future preset/editor UX.

## Do not merge responsibilities

- Do not create a second timer/stopwatch data store for widgets.
- Do not modify `third_party/aosp-deskclock/`; changes live in `app/`.
- Do not let Google-parity defaults become hard-coded obstacles to Clocky customization.
- Do not use old MVP code to replace more complete AOSP domain behavior unless a measured regression is documented.
- Do not dual-write new settings into the old primitive preference space.

## Verified legacy persistence contract

The repository-owned authority is `ci/Clocky_MVP_source.zip` (14,299 bytes, Git blob `6d63be2463ec408824a9b06eec282153c1a2df55`). The persistence/config/rendering contract was verified from its `WidgetPrefs.java`, `WidgetConfigActivity.java`, and `ClockWidgetRenderer.java` sources.

Legacy SharedPreferences:

- file: `clocky_widgets`
- per-widget prefix: `w_<appWidgetId>_`
- integer keys: `timeFont`, `dateFont`, `timeSize`, `dateSize`, `timeX`, `timeY`, `dateX`, `dateY`, `align`, `bg`, `hourMode`, `dateFormat`
- boolean key: `showDate`
- string keys: `timeColor`, `dateColor`
- there is **no persisted legacy `leadingZero` key**
- there are **no legacy per-profile preference keys**

Legacy font indices map to:

```text
0 sans-serif-light
1 sans-serif-rounded
2 serif
3 sans-serif-condensed
4 monospace
```

Legacy date-format indices map to:

```text
0 M月d日(E)
1 EEE, MMM d
2 yyyy.MM.dd
3 EEE d MMM
```

Legacy hour mode is `0 = follow system`, `1 = explicit 24h (HH:mm)`, `2 = explicit 12h (h:mm)`. Legacy alignment is `0 = START`, `1 = CENTER_HORIZONTAL`, `2 = END`. Legacy background is `0 = transparent layout`, `1 = dark layout`, `2 = light layout`; those layouts also participate in fallback color and adaptive-padding behavior.

## One-way migration rule

The current consolidated source of truth is schema-versioned JSON in `clocky_widget_settings`, key `widget.<appWidgetId>.settings`. A future primitive-key importer must obey all of the following:

1. Existing consolidated JSON wins; never overwrite it with old primitives.
2. Import only fields whose old semantics have an exact current representation.
3. Normalize and save through the current `WidgetSettingsStore`.
4. Only after a successful import, remove or permanently ignore that widget's old primitive keys so stale values cannot be resurrected.
5. Never write current values back to the legacy key space.
6. Leave current defaults untouched for concepts the old MVP did not persist, including `leadingZero` and profile overrides.

A full importer is intentionally deferred while some persisted old semantics remain under-specified in the current model/render contract: date-format default/override state, ARGB-alpha versus separate-opacity composition, LEFT/RIGHT versus legacy START/END in RTL, and exact old transparent/dark/light background mapping including fallback/API behavior. Partial or lossy migration is not acceptable.

## Migration order

1. Keep the standalone AOSP adaptation compiling and tested.
2. Keep `WidgetSettingsStore` as the sole current settings source of truth.
3. Layer source-defined Clocky presentation controls onto the Digital provider one at a time.
4. Preserve old intent only where an exact mapping is proven.
5. Add the one-way primitive importer after all persisted legacy semantics are representable without guessing.
6. Add remaining widget families and perform device/reference parity verification separately.

## Completion condition

Integration design is complete when every MVP capability has one new owner and there is no duplicate domain state between AOSP and Clocky. GitHub compile/test/lint validation does not substitute for launcher/device verification.