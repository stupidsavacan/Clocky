# Google Clock 7.13 参照メモ

Clocky の設計検討で参照した Google 時計 Digital ウィジェットの識別情報と、実機で確認した範囲を記録する。

この文書は**再現性のためのメタデータ記録**であり、Google 時計 APK 本体を Clocky の依存物・配布物として扱うものではない。

## APK 識別情報

| 項目 | 値 |
|---|---|
| Package | `com.google.android.deskclock` |
| Version name | `7.13 (745094482)` |
| Version code | `76007130` |
| minSdk | `23` |
| targetSdk | `35` |
| 取得元 | 開発基準端末にインストール済みの Google 時計を ADB でローカル抽出 |

### ファイル

| ファイル | サイズ | SHA-256 |
|---|---:|---|
| `base.apk` | 14,906,517 bytes | `58fc4c98cfc80786d81a927fed6b87852b8367734f758523c8264be5a2ded21d` |
| `split_config.xhdpi.apk` | 77,330 bytes | `65ca09d76f4e7b5dc174eee36be60c1670ae5578352df55fd2570b12fbad9191` |

ローカル保存時の推奨配置：

```text
reference_apk/
└─ google-clock-7.13/
   ├─ base.apk
   └─ split_config.xhdpi.apk
```

`reference_apk/` は Git 管理対象外。

## 基準ウィジェット

現在のホーム画面で確認した Provider：

```text
com.google.android.deskclock/
com.android.alarmclock.DigitalAppWidgetProvider
```

AppWidget ID：`11`

Motorola Launcher 上の現在値：

| 項目 | 値 |
|---|---:|
| span | 4 × 2 |
| minSpan | 2 × 1 |
| page | 0 |
| cell | `(0,3)` |
| HostView | 692 × 484 px |
| HostView | 約 395.4 × 276.6 dp |

基準端末：

| 項目 | 値 |
|---|---:|
| Display | 720 × 1600 px |
| Density | 280 dpi = 1.75 px/dp |
| Logical display | 約 411 × 914 dp |

## UIAutomator で確認した 4×2 bounds

| View | bounds | px | dp |
|---|---|---:|---:|
| Launcher Widget Host | `[14,815][706,1299]` | 692 × 484 | 約 395.4 × 276.6 |
| `themed_root` | `[47,829][673,1285]` | 626 × 456 | 約 357.7 × 260.6 |
| `android:id/background` | `[112,857][608,1256]` | 496 × 399 | 約 283.4 × 228.0 |
| `date` | `[137,875][583,943]` | 446 × 68 | 約 254.9 × 38.9 |
| `clock` | `[137,905][583,1214]` | 446 × 309 | 約 254.9 × 176.6 |

## AppWidget Provider 情報

Google 時計 7.13 では次の Provider を確認済み。

- `AnalogAppWidgetProvider`
- `StopwatchAppWidgetProvider`
- `DigitalAppWidgetProvider`
- `DigitalStackedAppWidgetProvider`
- `DigitalCitiesAppWidgetProvider`

Digital の宣言上の主な値：

```text
minWidth           = 150dp
minHeight          = 100dp
minResizeWidth     = 85dp
minResizeHeight    = 0dp
targetCellWidth    = 2
targetCellHeight   = 2
resizeMode         = horizontal | vertical
updatePeriodMillis = 0
```

## Clocky で参考にする考え方

- classic AppWidget / `RemoteViews`
- 不要な定期フル更新を避ける
- Widget Host のサイズ情報を利用する
- 時計と日付を視覚的に分離する
- Host 全面を必ずしも埋めず、余白をデザインとして使う
- サイズに応じて文字サイズ・余白・補助要素を調整する

Clocky は独立実装とし、Google 時計のコード・画像・フォント等をそのまま取り込まない。

## Artifact / Release ガード

この APK は参照用であり、Clocky の GitHub Actions Artifact / Release に含めない。

ガード条件：

- 上記 SHA-256 と一致するファイルは reject
- package が `com.google.android.deskclock` の APK は reject
- `reference_apk/` を Artifact 対象にしない
- Build workflow は Clocky の Gradle 出力だけを明示的に対象にする

詳細は [`MVP_計画書.md`](../../MVP_計画書.md) の「参照APKの保管と GitHub Actions 出力ガード」を参照。
