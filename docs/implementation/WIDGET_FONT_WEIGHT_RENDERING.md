# Digital widget font-weight rendering

Clocky stores the user-requested time and date weights independently as values in the semantic range `100..900`.

The classic Android home-screen widget is rendered with `RemoteViews`, which cannot receive an arbitrary `Typeface` instance. Clocky therefore keeps the requested value in `WidgetSettings` and resolves only the rendering backend:

- API 28+: canonical `100, 200, ... 900` faces using `android:textFontWeight`.
- API 23–27: nearest stable built-in platform face among `100, 300, 400, 500, 700, 900`.

The digital widget contains static `TextClock` variants for the canonical weights and makes exactly one time face and one date face visible. `TextClock` remains responsible for ticking, so font-weight customization does not introduce an app-driven minute update loop.

The offscreen size-measurement path applies the same resolved effective weight as the final RemoteViews renderer. This keeps the AOSP binary-search sizing behavior aligned with the visible widget.

A requested value such as `575` remains stored as `575`; the widget renderer resolves it to the nearest currently renderable face without mutating the saved setting.
