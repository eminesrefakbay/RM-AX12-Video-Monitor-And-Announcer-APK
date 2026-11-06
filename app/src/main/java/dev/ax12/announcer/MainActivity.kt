package dev.ax12.announcer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TreeMap

/**
 * Full-screen video; grey "NO VIDEO" panel when no frames arrive; spoken announcements from the
 * sound rules. The radio is only read, never written.
 */
class MainActivity : Activity() {

    private lateinit var texture: TextureView
    private lateinit var noFeed: View
    private lateinit var noFeedDetail: TextView
    private lateinit var status: TextView
    private lateinit var testPanel: View
    private lateinit var varsPanel: View
    private lateinit var varsText: TextView
    private lateinit var camera: CameraPreview
    private lateinit var speaker: Speaker
    private lateinit var rulesPanel: RulesPanel
    private lateinit var settingsPanel: SettingsPanel
    private lateinit var mainMenu: MainMenu
    private lateinit var warnPanel: RulesPanel
    private lateinit var sticksView: SticksView
    private lateinit var warningText: TextView
    private lateinit var warningBar: WarningBar
    private var sticksEnabled = false
    private lateinit var menuButton: Button
    private var overlayWasOpen = false
    private val hideMenuRunnable = Runnable {
        if (!Prefs.menuAlways(this) && !anyOverlayOpen()) menuButton.visibility = View.GONE
    }
    private var latestNumbers: Map<String, Double> = HashMap()
    private var latestTexts: Map<String, String> = HashMap()

    private var engine: RuleEngine? = null
    private var sdkScaler = ChannelScaler(true, ChannelScaler.Mode.PERCENT)
    private val fakeScaler = ChannelScaler(false, ChannelScaler.Mode.MICROS)

    private val fakeLink = FakeRadioLink()
    private val sdkLink: RadioLink? by lazy { if (AxSdkJni.loaded) AxSdkRadioLink(this) else null }
    private var sdkError: String? = null
    private var sdkRunning = false

    private var testVisible = false
    private var varsVisible = false
    private var lastVarsUpdate = 0L
    private val ui = Handler(Looper.getMainLooper())
    private val uiTiming = TimingStats(5000)
    private var lastTickAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        texture = findViewById(R.id.texture)
        noFeed = findViewById(R.id.noFeed)
        noFeedDetail = findViewById(R.id.noFeedDetail)
        status = findViewById(R.id.status)
        testPanel = findViewById(R.id.testPanel)
        varsPanel = findViewById(R.id.varsPanel)
        varsText = findViewById(R.id.varsText)

        speaker = Speaker(this)
        camera = CameraPreview(this, texture)

        buildTestPanel()
        menuButton = findViewById(R.id.menuButton)
        menuButton.setOnClickListener { mainMenu.show() }

        val liveValues = object : LiveValues {
            override fun numbers(): Map<String, Double> = latestNumbers
            override fun texts(): Map<String, String> = latestTexts
        }
        val frame = findViewById<FrameLayout>(R.id.rootFrame)
        val full = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        // overlays on the video: the sticks and the warning bar (panels are added after them, so they sit on top)
        sticksView = SticksView(this)
        sticksView.visibility = View.GONE
        frame.addView(sticksView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        warningText = TextView(this)
        warningText.textSize = 18f
        warningText.setTypeface(null, Typeface.BOLD)
        warningText.gravity = Gravity.CENTER
        warningText.maxLines = 2
        warningText.setPadding(dp(12), dp(6), dp(12), dp(6))
        warningText.visibility = View.GONE
        frame.addView(warningText, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        warningBar = WarningBar(warningText)

        rulesPanel = RulesPanel(
            this, speaker, liveValues,
            { reloadRules() },
            { startActivity(Intent(this, RulesActivity::class.java)) },
            { Prefs.rules(this) },
            { Prefs.setRules(this, it) }
        )
        warnPanel = RulesPanel(
            this, speaker, liveValues,
            { reloadWarnings() },
            { startActivity(Intent(this, RulesActivity::class.java).putExtra("warnings", true)) },
            { Prefs.warningRules(this) },
            { Prefs.setWarningRules(this, it) },
            warning = true,
            titleText = "Warning rules",
            testWarning = { text, level -> warningBar.showTest(text, level, SystemClock.elapsedRealtime()) }
        )
        settingsPanel = SettingsPanel(this, settingsHost)
        frame.addView(rulesPanel.root, full)
        frame.addView(warnPanel.root, FrameLayout.LayoutParams(full))
        frame.addView(settingsPanel.root, FrameLayout.LayoutParams(full))

        mainMenu = MainMenu(
            this,
            { !speaker.muted },
            { rulesPanel.show() },
            { speaker.muted = !speaker.muted },
            { settingsPanel.show() }
        )
        frame.addView(mainMenu.root, FrameLayout.LayoutParams(full))

        applyMenuMode()
        applyOverlayPrefs()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    override fun onBackPressed() {
        if (mainMenu.isShowing) mainMenu.hide()
        else if (warnPanel.isShowing) warnPanel.hide()
        else if (settingsPanel.isShowing) settingsPanel.hide()
        else if (rulesPanel.isShowing) rulesPanel.hide()
        else super.onBackPressed()
    }

    // ------------------------------------------------------------------ menu button that hides itself

    /** Any touch on the screen brings the MENU button back for a few seconds. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && !anyOverlayOpen()) showMenuButton(4000)
        return super.dispatchTouchEvent(ev)
    }

    private fun anyOverlayOpen(): Boolean = mainMenu.isShowing || rulesPanel.isShowing || warnPanel.isShowing || settingsPanel.isShowing

    private fun showMenuButton(forMs: Long) {
        menuButton.visibility = View.VISIBLE
        ui.removeCallbacks(hideMenuRunnable)
        if (!Prefs.menuAlways(this)) ui.postDelayed(hideMenuRunnable, forMs)
    }

    private fun applyMenuMode() {
        ui.removeCallbacks(hideMenuRunnable)
        if (Prefs.menuAlways(this)) menuButton.visibility = View.VISIBLE else showMenuButton(6000)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        reloadRules()
        reloadWarnings()
        startSdk()
        if (hasCameraPermission()) camera.start() else requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        ui.post(tick)
    }

    override fun onPause() {
        ui.removeCallbacks(tick)
        camera.stop()
        stopSdk()
        super.onPause()
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) camera.start()
    }

    private fun hasCameraPermission() =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun hideSystemUi() {
        Immersive.apply(this)
    }

    // ------------------------------------------------------------------ radio link

    private fun startSdk() {
        val link = sdkLink ?: return
        sdkError = link.start()
        sdkRunning = sdkError == null
        sdkError?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
    }

    private fun stopSdk() {
        if (sdkRunning) sdkLink?.stop()
        sdkRunning = false
    }

    private fun reloadRules() {
        val parsed = RuleParser.parse(Prefs.rules(this))
        engine = RuleEngine(parsed, speaker)          // a new engine starts silent
        sdkScaler = when (parsed.settings.scale) {
            "percent" -> ChannelScaler(false, ChannelScaler.Mode.PERCENT)
            "permille" -> ChannelScaler(false, ChannelScaler.Mode.PERMILLE)
            "us" -> ChannelScaler(false, ChannelScaler.Mode.MICROS)
            else -> ChannelScaler(true, ChannelScaler.Mode.PERCENT)
        }
        if (parsed.errors.isNotEmpty()) {
            Toast.makeText(this, "Sound rules: " + parsed.errors.size + " error(s). Open the rules editor.", Toast.LENGTH_LONG).show()
        }
    }

    private fun reloadWarnings() {
        val text = Prefs.warningRules(this)
        warningBar.load(text)
        val errors = RuleParser.parse(text).errors
        if (errors.isNotEmpty()) {
            Toast.makeText(this, "Warning rules: " + errors.size + " error(s). Open Settings, then the warning rules.", Toast.LENGTH_LONG).show()
        }
    }

    /** 20 times a second: NO VIDEO panel, read the radio, run the rules, refresh the debug panels. */
    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            if (lastTickAt != 0L) uiTiming.add(now, now - lastTickAt)
            lastTickAt = now

            val stale = now - camera.lastFrameMs > 1000
            noFeed.visibility = if (stale) View.VISIBLE else View.GONE
            if (stale) noFeedDetail.text = "NO VIDEO  \u00B7  " + camera.barText()

            // the MENU button must not sit on top of a panel's own buttons
            if (anyOverlayOpen()) {
                menuButton.visibility = View.GONE
                overlayWasOpen = true
            } else if (overlayWasOpen) {
                overlayWasOpen = false
                if (Prefs.menuAlways(this@MainActivity)) menuButton.visibility = View.VISIBLE else showMenuButton(4000)
            }

            val useFake = testVisible || sdkLink == null || !sdkRunning
            val link: RadioLink = if (useFake) fakeLink else sdkLink!!
            val scaler = if (useFake) fakeScaler else sdkScaler
            val sample = link.poll()
            if (sample != null) {
                if (scaler.observe(sample.raw)) {                    // the radio's unit became clear: start again
                    engine?.reset()
                    reloadWarnings()
                }
                val numbers = Variables.numbers(sample, scaler)
                latestNumbers = numbers
                latestTexts = sample.texts
                engine?.update(numbers, sample.texts, now)
                warningBar.update(numbers, sample.texts, now)
                if (sticksEnabled) sticksView.update(numbers)
                if (testVisible) {
                    val sb = StringBuilder()
                    for (i in 0 until 16) sb.append("ch").append(i + 1).append("=").append(numbers["ch" + (i + 1)]!!.toInt())
                        .append(if (i % 4 == 3) "\n" else "  ")
                    status.text = sb.toString().trim() + "\n" + link.name + ": " + sample.info + "\n" + camera.statusLine() + "\n" + camera.videoStats()
                }
                if (varsVisible && now - lastVarsUpdate > 250) {
                    lastVarsUpdate = now
                    varsText.text = describe(link, sample, numbers, scaler)
                }
            }
            ui.postDelayed(this, 50)
        }
    }

    private fun describe(link: RadioLink, sample: RadioSample, numbers: Map<String, Double>, scaler: ChannelScaler): String {
        val sb = StringBuilder()
        sb.append("source: ").append(link.name)
        if (link !is FakeRadioLink || sdkLink != null) sb.append("   unit: ").append(scaler.mode())
        sb.append('\n')
        if (link is FakeRadioLink) {
            sb.append(
                if (testVisible) "TEST CHANNELS ARE ON - the radio is not being read\n"
                else if (sdkLink == null) "NO SDK IN THIS BUILD - the radio is not being read\n"
                else "SDK IS NOT RUNNING - the radio is not being read\n"
            )
        }
        if (sdkError != null) sb.append("SDK: ").append(sdkError).append('\n')
        sb.append(sample.info).append('\n')
        sb.append(camera.videoStats()).append('\n')
        val ui5 = uiTiming.summary(SystemClock.elapsedRealtime())
        sb.append("app loop (should be about 50 ms): 95% under ").append(ui5.p95Ms).append(" ms, worst ").append(ui5.maxMs).append(" ms\n\n")
        val sorted = TreeMap<String, Double>(numbers)
        for ((k, v) in sorted) {
            if (k.startsWith("ch") || k.startsWith("raw") || k.startsWith("lv")) continue
            sb.append(String.format("%-10s %s\n", k, trimNumber(v)))
        }
        for ((k, v) in TreeMap<String, String>(sample.texts)) sb.append(String.format("%-10s \"%s\"\n", k, v))
        sb.append("\nchannels  raw  ->  us   (logic)\n")
        for (i in 0 until 16) {
            sb.append(String.format("ch%-2d %7s -> %4d   %s\n", i + 1, trimNumber(sample.raw[i]),
                scaler.toMicros(sample.raw[i]).toInt(), trimNumber(sample.logic[i])))
        }
        return sb.toString()
    }

    private fun trimNumber(v: Double): String =
        if (v == Math.rint(v) && Math.abs(v) < 1e9) v.toLong().toString() else String.format("%.2f", v)

    // ------------------------------------------------------------------ menu

    private fun chooseCamera() {
        val list = camera.listCameras()
        if (list.isEmpty()) {
            Toast.makeText(this, "Android lists no cameras", Toast.LENGTH_LONG).show()
            return
        }
        val names = Array(list.size) { list[it].second }
        Immersive.show(
            AlertDialog.Builder(this)
                .setTitle("Choose the HDMI camera")
                .setItems(names) { _, which -> camera.select(list[which].first) }
                .create()
        )
    }

    private fun setTestVisible(on: Boolean) {
        testVisible = on
        testPanel.visibility = if (on) View.VISIBLE else View.GONE
        status.visibility = if (on) View.VISIBLE else View.GONE
        engine?.reset()                                   // the source of the numbers changed
    }

    private fun setVarsVisible(on: Boolean) {
        varsVisible = on
        varsPanel.visibility = if (on) View.VISIBLE else View.GONE
    }

    // ------------------------------------------------------------------ settings and rules files

    private val settingsHost = object : SettingsHost {
        override fun cameraId(): String = camera.cameraId ?: "auto"
        override fun chooseCamera() = this@MainActivity.chooseCamera()
        override fun rotation(): Int = camera.rotation
        override fun rotateVideo() = camera.rotateNext()
        override fun fill(): Boolean = camera.fill
        override fun toggleFill() = camera.toggleFill()
        override fun speechOn(): Boolean = !speaker.muted
        override fun setSpeechOn(on: Boolean) { speaker.muted = !on }
        override fun speakTest() {
            if (speaker.muted) Toast.makeText(this@MainActivity, "Speech is off", Toast.LENGTH_SHORT).show()
            else speaker.say("Sound test", true)
        }
        override fun exportRules() = this@MainActivity.exportRules()
        override fun importRules() = this@MainActivity.importRules()
        override fun hasRulesBackup(): Boolean = Prefs.rulesBackup(this@MainActivity) != null
        override fun restoreRules() = this@MainActivity.restoreRules()
        override fun resetRules() = this@MainActivity.resetRules()
        override fun openRulesText() { startActivity(Intent(this@MainActivity, RulesActivity::class.java)) }
        override fun menuAlways(): Boolean = Prefs.menuAlways(this@MainActivity)
        override fun setMenuAlways(on: Boolean) {
            Prefs.setMenuAlways(this@MainActivity, on)
            applyMenuMode()
        }
        override fun variablesOn(): Boolean = varsVisible
        override fun setVariablesOn(on: Boolean) = setVarsVisible(on)
        override fun testChannelsOn(): Boolean = testVisible
        override fun setTestChannelsOn(on: Boolean) = setTestVisible(on)
        override fun about(): String = this@MainActivity.aboutText()

        override fun sticksOn(): Boolean = sticksEnabled
        override fun setSticksOn(on: Boolean) {
            Prefs.setBool(this@MainActivity, "sticksOn", on)
            applyOverlayPrefs()
        }
        override fun sticksSizeLabel(): String = sizeNames[Prefs.int(this@MainActivity, "sticksSize", 1).coerceIn(0, 2)]
        override fun cycleSticksSize() = cyclePref("sticksSize", 1, 3)
        override fun sticksPositionLabel(): String = positionNames[Prefs.int(this@MainActivity, "sticksPos", 0).coerceIn(0, 4)]
        override fun cycleSticksPosition() = cyclePref("sticksPos", 0, 5)
        override fun sticksOpacityLabel(): String = opacityNames[Prefs.int(this@MainActivity, "sticksOpacity", 1).coerceIn(0, 2)]
        override fun cycleSticksOpacity() = cyclePref("sticksOpacity", 1, 3)
        override fun editStickChannels() = this@MainActivity.editStickChannels()
        override fun warningBarOn(): Boolean = Prefs.bool(this@MainActivity, "warnBarOn", true)
        override fun setWarningBarOn(on: Boolean) {
            Prefs.setBool(this@MainActivity, "warnBarOn", on)
            applyOverlayPrefs()
        }
        override fun warningBarPositionLabel(): String = if (Prefs.int(this@MainActivity, "warnBarPos", 0) == 0) "top" else "bottom"
        override fun cycleWarningBarPosition() = cyclePref("warnBarPos", 0, 2)
        override fun editWarningRules() {
            settingsPanel.hide()
            warnPanel.show()
        }
        override fun previewWarnings() {
            val now = SystemClock.elapsedRealtime()
            warningBar.showTest("BATTERY CRITICAL 3.48 V per cell", 2, now, 4000)
            settingsPanel.hide()
        }
    }

    private val sizeNames = arrayOf("small", "medium", "large")
    private val positionNames = arrayOf("bottom center", "bottom left", "bottom right", "top left", "top right")
    private val opacityNames = arrayOf("35%", "65%", "95%")

    private fun cyclePref(key: String, def: Int, count: Int) {
        Prefs.setInt(this, key, (Prefs.int(this, key, def) + 1) % count)
        applyOverlayPrefs()
    }

    /** Apply the stick view and warning bar settings: on or off, size, place, which channels. */
    private fun applyOverlayPrefs() {
        sticksEnabled = Prefs.bool(this, "sticksOn", false)
        val mode = Prefs.int(this, "stickMode", 2)
        val roll = Prefs.int(this, "stickRoll", 1)
        val pitch = Prefs.int(this, "stickPitch", 2)
        val throttle = Prefs.int(this, "stickThrottle", 3)
        val yaw = Prefs.int(this, "stickYaw", 4)
        val layout = StickLayout.forMode(mode, roll, pitch, throttle, yaw)
        val names = arrayOf(
            StickLayout.axisName(layout, mode, true, true, roll, pitch, throttle, yaw),
            StickLayout.axisName(layout, mode, true, false, roll, pitch, throttle, yaw),
            StickLayout.axisName(layout, mode, false, true, roll, pitch, throttle, yaw),
            StickLayout.axisName(layout, mode, false, false, roll, pitch, throttle, yaw)
        )
        val boxDp = intArrayOf(64, 96, 132)[Prefs.int(this, "sticksSize", 1).coerceIn(0, 2)]
        sticksView.configure(layout, Prefs.int(this, "stickInvert", 0), names, boxDp)
        sticksView.alpha = floatArrayOf(0.35f, 0.65f, 0.95f)[Prefs.int(this, "sticksOpacity", 1).coerceIn(0, 2)]
        sticksView.visibility = if (sticksEnabled) View.VISIBLE else View.GONE

        val pos = Prefs.int(this, "sticksPos", 0).coerceIn(0, 4)
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.gravity = when (pos) {
            1 -> Gravity.BOTTOM or Gravity.START
            2 -> Gravity.BOTTOM or Gravity.END
            3 -> Gravity.TOP or Gravity.START
            4 -> Gravity.TOP or Gravity.END
            else -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        lp.setMargins(dp(10), dp(52), dp(10), dp(48))      // clear of the warning bar and the no-video bar
        sticksView.layoutParams = lp

        warningBar.enabled = Prefs.bool(this, "warnBarOn", true)
        val wlp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (Prefs.int(this, "warnBarPos", 0) == 0) {
            wlp.gravity = Gravity.TOP
            wlp.rightMargin = dp(110)                       // room for the MENU button
        } else {
            wlp.gravity = Gravity.BOTTOM
            wlp.bottomMargin = dp(40)
        }
        warningText.layoutParams = wlp
    }

    /** One dialog for: stick mode (1 to 4), which channel is roll / pitch / throttle / yaw, and direction. */
    private fun editStickChannels() {
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(8), dp(16), dp(8))

        var mode = Prefs.int(this, "stickMode", 2).coerceIn(1, 4)
        val modeNames = arrayOf(
            "Mode 1: left = pitch + yaw, right = throttle + roll",
            "Mode 2: left = throttle + yaw, right = pitch + roll",
            "Mode 3: left = throttle + roll, right = pitch + yaw",
            "Mode 4: left = pitch + roll, right = throttle + yaw"
        )
        val modeButton = Button(this)
        modeButton.isAllCaps = false
        modeButton.text = modeNames[mode - 1]
        modeButton.setOnClickListener {
            mode = mode % 4 + 1
            modeButton.text = modeNames[mode - 1]
        }
        body.addView(modeButton)

        fun field(label: String, value: Int): EditText {
            val t = TextView(this)
            t.text = label
            t.setPadding(0, dp(8), 0, 0)
            body.addView(t)
            val e = EditText(this)
            e.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            e.setText(value.toString())
            body.addView(e)
            return e
        }
        val roll = field("Roll channel (1 to 16)", Prefs.int(this, "stickRoll", 1))
        val pitch = field("Pitch channel", Prefs.int(this, "stickPitch", 2))
        val throttle = field("Throttle channel", Prefs.int(this, "stickThrottle", 3))
        val yaw = field("Yaw channel", Prefs.int(this, "stickYaw", 4))

        val mask = Prefs.int(this, "stickInvert", 0)
        val labels = arrayOf("Left stick up/down drawn reversed", "Left stick left/right drawn reversed",
            "Right stick up/down drawn reversed", "Right stick left/right drawn reversed")
        val flags = intArrayOf(StickLayout.INV_LEFT_V, StickLayout.INV_LEFT_H, StickLayout.INV_RIGHT_V, StickLayout.INV_RIGHT_H)
        val boxes = ArrayList<CheckBox>()
        for (i in 0 until 4) {
            val c = CheckBox(this)
            c.text = labels[i]
            c.isChecked = (mask and flags[i]) != 0
            body.addView(c)
            boxes.add(c)
        }
        val hint = TextView(this)
        hint.text = "The sticks show the channel values the radio sends. Common order: roll 1, pitch 2, throttle 3, yaw 4. " +
                "Turn a direction round if the dot moves the wrong way."
        hint.textSize = 12f
        hint.setPadding(0, dp(8), 0, 0)
        body.addView(hint)

        val scroll = ScrollView(this)
        scroll.addView(body)
        val dlg = AlertDialog.Builder(this)
            .setTitle("Stick view")
            .setView(scroll)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        Immersive.show(dlg)
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val values = listOf(roll, pitch, throttle, yaw).map { it.text.toString().trim().toIntOrNull() }
            if (values.any { it == null || it < 1 || it > 16 }) {
                Toast.makeText(this, "Channels are numbers from 1 to 16", Toast.LENGTH_SHORT).show()
            } else if (values.toSet().size < 4) {
                Toast.makeText(this, "Each of the four needs its own channel", Toast.LENGTH_SHORT).show()
            } else {
                Prefs.setInt(this, "stickMode", mode)
                Prefs.setInt(this, "stickRoll", values[0]!!)
                Prefs.setInt(this, "stickPitch", values[1]!!)
                Prefs.setInt(this, "stickThrottle", values[2]!!)
                Prefs.setInt(this, "stickYaw", values[3]!!)
                var m = 0
                for (i in 0 until 4) if (boxes[i].isChecked) m = m or flags[i]
                Prefs.setInt(this, "stickInvert", m)
                applyOverlayPrefs()
                dlg.dismiss()
            }
        }
    }

    private fun aboutText(): String {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
        val sdk = if (!AxSdkJni.loaded) "Built without the RadioMaster SDK files (test channels only)."
        else if (sdkRunning) "RadioMaster SDK: running."
        else "RadioMaster SDK: not running" + (sdkError?.let { " ($it)" } ?: "") + "."
        return "AX12 Video Monitor and Announcer, version " + version + "\n" + sdk
    }

    private val requestExport = 41
    private val requestImport = 42
    private val fileName = "ax12-sound-rules.txt"

    private fun exportText(): String =
        RulesFile.forExport(Prefs.rules(this), SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))

    private fun exportRules() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TITLE, fileName)
        try {
            startActivityForResult(intent, requestExport)
        } catch (e: ActivityNotFoundException) {
            exportToAppFolder()
        }
    }

    /** For a device without a file picker: write into the app's own folder, reachable over USB. */
    private fun exportToAppFolder() {
        try {
            val dir = getExternalFilesDir(null) ?: filesDir
            val f = File(dir, fileName)
            f.writeText(exportText(), Charsets.UTF_8)
            Immersive.show(
                AlertDialog.Builder(this)
                    .setTitle("Sound rules saved")
                    .setMessage("This device has no file picker, so the file was saved here:\n" + f.absolutePath +
                            "\n\nCopy it with a USB cable or adb. To import, put a file with the same name in that folder.")
                    .setPositiveButton("OK", null)
                    .create()
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Could not save the rules: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun importRules() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        try {
            startActivityForResult(intent, requestImport)
        } catch (e: ActivityNotFoundException) {
            val f = File(getExternalFilesDir(null) ?: filesDir, fileName)
            if (f.exists()) askImport(readTextLimited(f.inputStream())) else {
                Toast.makeText(this, "No file picker, and no " + f.absolutePath + " to import", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == requestExport) {
            try {
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(exportText().toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, "Sound rules saved", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not save the rules: " + e.message, Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == requestImport) {
            try {
                val stream = contentResolver.openInputStream(uri)
                if (stream == null) Toast.makeText(this, "Could not open the file", Toast.LENGTH_LONG).show()
                else askImport(readTextLimited(stream))
            } catch (e: Exception) {
                Toast.makeText(this, "Could not read the file: " + e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun readTextLimited(stream: java.io.InputStream): String {
        stream.use {
            val buf = ByteArray(RulesFile.MAX_CHARS * 2 + 1)
            var n = 0
            while (n < buf.size) {
                val r = it.read(buf, n, buf.size - n)
                if (r < 0) break
                n += r
            }
            return String(buf, 0, n, Charsets.UTF_8)
        }
    }

    private fun askImport(raw: String) {
        val text = RulesFile.normalize(raw)
        val summary = RulesFile.inspect(text)
        if (!summary.usable) {
            Immersive.show(AlertDialog.Builder(this).setTitle("Cannot import").setMessage(summary.message).setPositiveButton("OK", null).create())
            return
        }
        Immersive.show(
            AlertDialog.Builder(this)
            .setTitle("Import sound rules")
            .setMessage("The file has " + summary.message + "\n\nThis replaces your current rules. They are kept as a backup, and Settings can restore them.")
            .setPositiveButton("Import") { _, _ ->
                Prefs.setRulesBackup(this, Prefs.rules(this))
                Prefs.setRules(this, text)
                reloadRules()
                Toast.makeText(this, "Imported " + summary.rules + " rules", Toast.LENGTH_LONG).show()
                settingsPanel.refresh()
            }
            .setNegativeButton("Cancel", null)
            .create()
        )
    }

    private fun restoreRules() {
        val backup = Prefs.rulesBackup(this) ?: return
        val current = Prefs.rules(this)
        Prefs.setRules(this, backup)
        Prefs.setRulesBackup(this, current)
        reloadRules()
    }

    private fun resetRules() {
        Prefs.setRulesBackup(this, Prefs.rules(this))
        Prefs.setRules(this, DefaultRules.TEXT)
        reloadRules()
    }

    private fun buildTestPanel() {
        val list = findViewById<LinearLayout>(R.id.testList)
        for (i in 0 until 16) {
            val label = TextView(this)
            label.setTextColor(Color.WHITE)
            label.textSize = 14f
            label.text = "ch" + (i + 1) + "  1500"
            val bar = SeekBar(this)
            bar.max = 1000
            bar.progress = 500
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val us = 1000 + progress
                    fakeLink.values[i] = us.toDouble()
                    label.text = "ch" + (i + 1) + "  " + us
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            list.addView(label)
            list.addView(bar)
        }
    }
}
