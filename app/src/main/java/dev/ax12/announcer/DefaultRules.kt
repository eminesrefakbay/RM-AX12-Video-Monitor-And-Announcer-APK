package dev.ax12.announcer

/**
 * Starting rules, written for the AKB7 INAV mode ranges (AUX1 = ch5 ... AUX8 = ch12).
 * Edit freely in the app. Channel numbers are RC channel numbers.
 */
object DefaultRules {
    const val TEXT = """# one rule per line:   condition -> spoken text
# optional:  text | text spoken when it turns false     ! = urgent     @10 = repeat every 10 s
hysteresis = 25
hold = 100
initial = off

ch5 >= 1950 -> armed | disarmed
ch6 >= 1950 -> angle mode
ch6 >= 1450 and ch6 <= 1550 -> horizon mode
ch7 >= 1875 -> !return to home | return to home off
ch10 <= 1125 or ch10 >= 1875 -> position hold | position hold off
ch10 >= 1950 -> altitude hold
ch9 <= 1100 -> airmode on | airmode off
"""
}
