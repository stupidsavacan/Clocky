# Clocky

**Google Clock の使い慣れた時計体験を基準にしつつ、ウィジェットのタイポグラフィとレイアウトを大幅に拡張する Android 時計アプリ。**

> Status: **standalone AOSP DeskClock port builds on GitHub Actions / source-defined Digital Widget customization integrated / real-device parity pending**

## Product direction

Clocky は次の3層で構成します。

1. **Immutable upstream provenance** — AOSP DeskClock `android-17.0.0_r1` を `third_party/aosp-deskclock/` に固定保存。
2. **Standalone functional app** — AOSP Alarm / Timer / Stopwatch / World Clock / Digital & Analog Widget を `app/` に直接移植し、通常の Gradle Android アプリ向けに適応。
3. **Clocky experience layer** — Google Clock を UI/UX の参照仕様としてパリティを取り、その上に Clocky 独自の Widget 編集機能を追加。

Google Clock の APK は挙動・寸法・UI/UX の参照にのみ使い、プロプライエタリなコード・画像・フォント・署名資産は Clocky にコピーしません。

長期の製品境界・完成条件・最終到達点は [`docs/product/CLOCKY_END_STATE.md`](./docs/product/CLOCKY_END_STATE.md) を正本とします。日々の実装進捗は `docs/IMPLEMENTATION_BACKLOG.md` / Issue / PR で管理し、End-State 仕様と進捗管理を分離します。

## Current source state

```text
third_party/aosp-deskclock/   # immutable AOSP provenance snapshot
app/                          # standalone Gradle adaptation + Clocky-owned code
legacy/mvp-app-skeleton/      # pre-port Clocky app skeleton snapshot
ci/Clocky_MVP_source.zip      # previous MVP source bundle retained for reference
```

最初の direct-port bootstrap では AOSP source 160 / resources 505 / assets 1 を `app/` にミラー済みです。`app/` にはその後 Clocky 固有コードが追加されています。

## Standalone Gradle adaptation

```text
AOSP tag: android-17.0.0_r1
DeskClock commit: 1f6ebf36d0c14f5e16265d80022cb6068d97cebd
namespace: com.android.deskclock
applicationId: com.stupidsavacan.clocky
compileSdk: 35
minSdk: 23
targetSdk: 35
```

Direct-port では AOSP の package/import を大量変更しないため `namespace = com.android.deskclock` を維持し、インストール識別子だけ `com.stupidsavacan.clocky` に分離しています。Provider authority は `${applicationId}` に変更済みです。

- [`docs/port/DEPENDENCY_MAP.md`](./docs/port/DEPENDENCY_MAP.md)
- [`docs/port/PLATFORM_API_SCAN.md`](./docs/port/PLATFORM_API_SCAN.md)
- [`docs/port/AOSP_MIRROR_STATUS.md`](./docs/port/AOSP_MIRROR_STATUS.md)

## GitHub build status

`Current App CI` が canonical Web build environment です。GitHub Actions 上で debug dependency resolution、compile、unit tests、Android lint + gate、`assembleDebug`、debug APK SHA-256 を継続検証します。Reference APK Guard は独立workflowです。

Actions artifact storage quotaによりAPK/report uploadだけ失敗する場合がありますが、build/test/lint/assemble/hashが成功していれば既存方針どおりnonfatalです。

この検証は **実機・エミュレータ・launcher E2Eの代替ではありません**。実際の表示、clipping、resize interaction、targetSdk runtime behaviorなどはdevice検証が必要です。

## Clocky customization model

Clocky 固有設定は AOSP domain state と分離し、per-`appWidgetId` の schema-versioned JSON として `SharedPreferences` に保存します。

主なmodel項目：

- Time: font family, size, requested weight 100–900, letter spacing, colour/opacity, X/Y, alignment, system/12h/24h, leading zero
- Date: show/hide, font, size, independent weight, letter spacing, colour/opacity, date format, X/Y, alignment
- Layout/background: color/opacity, corner radius, padding, nullable size-profile overrides

- [`docs/spec/FONT_WEIGHT.md`](./docs/spec/FONT_WEIGHT.md)
- [`app/src/main/java/com/stupidsavacan/clocky/customization/`](./app/src/main/java/com/stupidsavacan/clocky/customization/)

## Digital Widget customization currently integrated

GitHub `main` のDigital Widget pathでは、repository内で意味論が確定した次の項目をmodel/storageから描画・設定へ接続しています。

- independent time/date weight rendering + base/profile editor
- date visibility rendering + settings
- independent time/date size rendering + base/profile editor
- independent time/date letter spacing rendering + settings
- 4×1 / 4×2 nullable overrides (`null = inherit`) for supported fields
- independent time/date X/Y resolution
- API 31+ RemoteViews X/Y translation; API 23–30 effective 0dp fallback while preserving requested values
- non-finite X/Y normalization/sanitization
- retained repository-MVPの5 font family（`sans-serif-light`, `sans-serif-rounded`, `serif`, `sans-serif-condensed`, `monospace`）のtime/date rendering compatibility
- explicit hour mode: system-following / forced 12h `h:mm` / forced 24h `HH:mm`
- launcher widget deletion時のper-widget consolidated settings cleanup
- resolver/editor/renderer/model regression tests for the merged paths

一方、modelにfieldが存在するだけでは実装契約とはみなしません。現時点では date-format default/override、ARGB alphaとseparate opacityの合成、LEFT/RIGHTとSTART/ENDのRTL意味、旧transparent/dark/light backgroundの正確な対応が不足しています。これらは推測して接続しません。

X/Y editing UIもrange/interaction semanticsが未定義のため未実装です。詳細は [`docs/IMPLEMENTATION_BACKLOG.md`](./docs/IMPLEMENTATION_BACKLOG.md) を正本にします。

## Existing MVP integration

旧MVPのrepository-owned source bundleは解析済みで、legacy persistence contractは [`docs/architecture/MVP_INTEGRATION.md`](./docs/architecture/MVP_INTEGRATION.md) に固定しました。

確認済みの旧prefsは `clocky_widgets` / `w_<appWidgetId>_...` で、旧MVPには **persisted `leadingZero` keyもprofile-specific preferenceもありません**。完全なprimitive-key importerは、旧値の全persisted semanticsをcurrent model/render contractへlosslessに表現できるまで意図的に追加しません。

AOSP が Alarm / Timer / Stopwatch / World Clock の状態を所有し、Clocky は typography/layout/preset を所有します。Widget が独自に timer/stopwatch state を持つことは禁止します。

## Google Clock parity target

通常利用で Google Clock にある主要 app/widget families を取りこぼさないことを目標にします。

- [`docs/parity/GOOGLE_CLOCK_PARITY.md`](./docs/parity/GOOGLE_CLOCK_PARITY.md)
- [`docs/parity/GOOGLE_CLOCK_UI_UX_SPEC.md`](./docs/parity/GOOGLE_CLOCK_UI_UX_SPEC.md)
- [`docs/parity/WIDGET_PARITY_SPEC.md`](./docs/parity/WIDGET_PARITY_SPEC.md)
- [`docs/parity/REFERENCE_CAPTURE_PROTOCOL.md`](./docs/parity/REFERENCE_CAPTURE_PROTOCOL.md)

## Next gates

Web-safe側で残るのは、repository内で意味論が明示された追加作業または契約の明確化です。意味論が不足したUI/renderingを推測して実装しません。

Device/reference側には引き続き次が残ります。

```text
real-device functional smoke test
        ↓
launcher / resize / RemoteViews behavior verification
        ↓
targetSdk runtime behavior verification
        ↓
Google Clock side-by-side parity measurement
        ↓
release validation
```

GitHub Actionsがgreenでも、上記を「検証済み」とは表現しません。

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

See [`THIRD_PARTY_AND_REFERENCE_POLICY.md`](./THIRD_PARTY_AND_REFERENCE_POLICY.md), [`docs/IMPLEMENTATION_BACKLOG.md`](./docs/IMPLEMENTATION_BACKLOG.md), and [`docs/WEB_AGENT_HANDOFF.md`](./docs/WEB_AGENT_HANDOFF.md).