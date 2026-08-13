# Clocky

**Android のホーム画面時計を、タイポグラフィ中心に細かくカスタマイズするためのウィジェットアプリ。**

Clocky は、通常の時計ウィジェットの軽さと安定性を保ちつつ、時刻と日付の見た目をかなり細かく調整できることを目標にしています。

> Status: **Planning / pre-MVP**

## 目標

最初の MVP は **4×2** を基準に完成させ、直後に **4×1** を追加します。

主な機能予定：

- Android ホーム画面 `AppWidget`
- Kotlin
- classic `RemoteViews` / `AppWidgetProvider`
- 透明背景を基本にした時計デザイン
- 時計と日付を独立して編集
- 時計サイズ / 日付サイズを別々に調整
- フォント、太さ、文字間隔、色、透明度、位置の調整
- 12h / 24h
- 日付フォーマット選択
- 背景色、透明度、角丸、余白
- Widget ごとの独立設定
- 編集可能なプリセット
- 4×2 / 4×1 のサイズ別レイアウト profile
- 不要な定期フル更新を避けた低負荷設計

## フォントの考え方

似たフォントを大量にフラット表示するのではなく、見た目の差が分かりやすい系統へまとめます。

初期カテゴリ案：

- Modern
- Rounded
- Serif
- Condensed
- Mono
- Display / Experimental

各カテゴリの中に少数の意味のあるバリエーションを置きます。

APK に同梱するフォントは再配布可能なライセンスを持つものだけを採用し、Apple 等のプロプライエタリな純正フォントを単に見た目を似せる目的で同梱しません。

## カスタマイズ

### Time

- font family / variant
- size
- weight
- letter spacing
- color / opacity
- X/Y offset
- Left / Center / Right
- 12h / 24h
- leading zero

### Date

- show / hide
- 時計と同じフォント / 別フォント
- font family / variant
- **独立した size**
- weight
- letter spacing
- color / opacity
- date format
- X/Y offset
- 時計との間隔

### Layout / Background

- transparent / filled
- background opacity
- corner radius
- padding
- global alignment
- global vertical position
- size-specific override

## サイズ戦略

### 4×2 — MVP の基準

まず 4×2 でカスタマイズ機能一式を完成・安定化させます。

### 4×1 — 直後に対応

4×2 の単純縮小にはせず、必要に応じて時計と日付を横並びへ変えるなど、4×1 専用 profile を持たせます。

プリセットやフォントを大量に増やす前に 4×1 対応を完了させる方針です。

## 基準実機での参考値

Google 時計 Digital ウィジェットを基準に実測した値です。

| 項目 | 値 |
|---|---:|
| Display | 720 × 1600 px |
| Density | 280 dpi / 1.75 px per dp |
| Logical display | 約 411 × 914 dp |
| Launcher | Motorola Launcher |
| Reference Widget | Google 時計 Digital |
| AppWidget ID | 11 |
| Current span | 4 × 2 |
| Minimum launcher span | 2 × 1 |
| Measured 4×2 HostView | 692 × 484 px |
| Measured 4×2 HostView | 約 395.4 × 276.6 dp |
| Expected 4×1 HostView | 約 395.4 × 138.3 dp |

これらは**参考端末上の実測値であり、Clocky の固定寸法ではありません**。実装では AppWidget Host から渡されるサイズ情報を優先します。

## 想定アーキテクチャ

```text
Clocky app
├─ WidgetConfigActivity
├─ WidgetSettingsRepository
│  └─ settings keyed by appWidgetId
├─ ClockWidgetProvider
├─ ClockWidgetRenderer
│  ├─ 4x2 profile
│  └─ 4x1 profile
├─ RemoteViews layouts
├─ redistributable font resources
└─ preset definitions
```

Google 時計 Digital の実機・APK解析で得た AppWidget 設計上の考え方は参考にしますが、Clocky は**独立実装**です。Google 時計のコード・画像・プロプライエタリ資産をコピーして作るものではありません。

## ドキュメント

- [`MVP_計画書.md`](./MVP_計画書.md) — MVP の範囲、設計、完成条件、技術方針
- [`実装予定表.md`](./実装予定表.md) — 実装順、各 Phase の作業項目と完了ゲート

## MVP ではやらないこと

- Android 標準ロック画面時計そのものの置換
- root / SystemUI 改造
- 任意 TTF / OTF のユーザー import
- 高コストな独自毎秒更新による秒表示
- 天気 / 予定 / バッテリー等の追加情報
- 全 Launcher での完全なピクセル一致
- Google / Apple のプロプライエタリ資産のコピー

## Lock screen

初期 Clocky は **ホーム画面ウィジェット**です。

通常のサードパーティ Android アプリからシステムのロック画面時計そのものを差し替えることは MVP の対象外です。将来、対象 OS / 端末がサードパーティのロック画面 Widget hosting を提供する場合は、別機能として調査します。

## License

TBD.
