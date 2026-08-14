# Widget parity specification

## Families

Clocky tracks five Google Clock reference widget families:

1. Digital
2. Digital Stacked
3. Digital Cities
4. Analog
5. Stopwatch

The AOSP snapshot already supplies a functional Digital/Analog baseline; the additional Google families are Clocky parity work built on the same app/domain state.

## Shared widget contract

Every family must define:

- provider metadata and resize bounds;
- state source;
- update triggers;
- click destinations;
- 12/24-hour behavior where relevant;
- date/next-alarm behavior where relevant;
- light/dark behavior;
- accessibility content descriptions;
- restore/rebind behavior;
- RTL and font-scale behavior;
- Clocky customization capability or an explicit reason it is not applicable.

## Digital

State:

- current local time;
- optional date according to reference breakpoint;
- next alarm according to reference state/breakpoint;
- host-provided width/height.

Update triggers:

- AppWidget update;
- date changed;
- locale changed;
- time/time-zone changed;
- next alarm changed;
- day boundary;
- world-city state only where displayed.

Known baseline capture:

- reference host: 720×1600 px / 280 dpi device;
- observed 4×2 HostView about 692×484 px (~395.4×276.6 dp);
- compact profile remains host-size-driven, not a hard-coded 4×1 pixel size.

Clocky extensions:

- time/date font family;
- requested weight 100–900 independently;
- size;
- letter spacing;
- color/opacity;
- X/Y offsets;
- alignment;
- background/padding/radius;
- size-specific overrides.

## Digital Stacked

Required logical elements:

- hour/minute presentation matching observed stacked composition;
- date/secondary information only at observed breakpoints;
- 12/24-hour handling;
- resize behavior that recomposes rather than merely scales when the reference does so.

Clocky customisation applies independently to each text role where practical; linked editing may be offered as a convenience.

## Digital Cities

Required state:

- local/current city role as observed;
- selected world-city rows;
- city name + time;
- time-zone and DST updates;
- empty/no-city state;
- click-through to Clock/world-city management.

City data must come from the AOSP/Clocky world-clock domain, not duplicated widget-only storage.

## Analog

Required state:

- hour/minute hands;
- dial;
- system time/time-zone response;
- reference click destination;
- size-safe scaling.

Clocky extension target after parity:

- approved dial presets;
- color/opacity/background controls;
- optional hand styling where it does not compromise legibility.

## Stopwatch

Required state:

- idle;
- running;
- paused;
- elapsed time synchronized with in-app Stopwatch state;
- action/tap behavior matching the reference flow.

The widget must not maintain an independent stopwatch clock. AOSP/Clocky stopwatch domain state is the single source of truth.

## Resize matrix

For each family, capture and freeze these states before pixel-parity sign-off:

| Width class | Height class | Required record |
|---|---|---|
| minimum | minimum | visibility + composition |
| compact | normal | text scale + padding |
| normal | compact | horizontal/stacked decision |
| reference default | reference default | full geometry |
| expanded | expanded | maximum useful composition |

Host cell counts are descriptive only; implementation uses `AppWidgetManager` size options in dp.

## Click matrix

| Element | Digital | Stacked | Cities | Analog | Stopwatch |
|---|---|---|---|---|---|
| main clock area | Clock app | Clock app | Clock app | Clock app | Stopwatch |
| city row | n/a | n/a | city/world-clock surface | n/a | n/a |
| next alarm | reference-captured destination | reference-captured | where shown | n/a | n/a |
| action controls | n/a | n/a | n/a | n/a | reference-captured action |

Exact PendingIntent destinations are frozen from observed behavior before implementation is marked parity-complete.

## Definition of done

Pre-build specification is complete when every family has state, data source, update triggers, resize states, click roles, accessibility and Clocky-extension rules. Runtime parity remains unverified until APK/device testing.
