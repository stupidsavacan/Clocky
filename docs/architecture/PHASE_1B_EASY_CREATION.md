# Phase 1B — Easy Creation: design record

> Status: **implementation design for Phase 1B** (`docs/product/CLOCKY_END_STATE.md` §6, §13).
> The End-State is authoritative. This file records how Phase 1B reaches its exit gate on top of the
> Phase 1A architecture (`PHASE_1A_DIGITAL_CORE.md`) and the decisions made on the way.

Exit gate: **a first-time user can create an attractive Digital clock in a few taps, starting from a
curated design rather than a blank editor.**

Out of scope (later phases): Studio, Info line / next alarm (2), bundled font library (2), direct canvas
manipulation, Undo/Redo, Square / Large, size-class override UI, My Designs / Favorites storage,
import / export / share, backup / restore, `requestPinAppWidget`, generated picker previews, other
families (3+).

## 1. What was added to Design v2

Phase 1A's pipeline is unchanged: `Design v2 → DesignResolver → DigitalWidgetComposer →
launcher / PreviewHost`. Phase 1B extends each stage; there is no second renderer, store or provider.

| Stage | Addition |
|---|---|
| Model | `DigitalDesign.style: StyleTokens?` (palette snapshot, `ThemeMode`, `fontPrimary` / `fontSecondary`, `TextSizeStep`), `DigitalDesign.source: DesignSource?` (built-in id + version + kit), `ColorRef.Token(role)`, reserved font ids `token:fontPrimary` / `token:fontSecondary`, `Template` gains `CENTER_STACK`, `INLINE`, `SPLIT`, `MINIMAL`, `LayoutPatch.template` |
| Codec | Additive optional keys of **schema 2** (`style`, `source`, `color.type = "token"`, `layout.template` under overrides). Phase 1A documents decode unchanged; a pre-1B app ignores the new keys, so no schema bump and no migration are needed. |
| Resolver | Token colors by `ThemeMode`; token fonts; text-size step scaling; per-class template; `MINIMAL` hides the date without rewriting the requested visibility; `SPLIT` fixes alignment (date START, time END) |
| Composer | One template layout per composition (`clocky_digital_widget{,_date_first,_inline,_split_row}`, same ids), theme-bound colors, inline baseline lift |
| Library | `design/library`: `Palettes`, `Typefaces`, `Kits`, `BuiltinDesigns`, `QuickTune` (pure Kotlin) |
| UI | `DigitalWidgetConfigActivity` is now the Gallery / Quick Tune host; the Phase 1A editor moved, unchanged, to `DigitalWidgetAdvancedActivity` ("Detailed edit") |

### Tokens, not copies

Elements reference tokens (`ColorRef.Token(PRIMARY)`, `token:fontPrimary`) so that **Quick Tune changes
only tokens** and the kit's proportions (sizes, letter spacing, alignment, background, opacity) survive.
`QuickTune` is a set of pure functions over `DigitalDesign`:

| Control | Operation |
|---|---|
| Color | `selectPalette` (kit palettes), `selectMaterialYou`, `setFollowSystem` |
| Typeface | `selectTypeface(category)` → the kit's `TypePair` for that category |
| Layout | `selectTemplate(sizeClass, template)` — writes the per-class patch only |
| Text size | `setTextSize(S/M/L)` — a token; scales time and date together after overrides |
| Date | `setDateVisible` — one switch; per-class date overrides are cleared so it always means what it says |
| Surprise me | `surprise` — picks one entry of the kit's `KitCombo` table (palette × typeface × per-class templates), never the one currently shown |

"S / M / L" is labelled **Text size** and is a `TextSizeStep`; it is unrelated to Strip / Card size classes.

### Built-in assets are snapshots

`BuiltinDesigns` is an immutable, versioned source asset (`LIBRARY_VERSION = 1`). `instantiate()` hands
the widget a copy that carries its own palette colors, font specs and a `DesignSource(builtinId,
version, kitId)`. The widget never looks the asset up again, so editing a built-in later cannot change a
placed widget. (Quick Tune does look the **kit** up by `source.kitId`, but only for its vocabulary of
choices — palette options, typeface pairs — and never to re-read colors.) A widget saved by an older build with no `style` is
"not tunable" and keeps the detailed editor.

## 2. Kits and the eight designs

A Kit is a design grammar: a typography vocabulary (six `TypefaceCategory` → `TypePair`), the palettes
that suit it, and a table of visually compatible combinations. Designs are built from the grammar, not
assembled ad hoc. Visual reference: boards Kit 01 Default, 02 Hairline, 04 Poster, 05 Editorial of the
26-board mockup (`docs/product/design/Clocky_widget_design_mockup.html`).

| Kit | Design | Look | Card | Strip |
|---|---|---|---|---|
| Default | **Clocky Default** (`clocky-default`) | Bare light text, sans 300 time over 500 date, wallpaper shows through | Center Stack | Inline |
| Default | **Tonal** (`default-tonal`) | Solid mint card (24dp), tone-on-tone text | Center Stack | Inline |
| Minimal | **Hairline** (`minimal-hairline`) | Thin 200 time only, wide tracking, bare | Time only | Time only |
| Minimal | **Quiet Split** (`minimal-quiet-split`) | Glass card (40% mono), wide-tracked date opposite the time | Split | Split (row) |
| Bold | **Poster** (`bold-poster`) | Heavy 900 time, orange accent date, bare | Time first | Inline |
| Bold | **Block** (`bold-block`) | Solid orange card (12dp), ink text | Split | Split (row) |
| Editorial | **Serif** (`editorial-serif`) | Serif time, tracked small date, bare | Center Stack | Inline |
| Editorial | **Paper** (`editorial-paper`) | Warm paper card (28dp, 94%), serif time | Split | Split (row) |

Clocky Default follows End-State §5.8 (Card → Center Stack, Strip → Inline).

### Typography without new fonts

Phase 1B has **no bundled fonts and redistributes none** (the OFL library is Phase 2). The six
categories map onto platform families that the Phase 1A catalog already renders exactly:

| Category | Time / date |
|---|---|
| Modern | system sans, kit weights (e.g. Default 300 / 500) |
| Rounded | `sans-serif-rounded` |
| Serif | `serif` / system sans for the date |
| Condensed | `sans-serif-condensed` |
| Mono | `monospace` |
| Display | system sans, kit's heavy weights (e.g. Bold 900 / 700) |

Non-sans families are exact only at weight 400 (Phase 1A rule), so their pairs use 400. The mockup's
Fraunces / Big Shoulders / Righteous are therefore approximated, not copied. When the bundled library
arrives, only the font ids in `Typefaces.pairs` change.

## 3. Theme modes: Fixed / Follow system / Material You

`ThemeMode` is part of the tokens and is a **request**; the resolver decides what is effective.

| Mode | API 31+ | below API 31 |
|---|---|---|
| Fixed | palette `fixedVariant` | same |
| Follow system | `RemoteViews.setColorInt(notNight, night)` — the launcher flips Light/Dark itself | variant for the night mode at the last update; disclosed (`ThemeSwitchUnavailable`) |
| Material You | `RemoteViews.setColor(@ColorRes)` onto `clocky_dyn_*` resources that alias `system_accent*` in `values-v31` / `values-night-v31`; the launcher resolves them with its own wallpaper palette and night mode | the design's own palette (Light/Dark by night mode at update); disclosed (`DynamicColorUnavailable`) |

Consequences, all deliberate:
- no periodic app-driven updates: day/night and wallpaper changes are handled by the launcher;
- the requested mode is stored unchanged; only the effective color degrades, and the editor says so;
- a color resource cannot carry alpha, so Material You applies element opacity through `setAlpha` on the view;
- the widget-config preview applies the same RemoteViews with the application context, so it resolves
  day/night and system colors like a launcher does.

The detailed editor edits literal colors and fonts, so opening it **detaches** a design from its tokens
(`QuickTune.detach` freezes them into the values they currently show). Studio (Phase 2) edits through
tokens and removes this limitation.

## 4. Templates

| Template | Layout | Notes |
|---|---|---|
| `TIME_FIRST` | vertical, time then date | Phase 1A; honors element alignment |
| `CENTER_STACK` | vertical, date then time | honors alignment (default centered) |
| `INLINE` | one row, time then date | row alignment = time alignment; date baseline-lifted by `(timePx − datePx) × 0.21` |
| `SPLIT` | Card: date START above time END. Strip: row, date left / time right | alignment is forced by the template |
| `MINIMAL` | time only | requested date visibility retained, not rendered |

Quick Tune offers four per size class: Card — Centered, Time first, Split, Time only; Strip — Side by
side, Split, Time first, Time only. Gallery and preview use the host's current size class.

## 5. Easy Creation flow

```
launcher add ─▶ DigitalWidgetConfigActivity
                  ├─ no saved design ─▶ Gallery (Clocky Default selected)
                  │       ├─ [Add this clock]  ─▶ save snapshot, update, RESULT_OK        (1 tap)
                  │       ├─ card ─▶ [Add this clock]                                      (2 taps)
                  │       └─ [Customize] ─▶ Quick Tune ─▶ [Done]
                  ├─ saved + tokens ─▶ Quick Tune on the saved design (launcher reconfigure)
                  └─ saved, no tokens (Phase 1A) ─▶ detailed editor, with "Browse clocks"
Quick Tune: "Detailed edit" ─▶ DigitalWidgetAdvancedActivity (saves itself, returns RESULT_OK)
```

- Nothing is persisted before the user finishes (Add / Done / the detailed editor's Save).
- Back from the Gallery returns `RESULT_CANCELED`, which makes the launcher delete a freshly added widget
  (AppWidget result contract). Cancelling a reconfigure leaves the widget as it was.
- Gallery cards and the Quick Tune preview render through `DesignPreview` = `DigitalWidgetUpdater.buildPortrait`
  (resolve → fit → compose) applied locally — the same RemoteViews the launcher receives, with live
  TextClocks. There is no Gallery-only renderer.
- Gallery moods are the four kits (All / Default / Minimal / Bold / Editorial). Favorites and My
  Designs tabs of the mockup are Phase 3 and are not present.
- Quick Tune controls have 48dp hit areas (NOTES.md); Surprise me is constrained by `Kit.combos`.

## 6. Decisions and open questions

1. **Palette chips are the kit's own list** (6 per kit) plus Material You, instead of the mockup's three
   wallpaper-derived swatches. Wallpaper hints (`WallpaperManager.getWallpaperColors`) are a separate
   piece of work; Material You already tracks the launcher's wallpaper extraction on API 31+.
2. The date is always rendered uppercase by the font fragments (Phase 1A). The Editorial / Bold / Minimal
   kits want that; a As-is / UPPER choice arrives with Studio.
3. Mockup italics (Fraunces italic) are not available from system families.
4. Strip templates `SPLIT` and `INLINE` need roughly 40–60dp of height; the common-scale fit (Phase 1A)
   keeps them inside the host.
5. Surprise me is deterministic per call only through its `Random` argument (tests seed it).
