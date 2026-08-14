# Font Weight specification

Clocky exposes font weight as a first-class numeric setting rather than a `Bold` switch.

## User-facing model

Time and date have independent values:

```text
Time weight: 100 ... 900
Date weight: 100 ... 900
```

Default visual preset should use the weight measured for the Google Clock reference. Users may then move away from that baseline without changing the rest of the preset.

## UI

Primary control: continuous-looking slider with labeled anchor points.

```text
Thin                                              Black
100 ─────────────────●──────────────────────────── 900
                     560
```

Recommended interaction:

- slider range: `100..900`;
- UI step: `1` where the selected font genuinely supports variable `wght`;
- show the current numeric value;
- quick anchors: `100 200 300 400 500 600 700 800 900`;
- reset action returns to preset/default weight;
- time/date may be linked, but remain independently stored.

## Rendering contract

The settings model stores the **requested semantic weight** as an integer even when a renderer cannot reproduce every value exactly.

```text
requestedWeight = 563
```

Renderer resolves that value according to font capability:

1. Variable font with usable `wght` axis: attempt exact requested weight where the widget rendering path supports it.
2. Static family with multiple weight files: choose nearest available face.
3. System family with platform weight support: map to the closest supported platform weight.
4. Last-resort renderer: map to nearest canonical weight (`100` through `900`).

This means the settings/storage API does not need to change when a better renderer becomes available.

## Storage

Per-widget, per-element settings:

```text
widget.<appWidgetId>.time.fontWeight = Int
widget.<appWidgetId>.date.fontWeight = Int
```

Valid input is clamped to `100..900`.

Suggested default: `400` until the Google-like preset has been measured and frozen.

## Presets

A preset stores explicit requested weights:

```json
{
  "timeWeight": 400,
  "dateWeight": 400
}
```

Changing the weight must not silently change font family, size, letter spacing, alignment, or position.

## Size-specific overrides

Base setting:

```text
timeWeight = 560
```

Optional profile override:

```text
4x1.timeWeight = 600
```

If no size-specific override exists, inherit the widget base setting.

## Compatibility rule

The editor may present 1-unit precision, but it must not promise that every installed font produces 801 visually distinct weights. The preview should disclose/reflect the resolved weight for static font families.

## MVP implementation order

1. Add `timeWeight` / `dateWeight` to settings model with migration defaults.
2. Add slider + numeric value in config UI.
3. Add resolver from requested weight to renderable face/weight.
4. Apply independently to time/date.
5. Add Google-like preset values after reference measurement.
6. Add variable-font exact-axis support only where the chosen AppWidget rendering path is confirmed reliable.
