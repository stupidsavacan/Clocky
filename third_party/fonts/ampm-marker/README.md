# Clocky AM PM Marker

Derived from the unchanged bundled `res/font/clocky_poppins_400.ttf` (Poppins Project Authors,
2020). Distributed under SIL OFL 1.1; the original copyright and license are in `OFL.txt`.
The family, full, unique and PostScript names are renamed to Clocky AM PM Marker.

`python tools/fonts/generate_ampm_marker.py` (fontTools 4.53.1) reproducibly subsets the font,
composes A/P + M outlines, and adds 24 required ligatures: `00`–`11` → AM, `12`–`23` → PM.
Unicode decimal digit aliases share the same input glyphs. Six generated TextClock layouts
retain the existing shadow variants. This font is only for the marker, outside FontCatalog and
the user-selectable font library. The committed font has no runtime fontTools dependency.
