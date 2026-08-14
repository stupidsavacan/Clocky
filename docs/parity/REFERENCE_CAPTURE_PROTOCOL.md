# Google Clock reference capture protocol

Use this protocol whenever a Google Clock screen or widget is measured for Clocky parity.

The goal is repeatable behavioural/UI measurement, not copying proprietary assets.

## Capture metadata

Record for every session:

```text
Reference app/version:
Android version:
Device:
Display px:
Density dpi / scale:
Font scale:
Launcher:
Theme: light/dark
Locale:
12h/24h system setting:
Date:
```

## Screen capture checklist

For each app screen capture at least:

1. empty/default state;
2. one-item state;
3. multi-item/scroll state;
4. active/running state where applicable;
5. paused/disabled state;
6. overflow/menu/sheet/dialog state;
7. error/permission state if reachable;
8. light + dark theme;
9. large font scale sanity check;
10. RTL sanity check where layout direction matters.

Record:

- top/bottom navigation geometry;
- typography hierarchy;
- icon/touch target positions;
- list spacing/dividers;
- FAB/primary action placement;
- modal/sheet behaviour;
- back navigation;
- animation/transition direction and approximate duration class (instant/short/medium/long);
- system permission handoffs;
- persistence after app restart.

## Widget capture checklist

For every widget family and resize breakpoint record:

```text
Widget family:
appWidgetId:
Host/launcher:
HostView px:
Approx dp:
Cell span:
min/max resize behaviour:
```

Measure:

- outer host bounds;
- actual rendered content bounds;
- left/right/top/bottom padding;
- time baseline and text bounds;
- date baseline and text bounds;
- next-alarm bounds;
- city labels/secondary clocks where present;
- background radius/opacity where visible;
- alignment mode;
- visibility changes at resize breakpoints;
- tap target and destination for every tappable region.

Repeat in:

- system 12h mode;
- system 24h mode;
- light theme;
- dark theme;
- at least one non-default locale;
- smallest supported widget size;
- largest practical size.

## Behaviour trace format

Write interactions as explicit state transitions:

```text
Given: alarm enabled for 07:30 on weekdays
When: user taps the enable switch
Then: alarm becomes disabled immediately
And: row remains present
And: next-alarm system state is recalculated
```

Avoid descriptions such as “works like Google Clock” without the observable state transition.

## Parity decision

A parity item can be marked complete only when:

- expected behaviour is written down;
- Clocky implementation is independently authored;
- side-by-side behaviour has been checked on a real device;
- intentional differences are documented;
- no proprietary Google resource was copied into shipping Clocky assets.

## Visual-diff convention

When screenshot comparison is introduced, keep reference and Clocky captures at the same:

- device resolution;
- density;
- font scale;
- theme;
- locale;
- widget span.

Track differences by category rather than chasing a single global pixel score:

- geometry;
- typography;
- colour/tone;
- iconography;
- state/visibility;
- interaction.
