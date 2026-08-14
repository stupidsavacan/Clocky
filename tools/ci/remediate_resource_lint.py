#!/usr/bin/env python3
"""Resolve narrowly-scoped resource lint issues inherited by the standalone DeskClock port.

The fixes are intentionally explicit: correct the Android tools namespace, mark the six currently
base-language-only notification channel labels as intentionally untranslated until Clocky owns
localized copies, and repair pt-PT plural items so the selected quantity is represented in text.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if new in text:
        print(f"already remediated {path}")
        return
    if old not in text:
        raise RuntimeError(f"expected pattern not found in {path}: {old[:120]!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"updated {path}")


# Android's tools namespace is HTTP, not HTTPS.
replace_once(
    "app/src/main/res/values/donottranslate_events.xml",
    '<resources xmlns:tools="https://schemas.android.com/tools" tools:ignore="TypographyDashes">',
    '<resources xmlns:tools="http://schemas.android.com/tools" tools:ignore="TypographyDashes">',
)

# These channel labels were added to the base resource set without translated counterparts.
# Keep the existing runtime fallback (English base text) explicit until Clocky ships owned
# translations, rather than generating dozens of fake copies solely to silence MissingTranslation.
strings = "app/src/main/res/values/strings.xml"
for name in (
    "firing_alarms_timers_channel",
    "alarm_missed_channel",
    "alarm_snooze_channel",
    "alarm_upcoming_channel",
    "stopwatch_channel",
    "timer_channel",
):
    replace_once(
        strings,
        f'<string name="{name}">',
        f'<string name="{name}" translatable="false">',
    )

# In pt-PT Android plural rule `one` can cover more than the literal number 1. A hard-coded "1"
# therefore loses the selected quantity. Preserve the actual value in every singular-form item.
pt = "app/src/main/res/values-pt-rPT/strings.xml"
replacements = (
    ('<item quantity="one">1 min</item>',
     '<item quantity="one"><xliff:g id="NUMBER">%d</xliff:g> min</item>'),
    ('<item quantity="one">Suspenso durante 1 minuto</item>',
     '<item quantity="one">Suspenso durante <xliff:g id="MINUTES">%d</xliff:g> minuto</item>'),
    ('<item quantity="one">1 dia</item>',
     '<item quantity="one"><xliff:g id="NUMBER">%s</xliff:g> dia</item>'),
    ('<item quantity="one">1 hora</item>',
     '<item quantity="one"><xliff:g id="NUMBER">%s</xliff:g> hora</item>'),
    ('<item quantity="one">1 h</item>',
     '<item quantity="one"><xliff:g id="NUMBER">%s</xliff:g> h</item>'),
    ('<item quantity="one">1 minuto</item>',
     '<item quantity="one"><xliff:g id="NUMBER">%s</xliff:g> minuto</item>'),
    ('<item quantity="one">1 min</item>',
     '<item quantity="one"><xliff:g id="NUMBER">%s</xliff:g> min</item>'),
    ('<item quantity="one">1 segundo</item>',
     '<item quantity="one"><xliff:g id="NUMBER">%s</xliff:g> segundo</item>'),
    ('<item quantity="one">1 minuto</item>',
     '<item quantity="one"><xliff:g id="FORMATTED_NUMBER">%s</xliff:g> minuto</item>'),
    ('<item quantity="one">Temporizador ignorado</item>',
     '<item quantity="one"><xliff:g id="NUMBER">%d</xliff:g> temporizador ignorado</item>'),
)
for old, new in replacements:
    replace_once(pt, old, new)

print("Resource lint remediation complete")
