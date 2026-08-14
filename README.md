# Clocky

**Google Clock の使い慣れた時計体験を基準にしつつ、ウィジェットのタイポグラフィとレイアウトを大幅に拡張する Android 時計アプリ。**

> Status: **MVP built / AOSP DeskClock foundation & Google Clock parity planning**

## Product direction

Clocky は今後、次の2層で作ります。

1. **Open functional base** — AOSP DeskClock の Alarm / Timer / Stopwatch / World Clock などを、スタンドアロン Gradle アプリ向けに適応。
2. **Clocky experience layer** — Google Clock を操作・表示の参照仕様としてパリティを取り、その上に Clocky 独自のウィジェット編集機能を追加。

Google Clock の APK は挙動・寸法・UI/UXの参照にのみ使い、プロプライエタリなコード・画像・フォント・署名資産は Clocky にコピーしません。

## Current MVP

現在の MVP では、ホーム画面のデジタル時計 Widget と基本的なカスタマイズ基盤まで到達しています。

- classic `RemoteViews` / `AppWidgetProvider`
- 4×2 baseline
- compact / 4×1 profile foundation
- time + date
- date show / hide
- independent time/date size
- font-family choices
- colour/background choices
- alignment
- per-widget settings
- signed release build pipeline

実機での Google Clock 完全パリティはまだ未完了です。

## Google Clock parity target

最終的には、通常利用で Google Clock にある主要機能を取りこぼさないことを目標にします。

- Alarm
- Timer
- Stopwatch + laps
- World clock / cities
- Settings
- Digital widget
- Digital Stacked widget
- Digital Cities widget
- Analog widget
- Stopwatch widget
- resize / 12h-24h / next-alarm / date / theme behaviour

詳細は [`docs/parity/GOOGLE_CLOCK_PARITY.md`](./docs/parity/GOOGLE_CLOCK_PARITY.md)。

## Clocky extensions

Google-like default preset を出発点に、次を変更可能にします。

### Time

- font family / variant
- size
- **weight 100–900**
- letter spacing
- colour / opacity
- X/Y offset
- Left / Center / Right
- 12h / 24h
- leading zero

### Date

- show / hide
- same / independent font
- independent size
- **independent weight 100–900**
- letter spacing
- colour / opacity
- date format
- X/Y offset
- distance from time

### Layout / Background

- transparent / filled
- background opacity
- corner radius
- padding
- global alignment / vertical position
- size-specific overrides
- editable presets

Weight の詳細仕様は [`docs/spec/FONT_WEIGHT.md`](./docs/spec/FONT_WEIGHT.md)。

## AOSP DeskClock baseline

最初の移植ベースは AOSP DeskClock のタグ付きスナップショットを固定して使用します。

```text
Tag: android-17.0.0_r1
DeskClock commit: 1f6ebf36d0c14f5e16265d80022cb6068d97cebd
```

AOSP DeskClock は Android platform / Soong 向けのため、そのまま `app/` にコピーするのではなく、依存関係を分類して standalone Gradle 用に適応します。

- [`docs/architecture/AOSP_DESKCLOCK_ADOPTION.md`](./docs/architecture/AOSP_DESKCLOCK_ADOPTION.md)
- [`docs/architecture/AOSP_PORT_INVENTORY.md`](./docs/architecture/AOSP_PORT_INVENTORY.md)
- [`THIRD_PARTY_AND_REFERENCE_POLICY.md`](./THIRD_PARTY_AND_REFERENCE_POLICY.md)

## Size strategy

Google Clock の reference behaviour を基準にしますが、固定ピクセルではなく AppWidget Host から渡されるサイズを優先します。

既存の参考端末実測値:

| 項目 | 値 |
|---|---:|
| Display | 720 × 1600 px |
| Density | 280 dpi / 1.75 px per dp |
| Logical display | 約 411 × 914 dp |
| Launcher | Motorola Launcher |
| Reference Widget | Google 時計 Digital |
| Current span | 4 × 2 |
| Measured 4×2 HostView | 692 × 484 px |
| Measured 4×2 HostView | 約 395.4 × 276.6 dp |

## Development order

1. AOSP snapshot / license / dependency inventory
2. Expand current MVP source into normal reviewable source files
3. Adapt AOSP core domains to standalone Gradle
4. Google Clock screen + widget parity specs
5. Functional parity implementation
6. Clocky extensions: weight / font / size / spacing / X/Y / colour / background
7. presets + advanced editor
8. real-device side-by-side verification

See [`docs/IMPLEMENTATION_BACKLOG.md`](./docs/IMPLEMENTATION_BACKLOG.md).

## Non-goals

- SystemUI / Android lock-screen clock replacement
- root-required behaviour
- pretending to be signed by Google or a platform key
- redistribution of Google Clock APK/assets
- copying proprietary Google/Apple fonts solely to imitate appearance

## Legacy planning docs

- [`MVP_計画書.md`](./MVP_計画書.md)
- [`実装予定表.md`](./実装予定表.md)
