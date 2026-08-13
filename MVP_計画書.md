# Clocky MVP 計画書

## 1. 目的

Clocky は、Android のホーム画面に置く時計ウィジェットを、**タイポグラフィ中心に細かくカスタマイズできるようにする**ことを目的とする。

Google 時計 Digital ウィジェットの「軽量・常時表示・ホーム画面に自然に馴染む」という良さを参考にしつつ、Clocky は独立実装として以下を強化する。

- 時計フォントの選択
- 時計サイズの細かな調整
- 日付サイズの独立調整
- 時計と日付の位置を独立調整
- 文字色・透明度・文字間隔の調整
- 背景の透明度・角丸・余白の調整
- プリセットから始めて細部を自由に崩せる編集方式

MVP ではまず **4×2** を完成させ、その直後に **4×1** を追加する。

---

## 2. MVP の完成条件

MVP は、以下をすべて満たした時点で完成とする。

### ウィジェット

- Android ホーム画面へ Clocky ウィジェットを追加できる
- 4×2 で安定して表示できる
- 4×1 でも専用レイアウトとして表示できる
- 時計表示が端末時刻に追従する
- 12時間 / 24時間表示を切り替えられる
- 設定変更が対象 AppWidget に即時反映される
- 複数個置いた場合、それぞれ別設定を保持できる
- 再起動後も設定と表示が維持される

### カスタマイズ

時計と日付を別オブジェクトとして扱い、最低限次を個別設定できる。

#### 時計

- フォントファミリー
- フォントバリエーション
- サイズ
- 太さ
- 文字間隔
- 文字色
- 透明度
- 横位置
- 縦位置
- 揃え位置
- 12h / 24h
- 先頭ゼロの有無

#### 日付

- 表示 / 非表示
- 時計と同じフォント / 別フォント
- フォントファミリー
- フォントバリエーション
- サイズ
- 太さ
- 文字間隔
- 文字色
- 透明度
- 横位置
- 縦位置
- 揃え位置
- 日付フォーマット
- 時計との間隔

#### 背景

- 完全透明
- 単色背景
- 背景透明度
- 角丸
- 内側余白

### プリセット

- 複数プリセットを用意できる
- プリセット選択後も全項目を変更できる
- プリセットは画像ではなく「設定値の集合」とする

---

## 3. MVP でやらないこと

以下は初期 MVP の対象外とする。

- Android 標準ロック画面時計そのものの置換
- root / SystemUI 改造
- ユーザーが任意の TTF / OTF を読み込む機能
- 独自の毎秒更新ループを使った秒表示
- 天気、予定、バッテリー等の情報カード
- 全 Launcher で完全に同じピクセル配置を保証すること
- Google / Apple のプロプライエタリなフォントや画像素材の無断同梱
- 2×1 / 2×2 等の小型サイズ最適化

これらは MVP 完成後に個別検討する。

---

## 4. 参考実機と既知の寸法

開発初期の基準端末では、現在利用中の Google 時計 Digital ウィジェットについて以下を確認済み。

| 項目 | 値 |
|---|---:|
| 画面 | 720 × 1600 px |
| Density | 280 dpi = 1.75 px/dp |
| 論理画面 | 約 411 × 914 dp |
| Launcher | Motorola Launcher |
| 基準ウィジェット | Google 時計 Digital |
| AppWidget ID | 11 |
| 現在 span | 4 × 2 |
| Launcher 最小 span | 2 × 1 |
| 4×2 HostView | 692 × 484 px |
| 4×2 HostView | 約 395.4 × 276.6 dp |
| 想定 4×1 HostView | 692 × 242 px |
| 想定 4×1 HostView | 約 395.4 × 138.3 dp |

注意：これらは**基準端末上の実測値**であり、Clocky の固定寸法にはしない。

実装では AppWidget Host から渡されるサイズ情報を優先する。

---

## 5. サイズ戦略

### 5.1 4×2

最初に完成させる基準サイズ。

用途：

- 大きな時刻表示
- 日付を上または下に配置
- 十分な余白を活かしたタイポグラフィ
- 背景付きデザイン

### 5.2 4×1

4×2 の基礎機能が完成した直後に対応する。

4×2 を単純に縦 50% に縮小するのではなく、**4×1 専用レイアウトプロファイル**を持つ。

例：

```text
4×2
      FRI, AUG 14
         02:49

4×1
FRI, AUG 14            02:49
```

### 5.3 設定値の共有とサイズ別 override

原則共通：

- フォント
- 色
- 12h / 24h
- 日付形式
- 背景色

必要に応じサイズ別：

- 時計サイズ
- 日付サイズ
- 時計 X/Y
- 日付 X/Y
- 時計と日付の並び方
- padding / margin

ユーザー向け UI では、通常は自動変換し、必要な場合のみ「このサイズだけ個別調整」を有効にできる形を目指す。

---

## 6. フォント設計

### 6.1 方針

大量の似たフォントをフラットに並べない。

トップレベルでは、一般ユーザーにも違いが分かりやすい見た目でまとめる。

初期カテゴリ案：

- Modern
- Rounded
- Serif
- Condensed
- Mono
- Display / Experimental

各カテゴリ内部に 2〜4 程度の意味のあるバリエーションを置く。

### 6.2 UI 表示

例：

```text
Modern
  Clean
  Light
  Wide

Rounded
  Soft
  Heavy

Serif
  Classic
  Editorial
```

### 6.3 フォントライセンス

APK に同梱するフォントは、**再配布可能なライセンスを持つものだけ**採用する。

Apple の純正フォント等を単に iPhone 風にする目的で同梱しない。

---

## 7. プリセット設計

プリセットは画像アセットではなく、設定パラメータの集合とする。

例：

```text
Preset: Editorial 01

timeFont       = Serif / Editorial
timeSize       = 126sp
timeWeight     = Regular
timeOffsetY    = +8dp

dateFont       = Modern / Clean
dateSize       = 15sp
dateWeight     = Medium
dateSpacing    = 0.12
dateOffsetY    = -72dp

alignment      = Center
background     = Transparent
```

選択後は、ユーザーが 1 項目ずつ変更できる。

初期プリセットカテゴリ候補：

- Clean
- Lock-screen inspired
- Minimal
- Editorial
- Retro
- Tech

---

## 8. 設定画面

設定画面は、**上部に実寸比プレビュー、下部に編集パネル**を置く。

イメージ：

```text
┌──────────────────────────────┐
│                              │
│        FRI, AUG 14           │
│          02:47               │
│                              │
└──────────────────────────────┘

 Time   Date   Layout   Style
──────────────────────────────
 FONT
 [ Modern / Clean ▾ ]

 SIZE
 132  ━━━━━━━━━●━━━━  180

 POSITION
 X  0dp
 Y +4dp

                 [ Apply ]
```

### 8.1 タブ構成候補

- Time
- Date
- Layout
- Style
- Preset

### 8.2 Advanced

通常ユーザーにはプリセットと主要設定を優先して見せる。

細かな X/Y offset、文字間隔、サイズ別 override 等は Advanced 側にまとめてもよい。

---

## 9. 技術アーキテクチャ

### 9.1 基本方針

- Kotlin
- XML ベース UI
- classic AppWidget / `RemoteViews`
- `AppWidgetProvider`
- 設定は `appWidgetId` 単位で保存
- Glance は MVP の主実装に使用しない

理由：Clocky はフォント・TextView 系パラメータを細かく扱うことが主目的であり、従来型 `RemoteViews` の方が制御しやすい。

### 9.2 想定構造

```text
app/
├─ widget/
│  ├─ ClockWidgetProvider
│  ├─ ClockWidgetRenderer
│  ├─ WidgetSizeProfile
│  └─ WidgetUpdateCoordinator
│
├─ settings/
│  ├─ WidgetSettings
│  ├─ WidgetSettingsRepository
│  └─ PresetRepository
│
├─ ui/
│  ├─ WidgetConfigActivity
│  ├─ preview/
│  └─ editor/
│
└─ res/
   ├─ layout/
   ├─ font/
   ├─ drawable/
   └─ xml/
```

### 9.3 設定モデル案

```text
WidgetSettings
├─ appWidgetId
├─ clock
│  ├─ fontFamily
│  ├─ fontVariant
│  ├─ textSize
│  ├─ weight
│  ├─ letterSpacing
│  ├─ color
│  ├─ alpha
│  ├─ alignment
│  ├─ offsetX
│  ├─ offsetY
│  ├─ hourMode
│  └─ leadingZero
│
├─ date
│  ├─ visible
│  ├─ fontFamily
│  ├─ fontVariant
│  ├─ textSize
│  ├─ weight
│  ├─ letterSpacing
│  ├─ color
│  ├─ alpha
│  ├─ alignment
│  ├─ offsetX
│  ├─ offsetY
│  └─ format
│
├─ background
│  ├─ color
│  ├─ alpha
│  ├─ cornerRadius
│  └─ padding
│
└─ sizeOverrides
   ├─ 4x2
   └─ 4x1
```

---

## 10. 時計更新戦略

Clocky は不要な定期フル更新を避ける。

更新が必要になる主なイベント：

- ウィジェット初回追加
- 設定変更
- AppWidget options / サイズ変更
- 日付変更
- タイムゾーン変更
- 時刻形式設定変更
- locale 変更
- 再起動後の復元

時刻そのものは、プラットフォーム側で時刻表示に適した View / formatting を利用できる場合はそれを優先する。

MVP では独自の毎秒 `AlarmManager` / Service 更新ループは作らない。

---

## 11. Google 時計解析から採用する考え方

基準として解析した Google 時計 Digital は、次の考え方が参考になる。

- AppWidgetProvider + RemoteViews
- AppWidget の定期更新に頼りすぎない
- 時刻表示とイベント駆動の更新を分離する
- Widget Host のサイズを考慮する
- 大きい Host 領域を必ずしも全面使用しない
- 日付と時計の視覚的階層を明確にする

ただし Clocky は独立実装とし、Google 時計のコード・画像・プロプライエタリな資産をコピーしない。

---

## 12. データ保存

MVP では `appWidgetId` ごとに設定を保持する。

要求：

- 複数ウィジェットを独立設定できる
- ウィジェット削除時に不要データを整理できる
- 再起動後も復元できる
- 将来設定項目を追加しても migration できる構造にする

保存手段は実装時に SharedPreferences / DataStore 等から選定する。

---

## 13. パフォーマンス要件

- ホーム画面表示中に常駐 Service を必須にしない
- 不要な毎分・毎秒のアプリプロセス起動を避ける
- RemoteViews 更新回数を抑える
- フォントや drawable の無駄な巨大化を避ける
- 設定画面のプレビューは滑らかに更新する
- 端末再起動後も自動復旧する

---

## 14. 初期テスト対象

最低限以下を確認する。

### 表示

- 4×2
- 4×1
- 透明背景
- 不透明背景
- 明るい壁紙
- 暗い壁紙
- 12h
- 24h
- 日本語日付
- 英語日付

### 状態変化

- 設定変更
- 日付跨ぎ
- タイムゾーン変更
- 端末時刻形式変更
- Launcher 再起動
- 端末再起動
- ウィジェット削除 / 再追加
- 複数 Clocky Widget 同時配置

### 端末 / Launcher

最初は基準端末を優先し、その後最低 1 種類以上の別 Launcher / 別 density でも確認する。

---

## 15. MVP 後の候補

優先度順ではなく候補一覧。

- 2×1 / 2×2
- ユーザー独自フォント import
- より高度な可変フォント axis
- 秒表示
- 縦書き / 特殊レイアウト
- 時計と日付以外のサブテキスト
- 天気
- 次の予定
- バッテリー情報
- Widget のエクスポート / インポート
- プリセット共有
- Material You 系自動色
- 対応 OS でのロック画面 Widget 参加可否調査

---

## 16. MVP 開発原則

1. **4×2 をまず完成させる。**
2. **4×1 をプリセット大量追加より先に入れる。**
3. **時計と日付を独立オブジェクトとして設計する。**
4. **似たフォントはまとめ、意味のある差だけを見せる。**
5. **プリセットは編集可能な設定値集合にする。**
6. **基準端末の実測値を参考にするが、固定値には依存しない。**
7. **見た目の自由度と電池消費の少なさを両立する。**
8. **プロプライエタリ資産をコピーせず独立実装する。**

---

## 17. 参照APKの保管と GitHub Actions 出力ガード

### 17.1 位置づけ

Google 時計 7.13 の APK は、Clocky の挙動・寸法・AppWidget 構造を確認するための**ローカル参照資料**として扱う。

Clocky のビルド依存物・ランタイム資産・配布物にはしない。Google 時計の APK 本体や、その中のコード・画像・フォント等を Clocky に取り込まない。

ローカルで参照する場合の推奨配置：

```text
reference_apk/
└─ google-clock-7.13/
   ├─ base.apk
   └─ split_config.xhdpi.apk
```

`reference_apk/` は Git 管理対象外とする。再現性のため、リポジトリにはバイナリそのものではなく、版情報・ファイルサイズ・SHA-256・解析結果を `docs/reference/` に記録する。

### 17.2 現在の参照APK識別情報

| ファイル | サイズ | SHA-256 |
|---|---:|---|
| `base.apk` | 14,906,517 bytes | `58fc4c98cfc80786d81a927fed6b87852b8367734f758523c8264be5a2ded21d` |
| `split_config.xhdpi.apk` | 77,330 bytes | `65ca09d76f4e7b5dc174eee36be60c1670ae5578352df55fd2570b12fbad9191` |

対象パッケージ：`com.google.android.deskclock`

対象バージョン：`7.13 (745094482)` / versionCode `76007130`

### 17.3 Actions / Release のルール

GitHub Actions から APK・AAB・ZIP 等を Artifact / Release として書き出す場合は、**Clocky のビルドで生成された出力だけを明示的にホワイトリストする**。

禁止事項：

- リポジトリ全体を対象に `**/*.apk` のような広い glob で Artifact を集める
- `reference_apk/` や参照資料ディレクトリを Artifact / Release に含める
- `com.google.android.deskclock` を applicationId とする APK を Clocky の成果物として公開する
- 上記 SHA-256 と一致する参照 APK を Actions の成果物へ含める

実装ルール：

1. Build workflow は Clocky の Gradle 出力ディレクトリだけを Artifact 候補にする。
2. `upload-artifact` / Release upload の**直前**に `scripts/verify-release-artifacts.sh` を実行する。
3. 参照 APK と同一 SHA-256 のファイルを検出した場合は即座に失敗する。
4. `apkanalyzer` が利用可能な場合、APK の applicationId を確認し `com.google.android.deskclock` なら失敗する。
5. ソースツリーに `.apk` / `.apks` / `.xapk` / `.aab` が誤って Git 追跡された場合も CI を失敗させる。
6. ガードが失敗した状態では Artifact / Release upload を実行しない。

このルールは **「参照用 APK は解析のためローカルに保持できるが、Clocky の成果物としては絶対に外へ出さない」** ことを機械的に保証するためのものとする。

### 17.4 実装ファイル

- `.gitignore` — 参照 APK とビルド済み Android package の誤コミット防止
- `.github/workflows/reference-apk-guard.yml` — Git 追跡された Android package バイナリを検出して CI failure
- `scripts/verify-release-artifacts.sh` — Actions の Artifact / Release upload 前に実行する成果物検査
- `docs/reference/google-clock-7.13.md` — 参照 APK の識別情報と解析メモ
