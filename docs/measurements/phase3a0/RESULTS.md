# Phase 3A-0 measurement record — 2026-10-08

Measured against main `dddd16c7dbfdf79129cf0f763812e33ecd575fc8`; PRs #42–#46 were verified merged. The diagnostic worktree is `spike/3a-size-map` and is never merged. This record contains measurement results only. No Square/Large production enum, General Patch, Studio class UI, schema, provider component, AOSP domain or release metadata was changed.

## Decisions

- **Size-class input:** `w = OPTION_APPWIDGET_MAX_WIDTH`, `h = OPTION_APPWIDGET_MIN_HEIGHT`, in dp. One widget class for both orientations; each RemoteViews entry still fits its actual reported size. These are the landscape-like options pair, not the dimensions of the current physical canvas. Invalid/missing options continue to require the existing safe Card fallback in the future resolver.
- **Constants:** `STRIP_MAX_H = 100dp`, `LARGE_MIN_H = 160dp`, `SQUARE_RATIO = 2.5625`. Order: Strip if h < 100; Square if w < 2.5625h; Large if h >= 160; otherwise Card. Classification is diagnostic only in the spike.
- **Size map:** **A**, host-reported sizes only. B is rejected on API35 at 4×4 for bitmap memory and costs more on moto. No synthetic anchor is necessary for the observed host-only selection.
- **Production minResize:** **NO for now**. Keep existing release metadata until SPLIT→Strip inflation is fixed and verified in 3A-2. This is a current-template failure, not a threshold conflict.
- **goAsync:** **YES** for future production generation; include fit and composition off the main thread. Compose-only timings understate the work. The spike prototype's UI-thread ANR was corrected with a diagnostic worker; no provider scheduling change was made to production.
- **Phase 3A implementation can start**, with the metadata and generation-budget gates below. Universal Latin AM/PM remains deferred.

## Hosts and reachability

| Host | API | Launcher | Display / density | Grid / observations |
|---|---|---|---|---|
| moto g13 | 34 | com.motorola.launcher3 | 720×1600px / 1.75 | 4 columns; 9 portrait cells |
| Pixel emulator, clocky-phase0 | 35 | com.google.android.apps.nexuslauncher | 1080×2400px / 2.625 | 4 columns; 9 portrait cells |
| Pixel emulator, clocky-api30 | 30 | com.google.android.apps.nexuslauncher | 1080×2280px / 2.75 | 5 columns; 12 portrait + 12 landscape cells |

For **each** 4-column host, 5×1, 5×2 and 5×3 are **Not reachable on this host**. Landscape for each of its 9 reachable cells is **Not exercised**: the home did not rotate when the reversible cdev orientation command was tried, and launcher rotation preferences were not changed. API30 home rotation worked and all 12 cells were measured in both orientations. Optional API31–33: Not exercised (no installed image); no launcher APK was downloaded. One UI/Nova/Lawnchair were unavailable and not attempted.

## Raw measurements and rule selection

The following **42 observed cell/orientation rows** use integer option values exactly as reported, not screenshot estimates. The SizeF list preserves reported fractional dp. Clipping/overlap columns test nonempty placed TextView rectangles against the widget root and each other; screenshot review found no obvious clipping/overlap in these current-template frames. They do not certify all templates or glyph/shadow ink. Ratios and signed margins are computed from the measured options. Launcher/package and density are in the host table; the companion raw-inputs.csv repeats them on every row.

| Host / API | Cell | Orientation | minW | minH | maxW | maxH | OPTION_APPWIDGET_SIZES (dp) | Chosen W/H (dp) | Ratio | Intended / chosen | Boundary margins h:100 / h:160 / ratio:2.5625 | Clipping / overlap | Local cdev evidence |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Pixel / 30 | 2x1 | landscape | 130 | 53 | 249 | 97 | N/A (API30) | 249 / 53 | 4.698113 | Strip / Strip | -47 / -107 / +2.135613 | False / False | s-20261008-001636-emulator-5562/0068-collect-pixel30-landscape-2x1 |
| Pixel / 30 | 3x1 | landscape | 203 | 53 | 382 | 97 | N/A (API30) | 382 / 53 | 7.207547 | Strip / Strip | -47 / -107 / +4.645047 | False / False | s-20261008-001636-emulator-5562/0071-collect-pixel30-landscape-3x1 |
| Pixel / 30 | 4x1 | landscape | 276 | 53 | 514 | 97 | N/A (API30) | 514 / 53 | 9.698113 | Strip / Strip | -47 / -107 / +7.135613 | False / False | s-20261008-001636-emulator-5562/0074-collect-pixel30-landscape-4x1 |
| Pixel / 30 | 5x1 | landscape | 349 | 53 | 647 | 97 | N/A (API30) | 647 / 53 | 12.207547 | Strip / Strip | -47 / -107 / +9.645047 | False / False | s-20261008-001636-emulator-5562/0077-collect-pixel30-landscape-5x1 |
| Pixel / 30 | 2x2 | landscape | 130 | 123 | 249 | 210 | N/A (API30) | 249 / 123 | 2.024390 | Square / Square | +23 / -37 / -0.538110 | False / False | s-20261008-001636-emulator-5562/0065-collect-pixel30-landscape-2x2 |
| Pixel / 30 | 3x2 | landscape | 203 | 123 | 382 | 210 | N/A (API30) | 382 / 123 | 3.105691 | Card / Card | +23 / -37 / +0.543191 | False / False | s-20261008-001636-emulator-5562/0062-collect-pixel30-landscape-3x2 |
| Pixel / 30 | 4x2 | landscape | 276 | 123 | 514 | 210 | N/A (API30) | 514 / 123 | 4.178862 | Card / Card | +23 / -37 / +1.616362 | False / False | s-20261008-001636-emulator-5562/0059-collect-pixel30-landscape-4x2 |
| Pixel / 30 | 5x2 | landscape | 349 | 123 | 647 | 210 | N/A (API30) | 647 / 123 | 5.260163 | Card / Card | +23 / -37 / +2.697663 | False / False | s-20261008-001636-emulator-5562/0056-collect-pixel30-landscape-5x2 |
| Pixel / 30 | 3x3 | landscape | 203 | 193 | 382 | 323 | N/A (API30) | 382 / 193 | 1.979275 | Square / Square | +93 / +33 / -0.583225 | False / False | s-20261008-001636-emulator-5562/0050-collect-pixel30-landscape-3x3 |
| Pixel / 30 | 4x3 | landscape | 276 | 193 | 514 | 323 | N/A (API30) | 514 / 193 | 2.663212 | Large / Large | +93 / +33 / +0.100712 | False / False | s-20261008-001636-emulator-5562/0047-collect-pixel30-landscape-4x3 |
| Pixel / 30 | 5x3 | landscape | 349 | 193 | 647 | 323 | N/A (API30) | 647 / 193 | 3.352332 | Large / Large | +93 / +33 / +0.789832 | False / False | s-20261008-001636-emulator-5562/0053-collect-pixel30-landscape-5x3 |
| Pixel / 30 | 4x4 | landscape | 276 | 263 | 514 | 436 | N/A (API30) | 514 / 263 | 1.954373 | Square / Square | +163 / +103 / -0.608127 | False / False | s-20261008-001636-emulator-5562/0044-collect-pixel30-landscape-4x4 |
| Pixel / 30 | 2x1 | portrait | 130 | 53 | 249 | 97 | N/A (API30) | 249 / 53 | 4.698113 | Strip / Strip | -47 / -107 / +2.135613 | False / False | s-20261008-001636-emulator-5562/0013-collect-pixel30-2x1 |
| Pixel / 30 | 3x1 | portrait | 203 | 53 | 382 | 97 | N/A (API30) | 382 / 53 | 7.207547 | Strip / Strip | -47 / -107 / +4.645047 | False / False | s-20261008-001636-emulator-5562/0017-collect-pixel30-3x1 |
| Pixel / 30 | 4x1 | portrait | 276 | 53 | 514 | 97 | N/A (API30) | 514 / 53 | 9.698113 | Strip / Strip | -47 / -107 / +7.135613 | False / False | s-20261008-001636-emulator-5562/0020-collect-pixel30-4x1 |
| Pixel / 30 | 5x1 | portrait | 349 | 53 | 647 | 97 | N/A (API30) | 647 / 53 | 12.207547 | Strip / Strip | -47 / -107 / +9.645047 | False / False | s-20261008-001636-emulator-5562/0022-collect-pixel30-5x1 |
| Pixel / 30 | 2x2 | portrait | 130 | 123 | 249 | 210 | N/A (API30) | 249 / 123 | 2.024390 | Square / Square | +23 / -37 / -0.538110 | False / False | s-20261008-001636-emulator-5562/0011-collect-pixel30-2x2 |
| Pixel / 30 | 3x2 | portrait | 203 | 123 | 382 | 210 | N/A (API30) | 382 / 123 | 3.105691 | Card / Card | +23 / -37 / +0.543191 | False / False | s-20261008-001636-emulator-5562/0030-collect-pixel30-3x2 |
| Pixel / 30 | 4x2 | portrait | 276 | 123 | 514 | 210 | N/A (API30) | 514 / 123 | 4.178862 | Card / Card | +23 / -37 / +1.616362 | False / False | s-20261008-001636-emulator-5562/0028-collect-pixel30-4x2 |
| Pixel / 30 | 5x2 | portrait | 349 | 123 | 647 | 210 | N/A (API30) | 647 / 123 | 5.260163 | Card / Card | +23 / -37 / +2.697663 | False / False | s-20261008-001636-emulator-5562/0025-collect-pixel30-5x2 |
| Pixel / 30 | 3x3 | portrait | 203 | 193 | 382 | 323 | N/A (API30) | 382 / 193 | 1.979275 | Square / Square | +93 / +33 / -0.583225 | False / False | s-20261008-001636-emulator-5562/0032-collect-pixel30-3x3 |
| Pixel / 30 | 4x3 | portrait | 276 | 193 | 514 | 323 | N/A (API30) | 514 / 193 | 2.663212 | Large / Large | +93 / +33 / +0.100712 | False / False | s-20261008-001636-emulator-5562/0035-collect-pixel30-4x3 |
| Pixel / 30 | 5x3 | portrait | 349 | 193 | 647 | 323 | N/A (API30) | 647 / 193 | 3.352332 | Large / Large | +93 / +33 / +0.789832 | False / False | s-20261008-001636-emulator-5562/0037-collect-pixel30-5x3 |
| Pixel / 30 | 4x4 | portrait | 276 | 263 | 514 | 436 | N/A (API30) | 514 / 263 | 1.954373 | Square / Square | +163 / +103 / -0.608127 | False / False | s-20261008-001636-emulator-5562/0040-collect-pixel30-4x4 |
| moto / 34 | 2x1 | portrait | 173 | 58 | 325 | 122 | 173.71428 × 122.28571; 325.7143 × 58.285713 | 325 / 58 | 5.603448 | Strip / Strip | -42 / -102 / +3.040948 | False / False | s-20261007-234013-ZY22GSDPFW/0022-collect-moto-2x1 |
| moto / 34 | 3x1 | portrait | 268 | 58 | 496 | 122 | 268.57144 × 122.28571; 496.57144 × 58.285713 | 496 / 58 | 8.551724 | Strip / Strip | -42 / -102 / +5.989224 | False / False | s-20261007-234013-ZY22GSDPFW/0025-collect-moto-3x1 |
| moto / 34 | 4x1 | portrait | 363 | 58 | 667 | 122 | 363.42856 × 122.28571; 667.4286 × 58.285713 | 667 / 58 | 11.500000 | Strip / Strip | -42 / -102 / +8.937500 | False / False | s-20261007-234013-ZY22GSDPFW/0028-collect-moto-4x1 |
| moto / 34 | 2x2 | portrait | 173 | 132 | 325 | 260 | 173.71428 × 260.57144; 325.7143 × 132.57143 | 325 / 132 | 2.462121 | Square / Square | +32 / -28 / -0.100379 | False / False | s-20261007-234013-ZY22GSDPFW/0017-collect-moto-2x2 |
| moto / 34 | 3x2 | portrait | 268 | 132 | 496 | 260 | 268.57144 × 260.57144; 496.57144 × 132.57143 | 496 / 132 | 3.757576 | Card / Card | +32 / -28 / +1.195076 | False / False | s-20261008-004632-ZY22GSDPFW/0021-collect-moto-3x2 |
| moto / 34 | 4x2 | portrait | 363 | 132 | 667 | 260 | 363.42856 × 260.57144; 667.4286 × 132.57143 | 667 / 132 | 5.053030 | Card / Card | +32 / -28 / +2.490530 | False / False | s-20261007-234013-ZY22GSDPFW/0010-collect-restored |
| moto / 34 | 3x3 | portrait | 268 | 206 | 496 | 398 | 268.57144 × 398.85715; 496.57144 × 206.85715 | 496 / 206 | 2.407767 | Square / Square | +106 / +46 / -0.154733 | False / False | s-20261007-234013-ZY22GSDPFW/0040-collect-moto-3x3 |
| moto / 34 | 4x3 | portrait | 363 | 206 | 667 | 398 | 363.42856 × 398.85715; 667.4286 × 206.85715 | 667 / 206 | 3.237864 | Large / Large | +106 / +46 / +0.675364 | False / False | s-20261007-234013-ZY22GSDPFW/0037-collect-moto-4x3-confirmed |
| moto / 34 | 4x4 | portrait | 363 | 281 | 667 | 537 | 363.42856 × 537.1429; 667.4286 × 281.14285 | 667 / 281 | 2.373665 | Square / Square | +181 / +121 / -0.188835 | False / False | s-20261008-004632-ZY22GSDPFW/0009-collect-moto-4x4 |
| Pixel / 35 | 2x1 | portrait | 172 | 62 | 332 | 108 | 172.19048 × 108.95238; 172.19048 × 104.0; 332.1905 × 62.857143; 332.1905 × 62.857143 | 332 / 62 | 5.354839 | Strip / Strip | -38 / -98 / +2.792339 | False / False | s-20261007-235716-emulator-5560/0022-collect-pixel35-2x1 |
| Pixel / 35 | 3x1 | portrait | 266 | 62 | 504 | 108 | 266.2857 × 108.95238; 266.2857 × 104.0; 504.38095 × 62.857143; 504.38095 × 62.857143 | 504 / 62 | 8.129032 | Strip / Strip | -38 / -98 / +5.566532 | False / False | s-20261007-235716-emulator-5560/0025-collect-pixel35-3x1 |
| Pixel / 35 | 4x1 | portrait | 360 | 62 | 676 | 108 | 360.38095 × 108.95238; 360.38095 × 104.0; 676.5714 × 62.857143; 676.5714 × 62.857143 | 676 / 62 | 10.903226 | Strip / Strip | -38 / -98 / +8.340726 | False / False | s-20261007-235716-emulator-5560/0028-collect-pixel35-4x1 |
| Pixel / 35 | 2x2 | portrait | 172 | 135 | 332 | 233 | 172.19048 × 233.90475; 172.19048 × 224.0; 332.1905 × 135.61905; 332.1905 × 135.61905 | 332 / 135 | 2.459259 | Square / Square | +35 / -25 / -0.103241 | False / False | s-20261007-235716-emulator-5560/0018-collect-pixel35-2x2 |
| Pixel / 35 | 3x2 | portrait | 266 | 135 | 504 | 233 | 266.2857 × 233.90475; 266.2857 × 224.0; 504.38095 × 135.61905; 504.38095 × 135.61905 | 504 / 135 | 3.733333 | Card / Card | +35 / -25 / +1.170833 | False / False | s-20261007-235716-emulator-5560/0034-collect-pixel35-3x2 |
| Pixel / 35 | 4x2 | portrait | 360 | 135 | 676 | 233 | 360.38095 × 233.90475; 360.38095 × 224.0; 676.5714 × 135.61905; 676.5714 × 135.61905 | 676 / 135 | 5.007407 | Card / Card | +35 / -25 / +2.444907 | False / False | s-20261007-235716-emulator-5560/0031-collect-pixel35-4x2 |
| Pixel / 35 | 3x3 | portrait | 266 | 208 | 504 | 358 | 266.2857 × 358.85715; 266.2857 × 344.0; 504.38095 × 208.38095; 504.38095 × 208.38095 | 504 / 208 | 2.423077 | Square / Square | +108 / +48 / -0.139423 | False / False | s-20261007-235716-emulator-5560/0037-collect-pixel35-3x3 |
| Pixel / 35 | 4x3 | portrait | 360 | 208 | 676 | 358 | 360.38095 × 358.85715; 360.38095 × 344.0; 676.5714 × 208.38095; 676.5714 × 208.38095 | 676 / 208 | 3.250000 | Large / Large | +108 / +48 / +0.687500 | False / False | s-20261007-235716-emulator-5560/0040-collect-pixel35-4x3 |
| Pixel / 35 | 4x4 | portrait | 360 | 281 | 676 | 483 | 360.38095 × 483.8095; 360.38095 × 464.0; 676.5714 × 281.14285; 676.5714 × 281.14285 | 676 / 281 | 2.405694 | Square / Square | +181 / +121 / -0.156806 | False / False | s-20261007-235716-emulator-5560/0052-collect-pixel35-4x4 |

[raw-inputs.csv](raw-inputs.csv) contains **222 candidate-input rows**: minPair, maxPair, portraitLike, landscapeLike and every API31+ SizeF entry for the 42 observations. It includes provisional and chosen classifications, intended class, ratio and boundary margins. No synthetic anchor is presented as a measured host size.

All **30 distinct host/cell combinations** satisfy the intended model: one row→Strip; 3×2/4×2/5×2→Card; 2×2/3×3/4×4→Square; 4×3/5×3→Large. Owner clarification on 2026-10-08: **4×4 is Square, shape priority**.

The limiting Square is moto 2×2: 325/132 = 2.462121. The limiting wide cell is API30 4×3: 514/193 = 2.663212. For ≥0.1 ratio margin the allowed threshold interval is [2.562121, 2.563212]; 2.5625 leaves **0.100379 / 0.100712**. This interval is narrow: adding hosts requires recalibration, not a claim of universal coverage. The best symmetric margins for minPair / maxPair / portraitLike are only 0.05972 / 0.08322 / 0.05457. The landscape-like pair meets the requested margin and R1 orientation stability.

Strip maximum h=62 leaves 38dp; minimum non-Strip h=123 leaves 23dp. Card maximum h=135 is 25dp below Large's 160; minimum Large h=193 is 33dp above it. Square takes precedence over Large height, including 4×4. Moto 4×2 is globally Card from 667×132dp, while its actual portrait entry is fitted to the reported 363.42856×260.57144dp. No host-specific exception was needed.

## Size-map construction and performance

Worst-case ephemeral design: gradient, UTC Info, seconds ON, forced 12-hour AM/PM suffix, strong shadow, requested bundled Poppins through the **unchanged production resolver / host capability**. All three environments reported bundled-font support false, so Preview/fit/widget used effective system sans. No widget design/settings were saved by the probe. Production remains Strip/Card; Square/Large are diagnostic labels, not finished layouts.

A uses reported SizeF keys only. B adds missing diagnostic-class anchors (Strip 240×80, Card 300×140, Square 180×180, Large 400×240dp), deduplicated and capped at 16 entries. Both reported entries share the R1 widget class; anchors resolve their own size. A/B anchors and live labels used the original provisional classifier to compare selection, distinct from the calibrated rule above. Each entry resolves, fits its own key, composes, locally applies to count allocated background bitmap bytes, and parcels. Current production dp→px truncation and background bitmap cap are reused; fractional keys can differ by a pixel from rounded physical bounds.

Timing uses nanoTime, nearest-rank p95. Dedicated-worker samples, n=30 updates per accepted configuration, at 4×4; each accepted update was sent to the real host. The first sample is included, **not a controlled cold-cache test**. B on API35 stops after the first rejected update; no p95 is inferred from that one sample. Assembly elapsed includes diagnostic local apply/bitmap inspection/entry parcels and excludes final map parcel/send IPC. Reported Parcel.dataSize is flattened parcel size, not the ashmem bitmap allocation.

| Host / mode | Updates / entries per update | Bitmap bytes | Map Parcel bytes | p95 entry compose ms | p95 entry fit+compose ms | p95 update compose ms | p95 update fit+compose ms | p95 instrumented assembly ms |
|---|---|---|---|---|---|---|---|---|
| 34 / A | 30 / 2 | 4689984 | 5148 | 12.822 | 180.178 | 25.562 | 388.834 | 539.283 |
| 34 / B | 30 / 5 | 6615684 | 10256 | 11.654 | 144.271 | 45.242 | 645.673 | 921.118 |
| 35 / A | 30 / 3 | 14203708 | 7460 | 18.641 | 122.222 | 84.762 | 386.077 | 534.806 |
| 35 / B | 1 / 6 | 18534224 | 12656 | N/A (n=1) | N/A (n=1) | N/A (n=1) | N/A (n=1) | N/A (n=1) |

Machine-readable figures, first-sample values and mark-scoped source references: [performance.json](performance.json).
Individual measured key/class/timing/bitmap dimensions/bytes/Parcel rows: [per-entry.csv](per-entry.csv) (306); total-update samples: [per-update.csv](per-update.csv) (91).
Complete requested-cell reachability, including Not reachable/Not exercised rows: [reachability.csv](reachability.csv) (72). Moto A/B bitmap usage is approximately 4.47 / 6.31MiB against **6,912,000 bytes** (6.59MiB). B leaves only 296,316 bytes headroom. API35 A uses 14,203,708 bytes against **15,552,000**; B uses **18,534,224**, and the host rejects it with `IllegalArgumentException` (bitmap usage above limit). The diagnostic catches this expected rejection and keeps the previous accepted map. Parcel sizes are small; no TransactionTooLargeException was observed in the final scoped map trials.

Compose-only p95 meets 100ms/entry, but **generation including fit does not** (moto A 180.178ms/entry; API35 A 122.222). A fit+compose p95/update is 388.834 / 386.077ms, near the provisional 400ms goal; B moto is 645.673ms. Keep 100ms/entry as an optimization target and 400ms/update as the production generation target, not as a completed shipping guarantee. goAsync is required, with production-equivalent end-to-end timing and first-update latency verification in 3A-2. Diagnostic assembly p95 exceeds 400ms because it performs extra measurements; send/IPC cost has not been timed. No revised looser budget is justified by these data.

## Live resize: logs versus screen observation

The original “callback only after drop” assumption is false on Motorola. Owner explicitly authorized amending this premise and continuing on 2026-10-08. Callback delivery and host entry selection are independent questions. A debug freeze flag suppresses regeneration/sending while still logging callbacks, to isolate host selection; it does **not** claim callbacks were absent.

| Trial | Log observation | Screen observation / implication | Local evidence |
|---|---|---|---|
| moto A ordinary held drag | DOWN 1791388552.281; MOVE 1791388553.366; options callback 1791388553.416; map regenerated 1791388554.102; BEFORE_UP 1791388583.457, AFTER_UP 1791388583.527 | A268×260→A268×398 before drop, but app regeneration occurred; this alone cannot prove host-only selection | s-20261008-004632-ZY22GSDPFW / 0025-inspect, 0026-logs |
| API35 A, freeze, 4×4→4×3 | callback during held drag, updateSuppressed; no entry compose/send after the callback | label A360×483→A360×464 while held, selecting an existing reported entry independently of map regeneration | s-20261008-010141-emulator-5560 / 0016-inspect, 0017-logs |
| API35 B, freeze, 3×2→3×1 | callback during held drag, updateSuppressed | Center Stack→INLINE row, Info hidden. Strip anchor selection is inferred from the changed layout and fitting key; Strip hides the exact-key label | same session / 0024-inspect, 0025-inspect, 0026-logs |
| moto B, freeze, 3×2→3×3 | callback, updateSuppressed; no regeneration | B268×260 stays B268×260 despite Square 180×180 anchor; anchors do not guarantee ratio-class switching | s-20261008-012219-ZY22GSDPFW / 0012-inspect, 0013-inspect, 0014-logs |

A supports observed host-only entry switching on API35 and normal callback-driven resize on moto. Synthetic anchors do not implement Square's ratio region. API31+ map rotation selection is **Not exercised** because these homes did not rotate. API30 portrait/landscape rendering and stable classification were exercised across all 12 cells. Do not advertise cross-class styling during a drag before a new options callback as guaranteed behavior.

## Templates, fit and Preview parity

[template-audit.csv](template-audit.csv) records **300 local template/size/orientation cases** (5 existing templates × 30 host/cells × 2 orientation bounds), using production fit/composer/resolver and widest Time/Date/AM/PM/Info strings. **280 applied cases** had no natural-size overflow, TextView rectangle clipping or overlap. **20 failed to apply**: every SPLIT+Strip input (6 moto, 6 API35, 8 API30). `clocky_digital_widget_split_row.xml` contains a plain `android.view.View` spacer (line 43), which RemoteViews refuses to inflate. This source is unchanged; fix and regression-test in 3A-2 before lowering release minResize. The failure is not addressed by changing thresholds.

The debug Preview activity applies the same composed RemoteViews entries at key size, using the same production environment/resolver/fit as the sent map. Its per-entry labels/layouts and placed representative A/B entries were compared; normal placed cell screenshots and local widest-string audits cover the size matrix. This establishes shared rendering/fallback paths and the stated geometry checks, **not pixel-perfect host-surface parity for every possible design**. No Square/Large Studio UI or final four-class templates were built. Final 3A layouts still need Preview/widget parity verification. Glyph/shadow-ink bounds, alternate locales/font scale and every possible offset were not exhaustively audited.

## Ticking and existing-widget regression

All sessions used cdev prepare/finish, mark-scoped logs and collect, in-place install, proc kill, widget --ticking --no-process, inspect --diff. No raw adb, uninstall, pm clear, logcat -c, force-stop or manual PID kill was used. Seconds were OFF only for stable host-only/ticking screenshots; performance and template stress cases used seconds ON.

| Host | Host-side minute ticking with process absent | In-place regression / final restore |
|---|---|---|
| moto API34 | 12:51→12:52; 48.6s; 7/7 samples process absent; A map | Phase 2 baseline/normal spike at 4×2: s-20261007-234013-ZY22GSDPFW / 0002-collect-baseline, 0010-collect-restored. Latest restore: s-20261008-012219-ZY22GSDPFW / 0017-collect-restored, settings byte-identical, ID23 preserved |
| Pixel API35 | 4:09→4:10; 46.6s; 7/7 process absent; A map | Main APK Phase 2 baseline s-20261007-235716-emulator-5560 / 0012-collect-phase2-baseline; in-place spike 0013 collect; final direct baseline comparison s-20261008-012114-emulator-5560 / 0005-collect-restored: settings byte-identical, ID2, page0 cell(0,1) span4×2 restored |
| Pixel API30 | 3:45→3:46; 55.2s; 9/9 process absent; ordinary pair | s-20261008-001636-emulator-5562 / 0003-collect-baseline, 0005 collect-after install, 0085-collect-restored: settings byte-identical, IDs5/7; ID7 page0 cell(1,1) span4×2 restored, ID5 untouched |

Moto ticking evidence: s-20261008-004632-ZY22GSDPFW / 0013-widget-ticking. API35: s-20261008-010141-emulator-5560 / 0013-widget-ticking. API30: s-20261008-001636-emulator-5562 / 0083-widget-ticking. All sessions finished with reversible device settings restored. The owner manually removed moto's Google widget/icons to make room; this was confirmed as owner action and not reversed. Latest moto baseline/home geometry was 4×3 cell(0,0), restored to that state; this is distinct from the earlier 4×2 Phase 2 regression baseline.

## Errors and verification limits

- A pre-existing API35 alarm startup crash was reproduced with the main APK: AlarmInitReceiver / AlarmStateManager PendingIntent.getService missing mutability flags. It is an AOSP-domain issue and was not changed. Do not claim the entire session was crash-free.
- An early diagnostic audit crashed on SPLIT inflation; the audit now catches per-case apply failures and reports them, and all 300 cases were rerun. The underlying production template failure remains.
- An early UI-thread diagnostic prototype caused API35 input-dispatch ANR. After moving probe work to a HandlerThread, the final worker A/B scoped trials showed no crash/ANR; expected B bitmap rejection was caught. Older log buffers are preserved, not cleared.
- Full existing debug/release unit suites and release Kotlin compilation passed after diagnostic metadata assertions were made variant-aware (`build-final.log`); debug formatter/classifier/key-limit/map-total tests passed, with subsequent measured-cell/boundary cases (7 spike tests). cdev's existing 160 unit tests passed. Diagnostic source and tests are not in this docs PR.

Raw logs/screens/UI dumps stay in ignored `build/device/sessions/` in the spike worktree. Only safe derived numerical tables and cdev summary.md files are publishable; personal/home screenshots and settings XML are not committed. Local evidence references above identify the retained files. The docs PR includes only this record, numerical tables and design/backlog amendments.

## Phase 3A gates

Proceed with 3A-1 model/resolver and then 3A-2 render path using calibrated inputs, keying A and asynchronous generation. Keep release minResize at 250/40dp until SPLIT Strip applies and the full template regression passes; preserve default placement minWidth 250dp / 4×2. Verify production-generation latency including first update and send/IPC, API31+ rotation on an enabled host, and final four-class Preview parity in 3A-2. Additional launchers require measured rule/budget validation. No provider identity, schema or AOSP-domain change is needed by this spike. The callback premise amendment and 4×4 classification were owner-approved; there is no unresolved single-rule or A/B stop condition.

Safe cdev restore summaries: [moto](moto-restored-summary.md), [API35](pixel35-restored-summary.md), [API30](pixel30-restored-summary.md).
