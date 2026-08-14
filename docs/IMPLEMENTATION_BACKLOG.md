# Clocky implementation backlog — no-build foundation

This backlog orders work so that architecture and behaviour are decided before spending time producing APKs.

## Phase F0 — repository / upstream foundation

- [x] Pin an AOSP DeskClock upstream revision.
- [x] Document Apache-2.0 handling and Google-reference boundaries.
- [x] Create a Google Clock parity matrix.
- [x] Specify numeric font-weight behaviour.
- [ ] Expand the current MVP source archive into reviewable normal source files.
- [ ] Remove temporary build-export/recovery files from the normal development path after confirming no needed data is lost.

## Phase F1 — AOSP standalone-port inventory

- [ ] Inventory every AOSP DeskClock package/file used by alarm, timer, stopwatch, world clock and shared data.
- [ ] Classify dependencies as: Android public API / AndroidX-Material / AOSP-internal / replaceable.
- [ ] Decide Clocky package namespace and migration strategy.
- [ ] Produce standalone Gradle source-set mapping.
- [ ] Define persistence migration rules for existing Clocky widget preferences.

Deliverable: a port map detailed enough that code can be moved without guessing.

## Phase F2 — Google Clock UX specification

For each screen: record empty state, populated state, menus, dialogs/sheets, transitions and system interactions.

- [ ] Clock / world clock
- [ ] Alarm list
- [ ] Alarm editor
- [ ] Timer setup
- [ ] Running timer
- [ ] Stopwatch + laps
- [ ] Settings
- [ ] Digital widget
- [ ] Digital Stacked widget
- [ ] Digital Cities widget
- [ ] Analog widget
- [ ] Stopwatch widget

Deliverable: behaviour specs + measurements, not copied assets.

## Phase F3 — Widget extension model

- [ ] Stabilise settings schema versioning.
- [ ] Add independent `timeWeight` and `dateWeight` (`100..900`).
- [ ] Add font capability/resolution model.
- [ ] Define independent size, spacing, colour and opacity fields.
- [ ] Define X/Y offsets.
- [ ] Define background radius/padding/opacity.
- [ ] Define size-specific overrides with inheritance.
- [ ] Define Google-like immutable baseline preset + editable user copy.

## Phase F4 — implementation after the foundation

Build order once source adaptation starts:

1. shared data/time infrastructure;
2. alarm scheduling/state;
3. timer;
4. stopwatch;
5. world clock;
6. Google-parity app UI;
7. Google-parity widget family;
8. Clocky visual extension controls;
9. accessibility + RTL + font-scale work;
10. real-device parity verification.

## Phase F5 — release discipline

- Keep the current signing identity for upgrade-compatible Clocky releases.
- Never commit signing passwords/JKS to a branch intended to become public.
- Produce signed APKs only after a reviewed source commit and parity/build checks.

## Definition of product success

A user familiar with Google Clock should be able to use Clocky without relearning the basic clock/alarm/timer/stopwatch experience, while gaining substantially deeper widget typography/layout controls.
