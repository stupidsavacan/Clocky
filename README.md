# Clocky

**Google Clock の使い慣れた時計体験を基準にしつつ、ウィジェットのタイポグラフィとレイアウトを大幅に拡張する Android 時計アプリ。**

> Status: **AOSP DeskClock direct-port prepared / 12 pre-build phases complete / compile not started**

## Product direction

Clocky は次の3層で構成します。

1. **Immutable upstream provenance** — AOSP DeskClock `android-17.0.0_r1` を `third_party/aosp-deskclock/` に固定保存。
2. **Standalone functional app** — AOSP Alarm / Timer / Stopwatch / World Clock / Digital & Analog Widget を `app/` に直接移植し、通常の Gradle Android アプリ向けに適応。
3. **Clocky experience layer** — Google Clock を UI/UX の参照仕様としてパリティを取り、その上に Clocky 独自の Widget 編集機能を追加。

Google Clock の APK は挙動・寸法・UI/UX の参照にのみ使い、プロプライエタリなコード・画像・フォント・署名資産は Clocky にコピーしません。

## Current source state

AOSP DeskClock の固定スナップショットは、原本と実作業ツリーを分離しています。

```text
third_party/aosp-deskclock/   # immutable AOSP provenance snapshot
app/                          # standalone Gradle adaptation + Clocky-owned code
legacy/mvp-app-skeleton/      # pre-port Clocky app skeleton snapshot
ci/Clocky_MVP_source.zip      # previous MVP source bundle retained for reference
```

最初の direct-port bootstrap では次を `app/` にミラー済みです。

| Area | AOSP files | Port files at bootstrap |
|---|---:|---:|
| source | 160 | 160 |
| resources | 505 | 505 |
| assets | 1 | 1 |

`app/` にはこの後 Clocky 固有コードが追加されるため、現在は AOSP 原本より大きくなります。

## Standalone Gradle adaptation

現在の移植設定：

```text
AOSP tag: android-17.0.0_r1
DeskClock commit: 1f6ebf36d0c14f5e16265d80022cb6068d97cebd
namespace: com.android.deskclock
applicationId: com.stupidsavacan.clocky
compileSdk: 35
minSdk: 23
targetSdk: 35
```

Direct-port 初期段階では AOSP の package/import を大量変更しないため `namespace = com.android.deskclock` を維持し、インストール識別子だけ `com.stupidsavacan.clocky` に分離しています。Provider authority は `${applicationId}` に変更済みです。

AOSP `Android.bp` の AndroidX / Material 依存は standalone Gradle 依存へマッピング済みです。

- [`docs/port/DEPENDENCY_MAP.md`](./docs/port/DEPENDENCY_MAP.md)
- [`docs/port/PLATFORM_API_SCAN.md`](./docs/port/PLATFORM_API_SCAN.md)
- [`docs/port/AOSP_MIRROR_STATUS.md`](./docs/port/AOSP_MIRROR_STATUS.md)

## Pre-build work completed

APK を生成せずに行う12工程は完了しています。

1. AOSP vendor PR を `main` へマージ
2. AOSP functional source を `app/` へ direct-port
3. Soong → standalone Gradle 設定
4. Manifest 移植・application identity 適応
5. resources / assets mirror
6. platform / hidden API 静的スキャン
7. AndroidX / Material dependency map
8. Clocky Widget settings model + per-widget persistence
9. Font Weight `100..900` resolver / render contract
10. Google Clock UI/UX parity contract
11. Digital / Stacked / Cities / Analog / Stopwatch Widget parity spec
12. 旧 Clocky MVP → AOSP/Clocky ownership integration design

詳細：[`docs/port/TWELVE_PHASE_LEDGER.md`](./docs/port/TWELVE_PHASE_LEDGER.md)

## Static platform scan

Pre-build の静的スキャンでは AOSP source 160 files を調査し、強い platform-internal import は検出されず、`@hide` 文字列が2箇所検出されています。これはコンパイラ/API lint の代替ではないため、次フェーズで実際の compile error を基準に再評価します。

## Clocky customization model

Clocky 固有設定は AOSP domain state と分離しています。

### Time

- font family / variant
- independent size
- **requested weight 100–900**
- letter spacing
- colour / opacity
- X/Y offset
- Left / Center / Right
- system / forced 12h / forced 24h
- leading zero

### Date

- show / hide
- same / independent font
- independent size
- **independent requested weight 100–900**
- letter spacing
- colour / opacity
- date format
- X/Y offset

### Layout / Background

- transparent / filled
- background opacity
- corner radius
- padding
- size-specific overrides
- Google-like baseline preset + editable Clocky settings

Settings are stored per `appWidgetId` as schema-versioned JSON in `SharedPreferences`.

Weight の保存値は renderer の能力と独立しています。例えば `563` を保存したまま、Variable Font なら exact `wght`、static family なら nearest available face へ解決できます。

- [`docs/spec/FONT_WEIGHT.md`](./docs/spec/FONT_WEIGHT.md)
- [`app/src/main/java/com/stupidsavacan/clocky/customization/`](./app/src/main/java/com/stupidsavacan/clocky/customization/)

## Google Clock parity target

通常利用で Google Clock にある主要機能を取りこぼさないことを目標にします。

### App

- Alarm
- Timer
- Stopwatch + laps
- World clock / cities
- Settings

### Widget

- Digital
- Digital Stacked
- Digital Cities
- Analog
- Stopwatch

- [`docs/parity/GOOGLE_CLOCK_PARITY.md`](./docs/parity/GOOGLE_CLOCK_PARITY.md)
- [`docs/parity/GOOGLE_CLOCK_UI_UX_SPEC.md`](./docs/parity/GOOGLE_CLOCK_UI_UX_SPEC.md)
- [`docs/parity/WIDGET_PARITY_SPEC.md`](./docs/parity/WIDGET_PARITY_SPEC.md)
- [`docs/parity/REFERENCE_CAPTURE_PROTOCOL.md`](./docs/parity/REFERENCE_CAPTURE_PROTOCOL.md)

## Existing MVP integration

旧 MVP の per-widget settings、4×2 / compact profile、time/date independent size、font、alignment、colour/background は捨てず、AOSP の domain/widget lifecycle の上に Clocky presentation layer として統合します。

AOSP が Alarm / Timer / Stopwatch / World Clock の状態を所有し、Clocky は typography/layout/preset を所有します。Widget が独自に timer/stopwatch state を持つことは禁止します。

詳細：[`docs/architecture/MVP_INTEGRATION.md`](./docs/architecture/MVP_INTEGRATION.md)

## Next gate

12工程は **pre-build completion** です。まだ次は実施していません。

```text
compile / dependency resolution
        ↓
compile error remediation
        ↓
manifest + targetSdk behavior fixes
        ↓
assembleDebug
        ↓
real-device functional test
        ↓
Google Clock side-by-side parity test
```

現時点では **APKを生成しておらず、compile成功もまだ主張しません**。

## Non-goals

- SystemUI / Android lock-screen clock replacement
- root-required behaviour
- Google / platform signing identity の模倣
- Google Clock APK/assets の再配布
- proprietary Google/Apple fonts のコピー

## License / provenance

- AOSP DeskClock: Apache License 2.0 upstream snapshot
- Google Clock: behavioral/UI reference only
- Clocky-owned code: project policyに従う

See [`THIRD_PARTY_AND_REFERENCE_POLICY.md`](./THIRD_PARTY_AND_REFERENCE_POLICY.md).
