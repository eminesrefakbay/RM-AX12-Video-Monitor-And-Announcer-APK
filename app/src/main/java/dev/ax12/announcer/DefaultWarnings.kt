package dev.ax12.announcer

/**
 * Starting warning rules for the status bar. A warning shows while its condition is true.
 * Text may hold live values: {vbat/6:2} = volts per cell with 2 decimals.
 * Severity: !text = critical (red, blinking), text = warning (amber), *text = info (blue).
 */
object DefaultWarnings {
    const val TEXT = """# warnings for the status bar:   condition -> text      (shown while the condition is true)
# !text = critical (red)    text = warning (amber)    *text = info (blue)
# live values in the text: {vbat/6:2} = volts per cell, {up_lq}, {sats}, {fm}
hysteresis = 25
var_hysteresis = 1
hold = 300
scale = auto

vbat / 6 < 3.70 and vbat / 6 >= 3.55 -> BATTERY LOW {vbat/6:2} V per cell
vbat / 6 < 3.55 and vbat > 12 -> !BATTERY CRITICAL {vbat/6:2} V per cell
up_lq < 70 and up_lq >= 40 and ch5 >= 1950 -> LINK QUALITY {up_lq}%
up_lq < 40 and ch5 >= 1950 -> !LINK CRITICAL {up_lq}%
tlm == 0 and ch5 >= 1950 -> !TELEMETRY LOST
sats < 8 and sats > 0 -> GPS {sats} SATELLITES
fm ~ "RTH" -> !RETURN TO HOME
ch5 >= 1950 -> *ARMED
"""
}
