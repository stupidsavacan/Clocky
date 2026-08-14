#!/usr/bin/env python3
from pathlib import Path

PATH = Path("app/src/main/java/com/android/alarmclock/DigitalAppWidgetProvider.kt")
text = PATH.read_text()


def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one match, found {count}: {old[:80]!r}")
    text = text.replace(old, new, 1)


replace_once(
'''    private class Sizes(
        val mTargetWidthPx: Int,
        val mTargetHeightPx: Int,
        val largestClockFontSizePx: Int
    ) {
        val smallestClockFontSizePx = 1
''',
'''    private class Sizes(
        val mTargetWidthPx: Int,
        val mTargetHeightPx: Int,
        private val requestedClockFontSizePx: Int,
        private val requestedDateFontSizePx: Int,
    ) {
        val largestFontScalePermille = 1000
        val smallestFontScalePermille = 1
''')

replace_once(
'''        var clockFontSizePx: Int
            get() = mClockFontSizePx
            set(clockFontSizePx) {
                mClockFontSizePx = clockFontSizePx
                mFontSizePx = Math.max(1, Math.round(clockFontSizePx / 7.5f))
                mIconFontSizePx = (mFontSizePx * 1.4f).toInt()
                mIconPaddingPx = mFontSizePx / 3
            }
''',
'''        var fontScalePermille: Int = largestFontScalePermille
            set(scalePermille) {
                field = scalePermille.coerceIn(smallestFontScalePermille, largestFontScalePermille)
                mClockFontSizePx = Math.max(
                        1,
                        Math.round(requestedClockFontSizePx * field / 1000f),
                )
                mFontSizePx = Math.max(
                        1,
                        Math.round(requestedDateFontSizePx * field / 1000f),
                )
                mIconFontSizePx = (mFontSizePx * 1.4f).toInt()
                mIconPaddingPx = mFontSizePx / 3
            }
''')

replace_once(
'''        fun newSize(): Sizes {
            return Sizes(mTargetWidthPx, mTargetHeightPx, largestClockFontSizePx)
        }
''',
'''        fun newSize(): Sizes {
            return Sizes(
                    mTargetWidthPx,
                    mTargetHeightPx,
                    requestedClockFontSizePx,
                    requestedDateFontSizePx,
            )
        }
''')

replace_once(
'''            append(builder, "Clock font: %dpx\\n", mClockFontSizePx)
''',
'''            append(builder, "Clock font: %dpx\\n", mClockFontSizePx)
            append(builder, "Date font: %dpx\\n", mFontSizePx)
            append(builder, "Font scale: %d/1000\\n", fontScalePermille)
''')

replace_once(
'''            val resolvedWeights = DigitalWidgetProfileResolver.resolveWeights(
                    widgetSettings,
                    options.getInt(OPTION_APPWIDGET_MIN_HEIGHT),
            )
''',
'''            val resolvedWeights = DigitalWidgetProfileResolver.resolveWeights(
                    widgetSettings,
                    options.getInt(OPTION_APPWIDGET_MIN_HEIGHT),
            )
            val resolvedSizes = DigitalWidgetProfileResolver.resolveSizes(
                    widgetSettings,
                    options.getInt(OPTION_APPWIDGET_MIN_HEIGHT),
            )
''')

replace_once(
'''            val largestClockFontSizePx: Int =
                    resources.getDimensionPixelSize(R.dimen.widget_max_clock_font_size)

            // Create a size template that describes the widget bounds.
            val template = Sizes(targetWidthPx, targetHeightPx, largestClockFontSizePx)
''',
'''            val scaledDensity: Float = resources.getDisplayMetrics().scaledDensity
            val requestedClockFontSizePx = Math.max(
                    1,
                    Math.round(resolvedSizes.timeSizeSp * scaledDensity),
            )
            val requestedDateFontSizePx = Math.max(
                    1,
                    Math.round(resolvedSizes.dateSizeSp * scaledDensity),
            )

            // Start at the exact Clocky-requested sizes. If they do not fit the widget bounds,
            // the existing binary search scales time and date down together while preserving the
            // user-requested ratio.
            val template = Sizes(
                    targetWidthPx,
                    targetHeightPx,
                    requestedClockFontSizePx,
                    requestedDateFontSizePx,
            )
''')

replace_once(
'''            // Measure the widget at the largest possible size.
            var high = measure(template, template.largestClockFontSizePx, sizer)
            if (!high.hasViolations()) {
                return high
            }

            // Measure the widget at the smallest possible size.
            var low = measure(template, template.smallestClockFontSizePx, sizer)
            if (low.hasViolations()) {
                return low
            }

            // Binary search between the smallest and largest sizes until an optimum size is found.
            while (low.clockFontSizePx != high.clockFontSizePx) {
                val midFontSize: Int = (low.clockFontSizePx + high.clockFontSizePx) / 2
                if (midFontSize == low.clockFontSizePx) {
                    return low
                }
                val midSize = measure(template, midFontSize, sizer)
                if (midSize.hasViolations()) {
                    high = midSize
                } else {
                    low = midSize
                }
            }
''',
'''            // First try the exact Clocky-requested sizes.
            var high = measure(template, template.largestFontScalePermille, sizer)
            if (!high.hasViolations()) {
                return high
            }

            // Verify that the smallest common scale fits before searching between both ends.
            var low = measure(template, template.smallestFontScalePermille, sizer)
            if (low.hasViolations()) {
                return low
            }

            // Binary search a common scale so independent time/date requested sizes retain their
            // ratio while the complete widget remains inside the host bounds.
            while (low.fontScalePermille != high.fontScalePermille) {
                val midScale: Int = (low.fontScalePermille + high.fontScalePermille) / 2
                if (midScale == low.fontScalePermille) {
                    return low
                }
                val midSize = measure(template, midScale, sizer)
                if (midSize.hasViolations()) {
                    high = midSize
                } else {
                    low = midSize
                }
            }
''')

replace_once(
'''        /**
         * Compute all font and icon sizes based on the given `clockFontSize` and apply them to
         * the offscreen `sizer` view. Measure the `sizer` view and return the resulting
         * size measurements.
         */
        private fun measure(template: Sizes, clockFontSize: Int, sizer: View): Sizes {
''',
'''        /**
         * Compute all font and icon sizes at a common fraction of the Clocky-requested time/date
         * sizes, apply them to the offscreen `sizer`, and return the resulting measurements.
         */
        private fun measure(template: Sizes, scalePermille: Int, sizer: View): Sizes {
''')

replace_once(
'''            // Adjust the font sizes.
            measuredSizes.clockFontSizePx = clockFontSize
''',
'''            // Adjust both fonts by the same scale to preserve the requested time/date ratio.
            measuredSizes.fontScalePermille = scalePermille
''')

PATH.write_text(text)
print("Applied Clocky Digital Widget size renderer patch")
