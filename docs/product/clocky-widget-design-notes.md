# Clocky Widget Design Mockup — 備考

## 位置づけ

このフォルダの HTML は、Clocky の長期 End-State を画面へ落とし込むための **UI/UX 基準案・インタラクティブモック** である。

製品境界・機能要件・完成条件の正本は `CLOCKY_END_STATE.md`。HTML と End-State が矛盾する場合は **End-State を優先**する。HTML は production source ではなく、実装時の見た目・情報階層・操作フローを共有するための参照資料として扱う。

## この案で維持したい核

- **Gallery → Quick Tune → Studio → My Designs / 共有** の段階的な導線。
- Preset-first, never preset-locked。完成デザインから始められるが、必要なら詳細編集まで降りられる。
- Gallery から **「このまま追加」** でき、細かく触りたくないユーザーを Studio に強制しない。
- Quick Tune は高頻度の変更だけに絞り、数タップで見栄えのよい結果へ到達できる構成を維持する。
- Studio は Time / Date / Info / Background / Layout などの **semantic slot** を編集する構造とし、汎用レイヤービルダーにはしない。
- UI 自体は restrained neutral base を保ち、AOSP DeskClock の強い青を Clocky 固有画面へ引き継がない。
- Widget instance と Design asset を分けて扱う。My Designs では保存・複製・再利用・共有へ自然につなげる。

## 実装前に直す点

### 1. Surprise me は完全ランダムにしない

モックの Surprise me は、最終実装では **選択中の Style Kit が許可する palette / typography / template 等の範囲内**でシャッフルする。

無関係な値を完全ランダムに組み合わせる方式は、Clocky が保証したい「破綻しにくく、美しい」結果と相性が悪いため採用しない。

### 2. Quick Tune の S / M / L は意味を明示する

S / M / L が widget size なのか文字サイズなのか初見で曖昧にならないよう、**「文字サイズ」等のラベルを付ける**。

Studio の Strip / Card / Square / Large は widget の size class として別概念なので、UI 上でも混同させない。

### 3. Studio に Behavior / 動作を追加する

End-State の Studio は `Time / Date / Info / Background / Layout / Behavior` を扱う。

12/24h、tap action、seconds などの設定先として **Behavior / 動作** を追加する。狭い画面ではタブを無理に詰めず、横スクロール可能な chip / tab row でよい。

### 4. タップ領域は 48dp 以上を保証する

見た目上の chip や control が小さくても、Android 実装では透明な hit area を含めて **48dp 以上**を確保する。これは End-State の accessibility DoD に従う。

## Roadmap との対応

- **Gallery / Quick Tune**: Phase 1B — Easy Creation の主な目標画面。
- **Studio のプロパティ編集**: Phase 2 — Studio Fundamentals。
- **直接操作・size class override・My Designs・共有**: 主に Phase 3 — Responsive Canvas & Library。
- Analog / World Clock / Stopwatch への展開は Phase 4 以降。Digital が Phase 1B の体験ゲートを満たす前に、このモックを理由として新 family を先行実装しない。

## 実装時の注意

- Preview は最終的に production widget と **同じ ResolvedSpec → 同じ RemoteViews** を使う。HTML のブラウザ描画を production preview 実装へそのまま移植しない。
- HTML 内の寸法・色・アニメーション・操作はデザイン意図の参考値であり、Android / RemoteViews / launcher 制約より優先されない。
- API / launcher による縮退が必要な機能は、要求値を失わず描画時に解決し、Studio 上で縮退を開示する。
- Google / Apple の proprietary font / asset はモックに見た目が近くても製品へコピーしない。

## 最終判断基準

このモックを実装へ落とす際も、機能数を増やすこと自体を目的にしない。

**「時計に限れば、汎用 widget builder より速く、破綻しにくく、美しく作れる」**

という Clocky の最終判断基準を維持する。
