# Google Clock UI/UX parity specification

## Product rule

Clocky defaults should feel familiar to a Google Clock user while remaining independently implemented. The proprietary reference APK is an observation target only; AOSP/public Android code and Clocky-owned code provide the implementation.

## Global interaction contract

1. Primary clock functions remain reachable from the main navigation without entering Clocky customisation.
2. Back behavior must return to the prior Clock surface before leaving the app.
3. Destructive actions require the same level of intent as the reference; Clocky must not introduce easier accidental deletion.
4. Controls that represent on/off state use immediate visual state feedback.
5. Time-sensitive surfaces update without requiring manual refresh.
6. Light/dark behavior follows the selected/system theme unless a Clocky-specific override is explicitly enabled.
7. RTL, font scale, TalkBack labels, and 12/24-hour formatting are first-class parity states.

## Main navigation

Required destinations:

- Alarm
- Clock / world clocks
- Timer
- Stopwatch
- Settings entry

Acceptance:

- selected destination is visually unambiguous;
- switching destinations preserves running timers/stopwatch state;
- returning from settings restores the prior destination;
- configuration-only Clocky controls do not replace or hide core clock navigation.

## Alarm UX

### List state

- show alarm time prominently;
- enabled state is visible without opening alarm details;
- repeated days, label and next occurrence are secondary information;
- expand/edit interaction does not accidentally toggle the alarm.

### Editing

- time selection;
- repeat days;
- label;
- ringtone/sound;
- vibration;
- delete;
- enable/disable.

### Firing state

- full-screen/notification path as allowed by current Android APIs;
- clear dismiss and snooze actions;
- lock-screen behavior is tested separately from normal in-app behavior;
- app restart/device reboot must not silently lose scheduled alarms.

## Clock / world clock UX

- current local clock remains visually primary;
- selected cities display city name plus local time;
- add-city flow supports search and selection;
- removing/reordering cities must not alter alarm data;
- locale/time-zone changes refresh displayed values.

## Timer UX

Required states:

1. empty/setup;
2. running;
3. paused;
4. expired;
5. reset/deleted.

Required interactions:

- enter duration;
- start;
- pause/resume;
- add time where supported by the observed reference;
- reset/delete;
- expired notification/full-screen behavior according to public Android APIs.

Multiple timers must remain independent if the reference version supports them; exact presentation is frozen by the reference-capture protocol before visual parity is marked complete.

## Stopwatch UX

- start, pause/resume and reset;
- lap creation while running;
- lap list remains associated with the current session;
- backgrounding the app must not reset elapsed time;
- Stopwatch AppWidget reflects the same logical session.

## Settings UX

Preserve Google-like grouping for core clock settings, then place Clocky-specific customization in a clearly separate section. Core settings must not be hidden inside widget customization.

Clocky-specific entry points:

- widget preset/editor;
- typography;
- per-size layout overrides;
- import/export only when implemented later.

## Motion

Match the reference's purpose, not proprietary animation files. Record for each transition:

- trigger;
- start/end state;
- approximate duration class (instant / short / medium);
- easing character;
- whether motion conveys selection, expansion, creation or deletion.

Recreate motion with Android/Material primitives.

## Visual measurement sheet

Every reference capture records:

| Property | Required |
|---|---|
| viewport px + density | yes |
| screen/widget bounds | yes |
| content padding | yes |
| baseline/bounding boxes | yes |
| type size/weight estimate | yes |
| colors incl. alpha | yes |
| corner radii | yes |
| touch target bounds | yes |
| dark/light | yes |
| 12/24h | yes |
| locale/RTL | where applicable |

## Parity acceptance

A surface can be marked Google-parity complete only when:

1. equivalent normal-user task flow exists;
2. all reference states in `REFERENCE_CAPTURE_PROTOCOL.md` are represented;
3. geometry/type/color deviations are recorded and intentional;
4. proprietary source/assets are not copied;
5. a real-device comparison is completed later.

This document completes the pre-build UI/UX contract; device-derived numeric constants remain measurements rather than assumptions.
