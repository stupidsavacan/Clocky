package com.android.alarmclock

import com.stupidsavacan.clocky.widget.digital.ClockyDigitalWidgetProvider

/**
 * Registered component name of the Clocky Digital widget, kept from the AOSP port so widgets that
 * users already placed survive app updates. The implementation is entirely Clocky-owned
 * ([ClockyDigitalWidgetProvider]). Renaming the component deletes placed widgets on update, even
 * with `android.appwidget.oldName` (verified on API 30); see
 * docs/architecture/PHASE_1A_DIGITAL_CORE.md section 5.
 */
class DigitalAppWidgetProvider : ClockyDigitalWidgetProvider()
