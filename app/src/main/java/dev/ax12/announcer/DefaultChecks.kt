package dev.ax12.announcer

/**
 * Starting pre-flight checks. Each rule is one check: the condition true = tick, false = cross, and no value
 * yet = no mark. The text may hold live values: {sats}, {up_lq}, {vbat/6:2}.
 */
object DefaultChecks {
    const val TEXT = """# pre-flight checks:   condition -> label      (a tick when the condition is true, a cross when not)
# live values in the label: {sats}, {up_lq}, {vbat/6:2} = volts per cell
hysteresis = 25
var_hysteresis = 1
hold = 500
scale = auto

vbat > 12 -> Battery connected
vbat / 6 >= 4.15 -> Battery full {vbat/6:2} V per cell
sats >= 10 -> GPS {sats} satellites (need 10)
up_lq >= 95 -> Link quality {up_lq}%
ch9 <= 1100 -> Airmode switch on
ch5 < 1500 -> Arm switch off
"""
}
