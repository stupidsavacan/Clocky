# Clocky Widget End-State Specification

> 種別: **Clocky のウィジェット製品における長期 End-State（最終到達点）の正本。** 実装フェーズの詳細計画ではなく、最終的に何を作り、何を作らないかを固定する。
> 適用: 実装計画・Issue・PR は本仕様を参照する。ただし各リリースで本仕様を一括実装する必要はない。到達順序は §13 の段階的ロードマップに従う。
> ベースライン調査範囲: `stupidsavacan/Clocky` main（2026-08-24 push）の Clocky 独自コード全体、Clocky が手を入れた AOSP widget provider/layout/metadata、docs/spec・docs/parity・IMPLEMENTATION_BACKLOG・WEB_AGENT_HANDOFF・MVP_INTEGRATION・MVP_計画書、Issue #31、PR #1–#33、`ci/Clocky_MVP_source.zip`（旧MVPソース。メモリ上で展開して読んだ）。外部情報は Android Developers / 9to5Google / Android Authority 等（末尾の Sources を参照）。

## Context

Clocky は AOSP DeskClock を移植した基盤の上に、Digital widget のタイポグラフィ編集機能を一項目ずつ積み上げてきた。weight、size、letter spacing、X/Y、5つのフォント、hour mode、per-size override がすでに入っている。ただ、項目単位の積み上げを続けても「完成形」には届かない。現状の問題は次のとおり。

- 設定UIが縦に並んだスライダーの羅列で、しかも Issue #31 によると実機では Material `Slider` の inflate で起動時にクラッシュする。
- 4×1/4×2 の判定が高さだけで決まっている。そのため、実機で追加直後のサイズ（191dp）がどちらの想定にも合わない。
- color / alignment / background / date format は「意味論が未定義」のまま止まっている。
- preset は `presetId = "google-clock"` という文字列があるだけで、仕組みとしては存在しない。

この文書では、ウィジェット機能の到達点を一つに決め、未定義のまま止まっている契約もここで決める。

**規範性:** §3〜§14 が長期仕様としての規範部分。§2 は 2026-08-24 時点の実装スナップショットであり、実装が進めば古くなり得る。現在状態の正本は `README.md` と `docs/IMPLEMENTATION_BACKLOG.md` とする。

---

## 1. Executive conclusion

**Clocky は「書体で選ぶ時計ウィジェット」の専門アプリにする。KWGT のような汎用ウィジェットビルダーにはしない。**

- 中心にあるのは **1つの Design ドキュメントモデル** と **1つの描画パイプライン**。その上にウィジェットの系統を **5つ** 載せる: Digital / Stacked / Analog / World Clock / Stopwatch。
- 時刻の更新は **すべてホスト側に任せる**: TextClock・AnalogClock・Chronometer。アプリ側が毎分ウィジェットを更新する仕組みは最後まで作らない。電池を食わず、時刻が遅れないことを、デザインの自由度より上に置く。
- 体験は3層で構成する。
  1. **Design Library**: 約40のキュレーション済みデザイン。ムードとサイズで選ぶ。
  2. **Quick Tune**: デザインを選んだあと、色・書体・サイズ・構図をチップで変える。20秒で完成させられることが目標。
  3. **Studio**: キャンバスを直接操作し、要素ごとのプロパティシートと、サイズクラスごとの override で作り込む。
- 自由度の上限は **RemoteViews が「時刻を自走させたまま」表現できる範囲** で決める。グラデーション文字、グロー、アウトライン、任意の TTF は入れない。逆に背景はビットマップで描くので、グラデーション・枠線・角丸は自由にできる。Analog の文字盤と針は API 31 以降ならビットマップの Icon として自由に描ける。
- 天気・予定・バッテリーは入れない。時計の周辺情報として許すのは、**次のアラーム・第2タイムゾーン・自由テキスト** だけ。

---

## 2. Current-state assessment

### 確認済みの現状（コードを読んで確認したものだけ）

| 領域 | 現状 | 根拠 |
|---|---|---|
| 設定モデル | `WidgetSettings`（Time/Date/Background + 4×1/4×2 の nullable `ProfileOverride`）。schema v1 の JSON を `clocky_widget_settings` / `widget.<id>.settings` に保存 | `customization/model/WidgetSettings.kt`、`storage/WidgetSettingsStore.kt` |
| 描画済みの項目 | time/date の weight（API 28 以上は 100 刻みの9段階、それ未満は6段階）、size、letter spacing（-0.20〜0.50em）、date の表示/非表示、X/Y（API 31 以上の translation のみ。23〜30 は 0dp）、5フォント（weight 400 のときだけ）、hour mode | `DigitalWidgetWeightRenderer`、`DigitalWidgetOffsetRenderer`、`LegacyWidgetFontFamilyPolicy` |
| モデルにあるが描画されていない項目 | color/opacity、alignment、date format、background、leadingZero | IMPLEMENTATION_BACKLOG の F4 |
| レイアウト | AOSP の `digital_widget.xml` に TextClock が **28個**（weight 9 + legacy font 5、それぞれ time と date）。表示するのは1つだけ。AOSP の二分探索オートサイズ（`optimizeSizes`）と world-city リストもそのまま残っている | `res/layout/digital_widget.xml`、`DigitalAppWidgetProvider.kt` |
| サイズ判定 | 高さだけで判定する2値（≤94dp なら 4×1、それ以外は 4×2）。metadata は AOSP 汎用のまま（minResize 136×59dp、targetCell なし、reconfigurable なし） | `DigitalWidgetProfileResolver`、`res/xml/digital_appwidget.xml` |
| 設定UI | 1画面の縦スクロールに、Slider と Switch と RadioGroup が並ぶ。プレビューは静的な TextView。Save するまで反映されない | `DigitalWidgetConfigActivity.kt` |
| Analog | AOSP そのまま。カスタマイズなし、config なし | `AnalogAppWidgetProvider.kt` |
| Stacked / Cities / Stopwatch | 未実装。仕様だけある | `WIDGET_PARITY_SPEC.md` |
| preset | `presetId` という文字列があるだけ。実体はない | — |
| 既知の不具合 | config 画面のクラッシュ（AppCompat テーマ上の Material Slider）、4×1/4×2 の幾何が想定からずれている、AOSP の青と AOSP のアイコンが漏れている | Issue #31 |
| 旧MVP | 3種類の背景レイアウト（transparent/dark #E6111111/light #E6FFFFFF、padding 18dp、compact のとき 10dp）、色は `#AARRGGBB` を手入力、date format 4種類、X/Y は ±40dp、compact は maxHeight<180 のとき、metadata は minResize 250×70dp・targetCell 4×2・reconfigurable | `ci/Clocky_MVP_source.zip` |

### 強み（残すもの）

1. **要求値と実効値を分ける設計**（`FontWeightResolver`）。保存するのはユーザーが要求した値で、描画側が端末の能力に応じて解決する。この方針を、色・位置・効果・Analog まですべてに広げる。
2. **nullable override による継承**（`null = inherit`）。サイズ別設定の正しいモデルになっている。手書きのフィールド列挙をやめて汎用化すれば、そのまま使える。
3. **AOSP は domain state を持ち、Clocky は presentation を持つ**という境界。widget が独自に timer/stopwatch の状態を持つことは今後も禁止する。
4. **TextClock に時刻を任せる方針**。アプリ側の更新ループを作らない。
5. **推測で実装しない開発文化**。この文書は、その「推測を禁じた項目」に対する答えを出す役割も持つ。
6. API レベルごとに縮退させ、そのことを明示する設計（translation は 31 以上、textFontWeight は 28 以上）。

### 不足（完成形とのギャップ）

- **プロダクトとしての体験がない**: プリセット、ギャラリー、簡単作成、ライブプレビューのどれもない。
- **描画方式が拡張できない**: フォントを1つ増やすたびに、時刻と日付の両方へ TextClock を足すことになる。フォント×weight×要素の組み合わせが爆発する。
- **サイズの体系が2値しかない**: 2×2、3×1、5×2 以上、横長と正方形の区別が表現できない。
- **未定義の契約が5つ残っている**: 色とα、RTL alignment、date format の既定値、背景、X/Y 編集の範囲。§5.8 ですべて決める。
- 提供しているのは Digital だけで、それも AOSP の provider に寄生している。

---

## 3. Product principles（今後ブレてはいけない原則）

1. **Time is sacred.** 時刻は必ず正しく、遅れない。ホスト側で自走する View だけで表示する。毎分のアプリ更新・Bitmap による時刻描画・Service の常駐は禁止。
2. **Typography first.** Clocky の差別化は書体・ウェイト・字間・構図にある。情報量では差別化しない。
3. **Preset-first, never preset-locked.** どのデザインもその場で全項目を編集でき、編集した結果はいつでも保存・複製・共有できる。
4. **Semantic slots, not free layers.** 編集できるのは意味のある要素（Time/Date/Info/Background/Analog parts）だけ。任意のレイヤーや図形の追加は KWGT の領域であり、Clocky ではやらない。
5. **Requested ≠ Effective.** ユーザーが要求した値は保存したまま残し、端末やランチャーの制約で縮退させるのは描画時だけにする。縮退が起きたらエディタ上で開示する。
6. **Responsive by default.** 標準はリサイズしても破綻しない Fit 方式。px 単位の固定配置は上級者向けの選択肢として残す。
7. **WYSIWYG = 同じ RemoteViews。** エディタのプレビューは、実際にウィジェットへ送るものと同じ RemoteViews をローカルで `apply()` して描く。プレビューと実機で見た目がずれることを、構造的にあり得ないものにする。
8. **Defaults feel like Google Clock.** 既定のデザイン「Clocky Default」は、Google Clock のふるまいと寸法にパリティを取る。
9. **No asset theft.** フォントは再配布可能なもの（OFL など）だけを同梱する。Google や Apple の資産はコピーしない。
10. **Every feature must pay rent.** 新しい項目を足すたびに、Basic と Advanced のどちらに置くかを決める。Basic の項目数には上限を設ける（各要素につき5項目まで）。

---

## 4. Final widget families

ウィジェットピッカーには **5つ** のエントリを出す。どれも共通の Design モデルと Studio を使い、描画エンジンは3種類（Text / Analog / Stopwatch）。

| # | Family | 存在理由 | 既定サイズ / 最小 | エンジン | カスタマイズ範囲 |
|---|---|---|---|---|---|
| 1 | **Digital**（主力） | Clocky の顔。ほとんどのユーザーはこれしか置かない | 4×2 / 2×1 | Text | 全項目 |
| 2 | **Stacked** | 時と分を縦に積む構図は、2×2 の正方形で最も美しい時計になる。Google にもある。ピッカー上で「正方形の時計」として見つけやすくするため、別エントリとして残す | 2×2 / 2×2 | Text（template = Stacked） | 全項目。時と分に別の色やウェイトを付けられる |
| 3 | **Analog** | 文字盤の需要は根強い。API 31 以降で本格的なカスタマイズができるようになったので、Clocky の第2の柱になる | 2×2 / 1×1 | Analog | 文字盤・針・色・目盛・数字の書体・日付窓 |
| 4 | **World Clock**（Cities） | 旅行者や海外の家族がいる人には必須。AOSP の city domain をそのまま使える | 4×2 / 2×1 | Text（collection） | 書体・色・背景・行の構成（List / Grid / Dual） |
| 5 | **Stopwatch** | Google とのパリティ。Chronometer で自走でき、AOSP の stopwatch state を使える | 4×1 / 2×1 | Stopwatch | スタイル（書体・色・背景）だけ。構図は固定 |

**既存候補の扱い**
- Digital と Stacked は **同じエンジンの template 違い** にする。Digital の Studio でも Stacked template を選べるので、ピッカー上のエントリは「出発点」の違いでしかない。
- 「Dual time」（ローカル時刻と1都市）は World Clock の Dual レイアウトに統合する。Digital の Info 行でも第2タイムゾーンを出せる。
- **新しい系統は足さない。** Timer widget、Word/Fuzzy clock、Next-alarm 専用 widget は §10 と §11 で判断している。

**Style Kit（系統をまたいだ統一感）**: Library のデザインは「Kit」単位で作る。たとえば Kit "Editorial" は、Digital / Stacked / Analog / World Clock の4つを同じ配色と書体で揃えたもの。ホーム画面に複数置いたときに見た目が揃うことが、Clocky の価値になる。

---

## 5. Final customization model

### 5.0 構造

```
Design
├─ Style tokens（Kit 共通）: palette（Primary/Secondary/Accent/Surface）, fontPrimary, fontSecondary
├─ Layout: template + 要素の配置 + サイズクラスごとの override
├─ Elements: Time / Date / Info / Background / (Analog parts | Cities options | Stopwatch style)
└─ Behavior: tap actions, hour mode, seconds, theme mode
```
要素の色や書体は、既定では **トークンを参照する**（例: Time.color = `token:Primary`）。個別に上書きすることもできる。Quick Tune ではトークンを変えるだけで全体の印象が切り替わり、Studio では要素ごとに崩せる。この2段構成が、初心者と上級者の両方に対応する鍵になる。

凡例: **B** = Basic（Quick Tune か Studio の第一階層）、**A** = Advanced（Studio の「詳細」に折り畳む）、**✕** = 提供しない

### 5.1 Time

| 項目 | 層 | 値 / 仕様 |
|---|---|---|
| フォント | B | 同梱ライブラリから選ぶ（§5.4） |
| Weight | B | 100〜900 の要求値（既存契約を維持）。プリセットのアンカー + 数値入力 |
| Size | B | Fit モードでは「領域に対する割合 40〜100%」、Fixed モードでは sp |
| 12h/24h | B | System / 12h / 24h（既存） |
| 先頭ゼロ | A | 12h で `h` と `hh` を切り替える。24h は常に `HH`。旧MVPには保存値がないので、既定は off（`h`） |
| AM/PM | B（12h のときだけ表示） | Hidden / Small suffix（時刻の 25〜60%）/ Inline / Above（Stacked のとき）。`a` パターンの独立した TextClock で出す |
| 区切り文字 | A | `:` / `.` / ` ` / なし / `·`。点滅させない |
| 秒 | A | Off（既定）/ On。TextClock の `ss` で出すので、ランチャー側で毎秒再描画される。電池への注意を表示する |
| 時と分を個別にスタイル | A | 色と weight を時と分で分ける（例: 時は Bold、分は Light。分だけ Accent 色）。`HH` と `mm` を2つの TextClock に分けて実現する |
| Letter spacing | A | -0.20〜0.50em（既存） |
| Line height | ✕（Stacked のときだけ A） | Stacked の行間を -30〜+30% で指定。それ以外は1行なので不要 |
| 色 / 不透明度 | B | §5.5 |
| 大文字/小文字 | ✕ | 数字なので意味がない |
| Tabular figures | ✕（自動） | 同梱フォントは等幅数字を既定にした版を採用する。RemoteViews から font feature を設定できないため |

### 5.2 Date

| 項目 | 層 | 値 / 仕様 |
|---|---|---|
| 表示 | B | Show / Hide（サイズクラスごとに override できる。既存） |
| 書式 | B | **Locale Auto（既定）** / プリセット6種（`EEE, MMM d`、`M月d日(E)`、`yyyy.MM.dd`、`EEE d MMM`、`EEEE`、`MMMM d`）。どれも locale skeleton を経由する |
| 独自パターン | A | ICU パターンを自由入力。リアルタイムに検証し、例を表示する |
| 大文字変換 | B | As-is / UPPER / lower。XML の textAllCaps を使うレイアウト変種で実現する |
| フォント | B | 既定は `token:fontSecondary`。Time とリンクさせることもできる |
| Weight / Size / Letter spacing | B / B / A | Time と同じ体系 |
| 色 / 不透明度 | B | 既定は `token:Secondary` |
| 位置（Time に対して） | B | template が決める（Above / Below / Leading / Trailing） |
| Time との間隔 | A | -20〜+40dp |

### 5.3 Info line（補助行。0〜1本）

| ソース | 層 | 判定 |
|---|---|---|
| 次のアラーム | B | **既定 On**（Google とのパリティ）。アイコンの有無と、「24時間以内のときだけ表示」を選べる |
| 第2タイムゾーン | B | TextClock の `setTimeZone` で自走する。都市名ラベルを付けられる |
| 自由テキスト | A | 最大24文字（名前やモットーなど） |
| なし | B | — |
| 天気 / 予定 / バッテリー / 歩数 | ✕ | §10 |

### 5.4 Typography library

- 同梱するのは **再配布可能（OFL など）な可変フォント、またはマルチウェイトのフォント**。最終的に **24ファミリー** を **6カテゴリ** に分ける: Modern / Rounded / Serif / Condensed / Mono / Display。旧MVPのカテゴリ案をそのまま採用する。
- 1カテゴリあたり4つ程度にして、「似たものを並べない」原則を守る。各フォントには、カバーしている文字体系・数字の形（等幅か）・使える weight をメタデータとして持たせる。
- **CJK の日付**（例: `10月6日(月)`）はシステムのフォールバックに任せる。選んだフォントが CJK をカバーしていない場合は、エディタに「日付の和文部分はシステム書体で表示されます」と表示する。
- システムフォント（sans-serif 系5種）も「System」カテゴリとして残す。旧MVPとの互換のため。
- **ユーザーによる TTF/OTF の読み込みはやらない**（§10）。

### 5.5 Color / opacity / theme

- **色の参照** は次の3種類のどれか。
  - `Fixed(rgb)`
  - `Dynamic(role, tone)`: Material You のシステムパレット。API 31 以上で `@android:color/system_accent1_*` などを使う。31 未満では Kit が持つ代替色に置き換える。
  - `Auto`: 背景や壁紙に対して読める側を自動で選ぶ。
- **不透明度** は要素ごとに 0〜100% の独立した値で持つ。
- **テーマのモード**（デザインごとに選ぶ）: `Fixed`（1組のパレット）/ `Follow system`（Light と Dark の2組。Dark は自動生成し、手で上書きできる）/ `Material You`（Dynamic role で構成）。API 31 以上では `setColorInt(night/notNight)` と色リソースでランチャー側が切り替えるので、アプリの更新は要らない。
- **壁紙との連動**: 壁紙の画像そのものは読めない（API 33 以降は特権が必要）。そこで `WallpaperManager.getWallpaperColors` のヒントを使い、`Auto` の判定と「壁紙に合う色」の提案スウォッチを作る。Pixel 系ランチャーのローカル色抽出は Dynamic role 経由で自然に効くが、効くかどうかはランチャーに依存すると明記する。
- **Gradient**: 背景だけ A で提供する（2〜3ストップ、線形と放射、角度）。**文字のグラデーションは提供しない**（§10）。
- **コントラストチェック**: エディタで常時チェックする。大きな文字は 3:1、日付や Info は 4.5:1 を下回ったら警告し、「修正」チップで一発で直せるようにする。

### 5.6 Effects

| 効果 | 判定 | 理由 |
|---|---|---|
| Legibility shadow（Off / Soft / Strong。色は文字色に応じて黒か白を自動で選ぶ） | **A** | 柄の多い壁紙で読みやすくするのに実用的。XML の shadow 属性を持つレイアウト変種で実現できる |
| 影の色・角度・距離を自由に指定 | ✕ | shadow 系の setter は remotable ではない。変種が爆発する |
| Glow / Neon | ✕（Future: RemoteCompose） | 同上。ビットマップで描くと時刻の自走をやめることになる |
| 文字のアウトライン / ストローク | ✕ | 同上 |
| 背景の枠線（幅・色・不透明度） | **A** | 背景はビットマップで描くので簡単に実現できる |
| 背景のぼかし（blur）・すりガラス | ✕ | ウィジェットから背後をぼかす API がない |

### 5.7 Background / container

| 項目 | 層 | 仕様 |
|---|---|---|
| 種類 | B | None / Solid / Gradient(A) / Outline only(A) |
| 色 / 不透明度 | B | §5.5 のトークンを参照 |
| 角丸 | B | **Match system（既定。API 31 以上の `system_app_widget_background_radius`）** / 0〜48dp |
| 内側余白 | A | 0〜32dp。上下左右をリンクして一括指定でき、リンクを外して個別にも指定できる |
| 枠線 | A | 幅 0〜4dp、色、不透明度 |
| ウィジェット全体の不透明度 | ✕ | 背景・文字それぞれの不透明度で十分。全体を薄くすると読めなくなる |

**描画の2経路**
- Native: Solid / Dynamic。ImageView の tint と outline radius（API 31 以上。それ未満は角丸ドローアブルの変種）を使う。
- Rendered: Gradient / 枠線 / その他。Clocky がウィジェットの実寸でビットマップを生成する。生成し直すのは、サイズが変わったとき・テーマが変わったとき・設定が変わったときだけ。

### 5.8 Layout & position — backlog の未定義契約をここで決める

**Template**（Text エンジン用。7種類）

| Template | 構図 | 主な用途 |
|---|---|---|
| Center Stack | 日付が上、時刻が下、中央揃え | Clocky Default（Google 風） |
| Time First | 時刻が上、日付が下 | 一般的 |
| Inline | 時刻と日付を横に並べる（日付は時刻のベースラインに揃える） | Strip（4×1） |
| Split | 日付は左上、時刻は右下（RTL では反転） | Card、Editorial 系 |
| Stacked | 時の行と分の行を積み、日付を添える | Square |
| Corner | 時刻を大きく左下に置き、日付を上に置く | Large、ポスター風 |
| Minimal | 時刻だけ | どのサイズでも |

**配置の決定事項**
- **Alignment**: `START / CENTER / END` に名前を変える（RTL で意味が反転する）。旧MVPの 0/1/2 はそのまま対応する。LEFT/RIGHT のような絶対指定は提供しない。
- **X/Y**: template が決めたアンカーからの相対 dp で持つ。X はロケールの書字方向に追従し、RTL では反転する。範囲は ±(そのサイズクラスの幅または高さの 50%)。保存値は丸めず、描画時にクランプする。API 30 以下では今と同じく 0dp に縮退し、エディタで「この端末では位置調整が反映されません」と表示する。
- **Size mode**: `Fit`（既定。テキストの領域に収まるよう自動でサイズを決め、ユーザーは倍率を指定する）/ `Fixed`（A。sp で指定し、収まらないときだけ縮小する）。今の AOSP の二分探索は Fit の実装として流用する。
- **色とαの合成**: 編集UIでは RGB と不透明度を別々に扱う。保存するときは RGB の α を FF に正規化する。旧データやインポートしたデータが α を持っていたら `opacity × α` に畳み込む。これで backlog の未定義が解消する。
- **Date format の既定**: `formatPattern: String?` にする。`null` は Locale Auto（既存の provider がしている locale 由来の既定と同じ）。
- **旧MVPの背景**: transparent は None、dark は Solid #111111・90%、light は Solid #FFFFFF・90% に対応させる。padding は 18dp（compact のときは 10dp で、サイズクラスの override に入れる）。light のときの既定の文字色 #111111 も一緒に取り込む。→ **これで旧MVPのインポーターを書く条件が揃う。**

### 5.9 Behavior

| 項目 | 層 | 仕様 |
|---|---|---|
| タップ時の動作 | B（ゾーン単位） | ゾーンは Time / Date / Info の3つ（Analog では全体を1つ）。選択肢は Clocky を開く（既定）/ Alarms / Timer / Stopwatch / World Clock / カレンダーアプリ（Date の既定）/ 次のアラームを編集（Info の既定）/ このウィジェットを編集 / 何もしない。**A**: 任意のアプリを起動 |
| 長押し / ダブルタップ | ✕ | 長押しはランチャーが使う。ダブルタップは RemoteViews で扱えない |
| Theme mode | B | §5.5 |
| アニメーション | ✕（エディタ内のみ） | §10 |

### 5.10 Analog parts（API 31 以上でフル機能）

| 項目 | 層 | 仕様 |
|---|---|---|
| 文字盤のスタイル | B | Plain / Index（12本線）/ Index 60 / Arabic / Roman / Dots / Ring / None（針だけ） |
| 文字盤の形 | B | Circle / Squircle / Rounded square / None |
| 針のスタイル | B | Classic / Baton / Rounded / Needle / Sword |
| 色 | B | 文字盤・目盛・時針・分針・秒針・中心キャップを個別に指定。既定はトークンを参照 |
| 数字の書体と weight | A | 同梱ライブラリから選ぶ（ビットマップで描くので任意の書体が使える） |
| 秒針 | A | Off（既定）/ On（AnalogClock 自身が刻む。スイープ運針はしない） |
| 日付窓 / 文字盤上のテキスト | A | 日付窓は 3時側 または 6時側。ブランド名風の自由テキストは最大12文字 |
| 針の太さと長さ | A | ±30% |

実装: API 31 以上では `AnalogClock` の `setDial`、`setHourHand`、`setMinuteHand`、`setSecondHand` に Clocky が生成したビットマップの Icon を渡す。API 26〜30 では同梱した8種類のプリセット（XML ドローアブル）だけを使える。それ未満は AOSP の既定のまま。

### 5.11 World Clock options

- レイアウト: List（2〜6行）/ Grid（2×2）/ Dual（ローカル時刻と1都市を大きく並べる）
- 各行の要素: 都市名、時刻、日付のずれ（+1 / −1）、昼夜の小アイコン（A）、ローカル時刻の行の有無
- 都市のデータは AOSP の `CityModel` を唯一の情報源にする。ウィジェット側で都市を重複して保存しない（既存の契約）。
- コレクションは API 31 以上で `RemoteCollectionItems` を使う（Service 不要）。それ未満は既存の `DigitalAppWidgetCityService` を使う。

### 5.12 Stopwatch options

- スタイル（トークン・書体・背景）だけを変更できる。構図は Compact（時間と Start/Pause）と Expanded（ラップ数とリセット）の2種類で固定。
- 表示は `Chronometer` で自走させる。ボタンは broadcast で `StopwatchModel` を操作する。

---

## 6. Easy Creation

### 方式の比較と決定

| 方式 | 速さ | 仕上がり | Clocky らしさ | 判定 |
|---|---|---|---|---|
| A. 段階式ウィザード（プリセット→色→フォント→完成） | △ 毎回4画面 | ○ | △ | 一部だけ採用（ステップを1画面に圧縮する） |
| B. ムードから作る（Minimal/Bold/…） | ○ | ○ | ○ | 採用（Library のカテゴリとして） |
| C. 質問に答えると候補を生成 | × 文字を読んで考える必要がある | △ 当たり外れがある | △ | 不採用 |
| D. 既存デザインの Remix | ◎ | ◎ 出発点がプロの品質 | ◎ | **主軸に採用** |

**決定: 「Gallery → Quick Tune」の2画面方式。** 中身は Remix を主軸にし、ムードはカテゴリとして使う。時計は見た目で選ぶものなので、文章で質問するより実物を並べた方が速い。

### フロー

```
[ウィジェット追加 or アプリ内「新しいウィジェット」]
        │
        ▼
① Gallery（1画面目）
   ┌───────────────────────────────┐
   │ ムード: [All][Minimal][Bold][Elegant][Retro][Mono][Playful][Editorial]
   │ ┌──────┐┌──────┐   ← 配置するサイズの実寸比で、
   │ │10:42 ││ 10   │      ライブの時刻を表示
   │ └──────┘│ 42   │
   │ ┌──────┐└──────┘
   │ ★お気に入り / My Designs のタブ
   │              [ このまま追加 ]  ← 既定の Clocky Default を選択済み
   └───────────────────────────────┘
        │ カードをタップ
        ▼
② Quick Tune（2画面目。上半分が大きなライブプレビュー、下にチップが4列）
   色      : (Kit 既定)(Material You)(壁紙に合う色 ×3)(Mono)(+ カスタム)
   書体    : (Modern)(Rounded)(Serif)(Condensed)(Mono)(Display) ← 各カテゴリの代表書体
   構図    : 対象サイズで使える template を3〜4個
   サイズ  : (S)(M)(L)  + 日付 On/Off トグル
   [🎲 Surprise me]  [詳細編集 →]           [ 完了 ]
```

- **操作量の目標**: 既定のままなら1タップ。好みのデザインなら2タップ。色と書体まで変えても5タップ・20秒以内。
- **Surprise me**: Kit が定義した「相性のよい組み合わせ表」の中だけでランダムに選ぶ（書体の組み合わせ×パレット×template）。全パラメータを無作為にすることはしない。
- **Quick Tune が変えるのはトークンだけ**: 色チップは palette トークン、書体チップは fontPrimary/fontSecondary を置き換える。だから Kit が持つ細部（字間や比率）を壊さない。
- 「詳細編集 →」を押すと、同じ状態のまま Studio に移る。
- **アプリ内から作る場合**: Library → Quick Tune →「ホームに追加」で `requestPinAppWidget`（API 26 以上）を呼び、作ったデザインのまま配置する。ランチャーのピッカーを経由しない、最短の経路になる。
- 配置したウィジェットを後から変えたい場合: API 31 以上では reconfigure、全バージョン共通で「タップ動作 → このウィジェットを編集」、それにアプリ内の「My Widgets」一覧から入れる。

---

## 7. Advanced Editor（Studio）

### 画面構成（スマホ縦向き）

```
┌─────────────────────────────────┐
│ ←  [↶][↷]   Strip|Card|Square|Large  [保存] │ ← 上部バー: Undo/Redo と、編集対象のサイズクラス
├─────────────────────────────────┤
│  背景: (壁紙色)(明)(暗)(市松)(写真を選ぶ)   │
│  ┌───────────────────────────┐  │
│  │      ┆ WED, OCT 6         │  │ ← 実寸比のキャンバス
│  │ ─ ─ ─┼─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │  │   中心線とスナップのガイド
│  │      ┆  10:42  ◉ 選択中    │  │   ランチャーの余白を薄く表示
│  └───────────────────────────┘  │
│  ⚠ API 30: 位置調整は反映されません    │ ← 縮退の開示
├─────────────────────────────────┤
│ [Time][Date][Info][Background][Layout][Behavior] │ ← 要素タブ（キャンバスのタップでも選べる）
├─────────────────────────────────┤
│ Text   Color   Position   Effects      │ ← プロパティのカテゴリ（横スワイプ）
│ Font     [Modern / Inter ▾]      •     │ ← ・ = プリセットから変更あり（タップでリセット）
│ Weight   ──────●──── 560 [⌨]           │
│ Size     ────●────── 72%               │
│ ▸ 詳細（letter spacing, 区切り, 秒…）   │ ← Advanced は折り畳む
└─────────────────────────────────┘
```

### 操作の決定事項

| 操作 | 採否 | 詳細 |
|---|---|---|
| キャンバス上で要素をタップして選択 | 採用 | 選んだ要素は枠とハンドルで示す |
| ドラッグで移動 | 採用 | 中心・端・ほかの要素のベースラインにスナップし、スナップ時にハプティクスを返す。押しながら2本指目を置くとスナップを解除 |
| 1dp 単位の微調整 | 採用 | Position タブの十字キー。長押しで連続移動 |
| ピンチで拡大縮小 | 採用 | 選んだ要素の Size を変える |
| 数値の直接入力 | 採用 | どのスライダーも値をタップすると入力欄になる。TalkBack でも操作できる |
| スライダー | 採用 | 主要な値にはアンカー目盛を付ける（weight は 100 刻み、など） |
| リンク編集 | 採用 | Time と Date の間で書体・色・alignment を🔗でつなげる。既定はつながっている |
| サイズクラス別の編集 | 採用 | 上部で編集対象のサイズクラスを選ぶ。既定は「全サイズ」に書き込む。「このサイズだけ」をオンにすると override に書き込み、該当項目に◆バッジを付け、長押しで「継承に戻す」 |
| リサイズの擬似体験 | 採用 | キャンバスの右下ハンドルで 2×1 から 5×4 まで伸縮できる。サイズクラスをまたぐと自動で切り替わる |
| Undo / Redo | 採用 | セッション内で50段 |
| 比較 | 採用 | プレビューを長押しすると、編集前（またはプリセットの元の状態）を表示する |
| 全体リセット / プリセットに戻す | 採用 | メニューに置く |
| 複数要素の同時選択 | 不採用 | 要素は最大4つなので、リンク編集で足りる |
| レイヤーの重なり順・グループ化 | 不採用 | 意味のあるスロットだけを扱う原則（§3-4） |

**変更は即座にプレビューへ反映し、ウィジェットには「保存」で反映する。** 未保存のまま戻ろうとしたら、確認を出す。

---

## 8. Design Library / Presets

| 論点 | 決定 |
|---|---|
| 数 | **Phase 1B で Digital 8個**（4 Kit × 2案）から開始。family 展開後は **16個以上**（最低 4 Kit × Digital/Stacked/Analog/World Clock）へ拡張し、完成形で **40〜48個**（8〜10 Kit + 単発デザイン）。100を超えるほど増やさない |
| カテゴリ | ムード7種: Minimal / Bold / Elegant / Retro / Mono / Playful / Editorial。それに加えて「Clocky Default」。フィルターは family と、合うサイズ（Strip/Card/Square/Large） |
| 系統間で共通化するか | する。Kit = Style tokens と各 family 用の Design をまとめたもの |
| 内蔵とユーザー作成の分け方 | 内蔵は assets に置く JSON で変更不可。各デザインに `builtinId@version` を持たせる。ユーザー作成は My Designs に置き、名前変更・複製・削除・お気に入りができる |
| プリセットを編集したとき | **スナップショット方式**。ウィジェットは Design の複製を持ち、出自として `origin = builtinId@version` を記録する。内蔵プリセットを更新しても、配置済みのウィジェットは変わらない。「My Designs に保存」でユーザーのデザインになる |
| リンク方式（デザインを編集すると全ウィジェットに反映される） | **不採用**。意図しない連鎖変更を防ぐため。代わりに「他のウィジェットにも適用…」という明示的な操作を用意する（対象を複数選べる） |
| Versioning | 必要。Design ドキュメントに `schema`、内蔵デザインに `version` を持たせる。ドキュメントのスキーマ移行はアプリが一方向に行う（v1 → v2 → …） |
| お気に入り | 採用（コストが低く、40以上の中から選ぶときに効く） |
| 検索 | 不採用（40個前後ならカテゴリとフィルターで足りる） |
| 複製 | 採用 |
| Import / Export | 採用。`.clocky`（JSON）ファイルと、「CLOCKY2:…」形式のテキストコード（クリップボードやメッセージで共有できる）。フォントは ID で参照する（同梱フォントだけなので外部ファイルは要らない）。インポート時にスキーマ検証とクランプを行い、知らないフォントは同じカテゴリの既定に置き換える |
| 共有 | Android の共有シート経由だけ（ファイルかテキストコード）。QR は Future |
| コミュニティギャラリー / サーバー | 不採用（§10） |
| バックアップ | 必須。My Designs とウィジェットの設定を Auto Backup の対象にする。`onRestored(oldIds, newIds)` で appWidgetId を付け替える |

---

## 9. Responsive / size architecture

### サイズクラス（4×1 / 4×2 の2値を置き換える）

| Class | 判定（host から渡される dp。幅 w と高さ h の両方で判定） | 代表的なセル | 推奨 template |
|---|---|---|---|
| **Strip** | h < 100 | 4×1, 3×1, 5×1, 2×1 | Inline / Minimal |
| **Card** | 100 ≤ h < 200 かつ w ≥ 1.4h | 4×2, 5×2, 3×2 | Center Stack / Split |
| **Square** | w < 1.4h かつ h ≥ 100 | 2×2, 3×3 | Stacked / Minimal / Analog |
| **Large** | h ≥ 200 かつ w ≥ 1.4h | 4×3, 5×3 以上 | Corner / Center Stack |

- しきい値は定数としてまとめ、実機で測ってから確定させる（Issue #31 で実測された 268×191dp は Card に入る）。
- **override のモデル**: 今の `ProfileOverride`（フィールドを手で列挙したもの）を、汎用の nullable patch に置き換える。キーはサイズクラス、中身は「プロパティのパス → 値」。override できるのは Layout 系のプロパティだけ（template、要素ごとの size・offset・visible・間隔、padding、alignment）。書体と色はサイズによらず共通にし、override の対象にしない（サイズによって印象が変わるのを防ぐ）。

### 描画
- **API 31 以上**: `RemoteViews(Map<SizeF, RemoteViews>)` で4クラス分をまとめて送る。ランチャーはリサイズ中もアプリを呼ばずに切り替えられる。
- **API 23〜30**: `onAppWidgetOptionsChanged` を受けて、縦向き用と横向き用のペアを作る（今の AOSP の方式）。
- **metadata**: `targetCellWidth/Height`（API 31 以上）、`minResize` は 2×1 相当、`widgetFeatures="reconfigurable"`。`configuration_optional` は付けない（初回の Gallery を必ず通すため。そこは1タップで抜けられる）。
- **ランチャーごとの差への対応**:
  1. `OPTION_APPWIDGET_SIZES`（API 31 以上）を最優先で使う。
  2. targetSdk 31 以上では既定の padding が付かないので、Clocky が内側の余白を自分で持つ（§5.7）。
  3. 角丸は Match system を既定にする。
  4. **キャリブレーション**（A）: 「このランチャーでは上下が切れる」と感じた場合に備えて、ウィジェットごとに外側の inset を ±8dp で調整できる。
  5. 次のランチャーでの検証を DoD に含める: Pixel Launcher / One UI Home / Moto / Nova / Lawnchair。
- **Generated previews**（API 35 以上）: ピッカーに Clocky Default を実際の RemoteViews で表示する。API 31〜34 は `previewLayout`、それ未満は画像。Kit の出来栄えをピッカーの時点で見せられる。

---

## 10. Features explicitly rejected

| 機能 | 理由 |
|---|---|
| 天気 | 位置情報の権限、外部 API キーやプロバイダ、ネットワーク、定期更新が必要になる。時計の主題からも外れ、At a Glance などに任せるのが最適。Clocky の「電池と権限が軽い」という価値を損なう |
| カレンダーの次の予定 | READ_CALENDAR 権限とプライバシーの説明が要り、予定が変わるたびの更新処理も必要。Google の At a Glance と重複する |
| バッテリー / 歩数 / システム情報 | 頻繁な更新が要る。KWGT の領域 |
| アプリ駆動の毎分更新（ビットマップ時計、Word clock など） | Doze 中は正確な毎分起床が保証されず、時刻が遅れる。原則1に違反 |
| 文字のグラデーション / グロー / アウトライン / 影の自由指定 | RemoteViews で remotable な setter がない。ビットマップで描くと時刻の自走をやめることになる → Future: API 36 以上の RemoteCompose / DrawInstructions がどこまで成熟するかを見て再評価する |
| ユーザー TTF の読み込み | TextClock は XML の `@font` しか受け取れず、実行時に読み込んだ Typeface は RemoteViews へ渡せない。ビットマップに逃げると原則1に違反する |
| 自由レイヤー / 図形 / 任意の要素の追加 | KWGT と同じ土俵に乗り、初心者向けの体験が崩れる。テストする組み合わせも無限になる |
| 点滅するコロン / 秒針のスイープ運針 / ウィジェット内のアニメーション | 毎秒の更新か、remotable なアニメーションが必要。電池を食い、ランチャーによって動作もまちまち |
| ウィジェット全体の不透明度 | 要素ごとの不透明度と重複し、可読性を壊すだけ |
| 背景のぼかし / すりガラス | ウィジェットから背後をぼかす API がない |
| 質問に答えてデザインを生成 | Gallery から選ぶより遅く、品質も安定しない |
| デザインのリンク方式（編集が全ウィジェットに連鎖） | 意図しない変更を生む。明示的な「他にも適用」で代替する |
| コミュニティギャラリー / クラウド同期 / アカウント | サーバーの運用、モデレーション、プライバシー対応のコストに見合わない。ファイルとテキストコードの共有で足りる |
| デザインの文字検索 | 50個前後の Library では不要 |
| 全パラメータの完全ランダム | 醜い結果が多い。Kit の範囲内に制約した Surprise me で代替する |
| ロック画面の時計の置き換え / SystemUI | root が必要か、プラットフォーム側の領域（README の Non-goals を維持） |
| Glance への移行 | Glance には TextClock に相当するものがなく、毎分の更新が必要になる。細かい制御も効かない。classic RemoteViews を維持する |
| 長押し・ダブルタップの割り当て | ランチャーが使う操作か、RemoteViews で扱えない操作 |

---

## 11. Final feature matrix

| 機能 | 分類 |
|---|---|
| Digital family / Clocky Default（Google パリティ） | Essential |
| 時刻の書体・weight・size・色・不透明度、12/24h | Essential |
| 日付の表示・書式（Locale Auto + プリセット）・書体・size・色 | Essential |
| 次のアラーム（Info） | Essential |
| サイズクラス（Strip/Card/Square/Large）+ responsive RemoteViews | Essential |
| Fit 方式のサイズ決定 | Essential |
| Gallery + Quick Tune（簡単作成） | Essential |
| ライブプレビュー（同じ RemoteViews を apply） | Essential |
| 内蔵 Library（Digital 8個から開始 → family 展開時16個以上 → 完成形40〜48個）と Kit | Essential |
| テーマのモード（Fixed / Follow system / Material You） | Essential |
| 背景（None/Solid、角丸の Match system、不透明度） | Essential |
| タップのゾーン（3つ）と既定の割り当て | Essential |
| アクセシビリティ（content description、コントラスト警告、TalkBack で操作できるエディタ） | Essential |
| RTL（START/END）、locale、font scale | Essential |
| バックアップと復元（appWidgetId の付け替え） | Essential |
| 旧MVPのインポーター（§5.8 の契約を前提にする） | Migration-only（既存開発版の設定保持が必要な場合に実施。End-State の製品価値には含めない） |
| Studio（タブ + シート + 数値入力 + リセットの表示） | Essential |
| Undo/Redo、比較 | Recommended |
| キャンバスでのドラッグ・スナップ・ガイド・ピンチ | Recommended |
| サイズクラスごとの override の UI | Recommended |
| Stacked family | Recommended |
| Analog family（API 31 以上でフル機能） | Recommended |
| World Clock family | Recommended |
| Stopwatch family | Recommended |
| 第2タイムゾーン（Info） | Recommended |
| AM/PM のスタイル、先頭ゼロ | Recommended |
| 同梱フォント24ファミリー（6カテゴリ） | Recommended |
| 壁紙に合う色の提案 / Auto contrast | Recommended |
| My Designs・複製・お気に入り | Recommended |
| Import / Export / テキストコード共有 | Recommended |
| ホームに追加（requestPinAppWidget） | Recommended |
| Generated previews（API 35 以上） | Recommended |
| Surprise me（Kit の範囲内） | Recommended |
| 「他のウィジェットにも適用」 | Recommended |
| 時と分の個別スタイル | Power-user |
| 秒表示（Digital と Analog） | Power-user |
| 独自の日付パターン | Power-user |
| Gradient / 枠線の背景 | Power-user |
| Legibility shadow | Power-user |
| 区切り文字、letter spacing、行間（Stacked） | Power-user |
| Fixed（sp）サイズ方式 | Power-user |
| 自由テキストの Info、任意アプリの起動 | Power-user |
| ランチャーのキャリブレーション inset | Power-user |
| Analog の数字書体・日付窓・針の比率 | Power-user |
| Timer widget | Future/optional |
| 写真の背景 | Future/optional |
| RemoteCompose による表現（グロー、文字グラデーション） | Future/optional |
| QR での共有 | Future/optional |
| タブレットのロック画面 / Hub への対応 | Future/optional |
| Word / Fuzzy clock | Future/optional（RemoteCompose が前提） |
| 天気・予定・バッテリー・ユーザーフォント・自由レイヤー・ウィジェット内アニメーション・コミュニティギャラリー・Glance・検索・完全ランダム・リンク方式 | Reject |

---

## 12. Target architecture

```
                 ┌──────────── Domain（AOSP の所有。変更しない）────────────┐
                 │ AlarmModel / CityModel / StopwatchModel / TimeModel        │
                 └──────────────────────────┬───────────────────────────────┘
                                            │ 読み取り専用 + broadcast
┌─────────────── Clocky Widget layer（Clocky の所有）──────────────────────────────────┐
│ model/                                                                           │
│   Design (schema v2): id, name, origin{builtinId,version}, family, kitId          │
│     styleTokens{palette roles, fontPrimary, fontSecondary}                        │
│     elements{time,date,info,background,analog?,cities?,stopwatch?}               │
│     layout{template, base, overrides: Map<SizeClass, Patch>}                     │
│     behavior{tapZones, hourMode, seconds, themeMode}                              │
│   ColorRef = Fixed(rgb) | Dynamic(role,tone) | Auto  + opacity                    │
│   WidgetInstance{appWidgetId, family, design(snapshot), calibration}              │
│ repo/                                                                            │
│   WidgetInstanceStore（今の SharedPreferences JSON を v2 に。v1 → v2 の移行）       │
│   DesignRepository（内蔵は assets/designs/*.json、ユーザー作成は files/designs/*.json）│
│   FontCatalog（ID、カテゴリ、weight、文字体系、res の対応）                        │
│   LegacyMvpImporter（clocky_widgets → v2。一方向で、成功したら旧キーを削除）       │
│ resolve/                                                                         │
│   SizeClassResolver(w,h dp) → SizeClass                                          │
│   DesignResolver(design, sizeClass, theme, SdkCapabilities) → ResolvedSpec        │
│     （トークンの解決、override の適用、要求値から実効値への変換、縮退の記録）       │
│   SdkCapabilities: translation≥31, fontWeight≥28, dynamicColor≥31,               │
│                    analogIcons≥31, responsive≥31, generatedPreview≥35            │
│ render/                                                                          │
│   RemoteViewsComposer: templateレイアウト + addView(slot, fontFragment) を合成      │
│     fontFragment = ビルド時にコード生成する「書体1つ × 9 weight × shadow 変種」の   │
│     TextClock レイアウト。要素ごとにスロットへ addView するので、組み合わせが爆発しない │
│   BackgroundRenderer: Native（tint/outline）| Rendered（Bitmap）                   │
│   AnalogRenderer: 文字盤と針の Bitmap を Icon にする（API 31 以上）/ XML 変種       │
│   CitiesRenderer / StopwatchRenderer                                             │
│   ClickWiring: tap zone → PendingIntent                                          │
│ providers/（Clocky が所有する AppWidgetProvider。Issue #31 の方針）                │
│   Digital, Stacked, Analog, WorldClock, Stopwatch → 共通の WidgetUpdateCoordinator │
│     （イベント: options/size, 日付, TZ, locale, 次のアラーム, city, theme, 設定保存）│
│ editor/                                                                          │
│   Gallery, QuickTune, Studio（Material 3 テーマで作り直す）                        │
│   EditorSession（Design のドラフト + Undo スタック + dirty 判定）                  │
│   PreviewHost: 同じ ResolvedSpec → 同じ RemoteViews → apply() + 編集オーバーレイ   │
│   ContrastChecker, DegradationNotices                                            │
└──────────────────────────────────────────────────────────────────────────────────┘
```

**主要な設計判断**
1. **フォントの断片を合成する**: 書体はスロットへの `addView` で差し替え、weight は断片の中で可視/不可視を切り替える。これで「書体の数×weight×要素」の組み合わせを「書体の数」個のレイアウトファイルに抑えられる。今の28 TextClock 方式から移行する。
2. **今ある純粋関数を活かす**: `FontWeightResolver`、`RemoteViewsFontWeightPolicy`、`WidgetLetterSpacingPolicy`、`DigitalWidgetFormatPolicy`、`DigitalWidgetOffsetRenderer` の縮退ロジックは、resolve/ と render/ の部品として残す。`ProfileOverride` と各 Editor オブジェクトは汎用の Patch へ置き換える。
3. **Digital の provider を Clocky 側へ移す**: AOSP の world-city list とオートサイザーを主経路から外す。World Clock family は別の provider にする（Issue #31 の3番の方針を採る）。リリース前なので、provider のクラス名が変わることによる移行コストはない。
4. **テストの境界**: resolve/ は純粋な Kotlin なので unit test で網羅する。render/ は Robolectric で RemoteViews を apply したスナップショットを比べる。providers/ は実機の matrix で確認する。

---

## 13. Roadmap（End-State を一括実装しないための段階的ゲート）

このロードマップは長期仕様を小さな出荷可能単位へ分解するためのもの。**各段階の完了条件を満たすまで、次段階の機能を抱き合わせない。** 特にアーキテクチャ刷新と大規模 UI 新設を同時に行わない。

**Phase 0 — Stabilize（現行 Digital を壊れない状態へ戻す）**
- Issue #31 の config 画面クラッシュを修正し、inflate のスモークテストを追加する。
- §5.8 の未定義契約を docs/spec に固定する。
- 4×1 / 4×2 の既存導線を実機で再確認し、AOSP の青・アイコン等の意図しない露出を整理する。
- 出荷ゲート: **現行 Digital widget を追加 → 設定 → 表示 → リサイズできる。**

**Phase 1A — Digital Core（Clocky が主力 Widget を所有する）**
- Design model v2 と v1 → v2 migration。
- Clocky-owned Digital provider と renderer を作り、AOSP Digital provider への寄生を主経路から外す。
- SizeClassResolver はまず **Strip / Card の2クラス**だけ。
- Time / Date、font / weight / size / color / opacity、alignment、date format、Native background を描画まで通す。
- 実際の widget と同じ RemoteViews を `apply()` する PreviewHost を作る。
- 旧MVP importer は、既存開発版の設定保持が必要な場合だけこの段階または後続の移行作業として実施する。
- 出荷ゲート: **Clocky-owned Digital が 4×1 / 4×2 系で安定し、設定とプレビューが一致する。**

**Phase 1B — Easy Creation（選んですぐ完成する体験）**
- Gallery + Quick Tune の最小版。
- 4 Kit（Default / Minimal / Bold / Editorial）を用意し、**各 Kit 2つの Digital design = 8デザイン**から開始する。
- Quick Tune は palette / typography / template / size / date visibility の高頻度項目だけを扱う。
- Material You / Follow system は、この段階で Digital の主経路に載せる。
- 出荷ゲート: **初めてのユーザーが数タップで見栄えの良い Digital を作れる。**

**Phase 2 — Studio Fundamentals（直接操作より先に、詳細編集を完成させる）**
- Studio v1: Time / Date / Info / Background / Layout / Behavior の意味スロット編集。
- 数値入力、Basic / Advanced、リセット、Undo/Redo、要求値→実効値の縮退表示。
- フォントライブラリを12 family程度まで拡張。AM/PM、先頭ゼロ、次のアラーム、第2タイムゾーン、tap zones、コントラスト警告。
- Rendered background（gradient / outline）と Legibility shadow。
- 出荷ゲート: **キャンバス直接操作なしでも、上級者が Digital を細部まで作り込める。**

**Phase 3 — Responsive Canvas & Library（直接操作とデザイン資産化）**
- Square / Large size class と汎用 override UI。
- キャンバスのドラッグ・スナップ・ピンチ・擬似リサイズを追加する。
- Stacked family。
- My Designs、複製、お気に入り、Import / Export、共有、他 widget への適用、backup / restore、requestPinAppWidget、generated previews。
- 出荷ゲート: **デザインを保存・複製・共有し、複数サイズで自然に使える。**

**Phase 4 — Families（主力が成熟してから系統を増やす）**
- Analog は事前 spike が通った API / launcher 範囲だけ実装する。
- World Clock、Stopwatch を追加。
- 既存4 Kitを Digital / Stacked / Analog / World Clock へ展開し、Library を **16デザイン以上**にする。
- Stopwatch は family 数の都合で Kit への展開を必須にしない。
- 出荷ゲート: **時計 widget の主要 family が共通 Design model / UX で揃う。**

**Phase 5 — Maturity（最終到達点）**
- Library を40〜48、8〜10 Kitへ。フォントを24 familyへ。
- RTL、locale、font scale、TalkBack、launcher matrix、電池・性能を監査する。
- Google Clock との parity と Clocky 独自価値の両方を side-by-side で検証する。
- Future 項目は必要性と Android platform の進展を見て再評価する。

**スコープ規則:** Digital が Phase 1B の体験ゲートを満たすまでは、新 family を増やさない。新しい第6 family、自由レイヤー、天気・予定等を追加したい場合は、実装前に本 End-State Specification 自体を改訂する。

## 14. Definition of Done

**体験**
- [ ] 初めてのユーザーが、ウィジェットを追加してから満足のいくデザインを置くまで、30秒・5タップ以内で済む（社内ユーザーテストで中央値を測る）
- [ ] Library に 40個以上・8 Kit 以上あり、すべての Kit が Digital/Stacked/Analog/World Clock で一貫した見た目になっている
- [ ] 時刻・日付・Info・背景の Basic 項目を、Studio の第一階層だけで変更できる
- [ ] 上級者向けの全項目が、数値入力・リセット・サイズクラス別 override・Undo に対応している
- [ ] Studio のプレビューと実機のウィジェットが、同じ RemoteViews から描画されている

**正しさ**
- [ ] 時刻の表示はすべてホスト側で自走する View だけ。アプリによる毎分更新はゼロ
- [ ] 日付・タイムゾーン・locale・12/24h・次のアラームの変更が、1分以内に反映される
- [ ] 複数のウィジェットがそれぞれ独立した設定を持つ。削除したら設定も消え、バックアップから復元するとIDが付け替えられる
- [ ] 既存開発版からの移行を提供する場合、対象となる旧MVP設定を損失なくインポートできる（公開製品のEnd-State必須条件ではない）
- [ ] すべての縮退（API や ランチャーによるもの）がエディタで開示される

**互換性**
- [ ] minSdk から最新まで、各 API の帯（23〜27 / 28〜30 / 31〜34 / 35 以上）で全 family が描画される
- [ ] Pixel / One UI / Moto / Nova / Lawnchair で、2×1・4×1・2×2・4×2・5×3 のリサイズで文字が切れたり重なったりしない
- [ ] 縦向きと横向き、light/dark、Material You、RTL（ar/he）、ja/en、font scale 200% で崩れない

**アクセシビリティと性能**
- [ ] ウィジェットの content description が「時刻・日付・次のアラーム」を読み上げる
- [ ] エディタを TalkBack だけで最後まで操作できる。タッチ領域は 48dp 以上
- [ ] 既定のプリセットはすべてコントラスト基準を満たす
- [ ] 1ウィジェットあたりの再描画が1日数回程度（イベント時だけ）。背景ビットマップは画面サイズの1.5倍以内

---

## 最終評価 —「これ以上ウィジェット機能を足さなくても満足できるか」

- **普通のユーザー**: Gallery で選んで、Quick Tune で色と書体を変え、Material You と light/dark に追従させる。これで満足できる。Google Clock との差は「選べて美しい」こと。**十分。**
- **こだわりたいユーザー**: 書体（24ファミリー × weight 100〜900）、時と分の個別スタイル、字間、構図7種、ドラッグとスナップ、サイズごとの override、背景の gradient と枠線、Analog の部品単位の指定まで揃う。足りないのは文字のエフェクトと自由レイヤーだけで、どちらも意図的に外している。**十分。** KWGT と比べられたら、「時計に限っては Clocky の方が速く・美しく・電池に優しい」と答えられる。
- **見直しで見つけて統合した不足**:
  1. 系統をまたいだ統一感 → **Style Kit** として統合した（§4・§8）
  2. 復元時の appWidgetId の付け替え → Essential に追加した
  3. ランチャーの余白差 → キャリブレーション inset と Match system 角丸で対応した
  4. 壁紙が読めない制約 → wallpaper color のヒントとエディタの背景切り替えで代替した
  5. 縮退がユーザーに見えない問題 → 縮退の開示を原則に昇格させた
  6. CJK の日付とフォントのカバー範囲 → フォールバックの明示とメタデータで対応した
- **残る不確実性（前提として明示する）**:
  - 可変フォントで `textFontWeight` が wght 軸にどこまで効くかは、端末とバージョンによって違う可能性がある。実機で確認するまでは、静的な9 weight を前提にする。
  - `AnalogClock` に Icon で文字盤と針を渡す方法が OEM のランチャーで動くか、実機で確認が必要。
  - サイズクラスのしきい値（100dp / 200dp / 1.4）は実測で確定させる。
  - RemoteCompose がウィジェット向けに安定するかどうかが、Future 項目を解禁する条件になる。

## Verification（この仕様の検証方法）

1. Phase 0 で、§5.8 の契約を docs/spec に転記する。backlog の未定義5項目を閉じ、レビューで合意する。
2. §9 のサイズクラスのしきい値は、Issue #31 と同じ診断用ビルドで5つのランチャーの OPTION 値を集めて確定させる。
3. フォント断片の合成方式は、Phase 1 の最初の spike（書体2つ × weight 9 × 要素2、API 23/28/31/35 のエミュレータ）で、描画・RemoteViews のサイズ制限・時刻の自走を確認してから本実装に進む。
4. Analog の Icon 方式は、Phase 4 の前に API 31 以上の実機3機種で spike する。
5. Easy Creation の「30秒・5タップ」は、Phase 1B の終わりに3〜5人でユーザーテストして確かめる。


## Specification governance

- 本文書は **長期の製品境界と完成条件**を定義する。日々の実装進捗はここへ書き込まず、`docs/IMPLEMENTATION_BACKLOG.md` / Issue / PR で管理する。
- Android API や launcher 実機検証で前提が崩れた場合は、黙って別方式へ逸脱せず、本仕様の該当節を更新して理由を残す。
- End-State にない大機能を追加する場合は「既存原則の延長か」「Clocky を汎用 builder 化しないか」を先に評価する。
- **最終判断基準:** 機能数ではなく、「時計に限れば、汎用 widget builder より速く、破綻しにくく、美しく作れる」状態を守る。

## Sources
- [Add previews to your widget picker (Android Developers)](https://developer.android.com/develop/ui/views/appwidgets/previews)
- [Generated previews (Android Developers)](https://developer.android.com/develop/ui/compose/glance/generated-previews)
- [RemoteViews.DrawInstructions (Android Developers)](https://developer.android.com/reference/kotlin/android/widget/RemoteViews.DrawInstructions)
- [From RemoteViews to RemoteCompose (Medium)](https://medium.com/@fioravanti.luka/glance-remoteviews-and-remotecompose-what-actually-changed-in-android-16-4afc4b63b0ad)
- [Track metrics for your widget (Android Developers)](https://developer.android.com/develop/ui/compose/glance/metrics)
- [Create an advanced widget (Android Developers)](https://developer.android.com/develop/ui/views/appwidgets/advanced)
- [Google Clock 8.1 Material 3 Expressive redesign (9to5Google)](https://9to5google.com/2025/08/22/google-clock-material-3-expressive/) — ウィジェットには変更がないと報じられている
- [Best Android clock widgets (Android Authority)](https://www.androidauthority.com/best-android-clock-widgets-weather-clock-widgets-883303/)
- [KWGT (Google Play)](https://play.google.com/store/apps/details?id=org.kustom.widget&hl=en_US)
- [One UI 8 lock screen clock customization (Android Authority)](https://www.androidauthority.com/one-ui-8-beta-lock-screen-customizable-clock-settings-3571660/)