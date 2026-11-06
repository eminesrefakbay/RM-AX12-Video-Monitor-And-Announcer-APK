package dev.ax12.announcer

/**
 * Starting rules, written for the AKB7 INAV mode ranges (AUX1 = ch5 ... AUX8 = ch12).
 * Edit freely in the app. Open MENU > Settings > Diagnostics > Show variables to see every name you can use.
 */
object DefaultRules {
    const val TEXT = """# one rule per line:   condition -> spoken text
# optional:  text | text spoken when it turns false     ! = urgent     @10 = repeat every 10 s
hysteresis = 25
var_hysteresis = 1
hold = 100
initial = off
scale = auto

# --- switches (INAV mode ranges, channel numbers = RC channels, 1000..2000 us) ---
ch5 >= 1950 -> armed | disarmed
ch6 >= 1950 -> angle mode
ch6 >= 1450 and ch6 <= 1550 -> horizon mode
ch7 >= 1875 -> !return to home | return to home off
ch10 <= 1125 or ch10 >= 1875 -> position hold | position hold off
ch10 >= 1950 -> altitude hold
ch9 <= 1100 -> airmode on | airmode off

# --- battery, volts per cell (6S): the flight controller's value, as shown on the OSD ---
# Li-ion landing about 3.6 V: warning 3.70, critical 3.55.  For the LiPo use 3.85 / 3.70.
# "vbat > 12" keeps the critical warning quiet when the flight controller is powered from USB only (vbat = 0).
vbat / 6 < 3.70 and vbat / 6 >= 3.55 -> battery low @30
vbat / 6 < 3.55 and vbat > 12 -> !battery critical @10

# --- link: only while the arm switch is on (ch5), so an unpowered aircraft stays silent ---
up_lq < 70 and ch5 >= 1950 -> link quality low
up_lq < 40 and ch5 >= 1950 -> !link critical @5
tlm == 0 and ch5 >= 1950 -> !telemetry lost

# --- the flight controller's own flight mode (check the exact names under Settings > Diagnostics > Show variables) ---
fm ~ "RTH" -> return to home active
"""
}
