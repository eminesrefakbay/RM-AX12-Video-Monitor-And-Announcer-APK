package dev.ax12.announcer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
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
    private val updater by lazy { Updater(this) }
    private val autoCheck = Runnable { if (Prefs.bool(this, "updateAuto", true)) updater.check(false) }
    private var batteryCells = 6
    private var batteryName = ""
    private var aircraftName = ""
    private var splash: SplashOverlay? = null
    private val splashHide = Runnable { splash?.dismiss() }
    private lateinit var warnPanel: RulesPanel
    private lateinit var checkPanel: RulesPanel
    private lateinit var statsView: FlightStatsView
    private lateinit var radarView: RadarView
    private lateinit var checkView: ChecklistView
    private lateinit var chartView: ChartView
    private val chartModel = ChartModel()
    private var chartOn = false
    private val flightStats = FlightStats()
    private var checkEngine: RuleEngine? = null
    private var checkModel: CheckModel? = null
    private var statsOn = true
    private var radarOn = true
    private var checkOn = true
    private var lastTileUpdate = 0L
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
        speaker.setLanguage(Lang.locale(Lang.speechCode(this)))
        camera = CameraPreview(this, texture)

        buildTestPanel()
        menuButton = findViewById(R.id.menuButton)
        menuButton.text = tr("MENU")
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
        sticksView.onLongPress = { editChannelBars() }
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

        statsView = FlightStatsView(this)
        statsView.visibility = View.GONE
        frame.addView(statsView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        radarView = RadarView(this)
        radarView.bind(flightStats)
        radarView.visibility = View.GONE
        frame.addView(radarView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        chartView = ChartView(this)
        chartView.bind(chartModel)
        chartView.visibility = View.GONE
        frame.addView(chartView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        rootFrame = frame
        layoutEditor = LayoutEditor(this, layoutHost)
        checkView = ChecklistView(this)
        checkView.visibility = View.GONE
        frame.addView(checkView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        frame.addView(layoutEditor, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        for (t in tileNames) watchTileSize(t)

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
            titleText = tr("Warning rules"),
            testWarning = { text, level -> warningBar.showTest(text, level, SystemClock.elapsedRealtime()) }
        )
        checkPanel = RulesPanel(
            this, speaker, liveValues,
            { reloadChecks() },
            { startActivity(Intent(this, RulesActivity::class.java).putExtra("checks", true)) },
            { Prefs.checkRules(this) },
            { Prefs.setCheckRules(this, it) },
            titleText = tr("Pre-flight checks"),
            check = true
        )
        settingsPanel = SettingsPanel(this, settingsHost)
        frame.addView(rulesPanel.root, full)
        frame.addView(warnPanel.root, FrameLayout.LayoutParams(full))
        frame.addView(checkPanel.root, FrameLayout.LayoutParams(full))
        frame.addView(settingsPanel.root, FrameLayout.LayoutParams(full))

        mainMenu = MainMenu(
            this,
            { !speaker.muted },
            { rulesPanel.show() },
            { speaker.muted = !speaker.muted },
            { settingsPanel.show() },
            { batteryName },
            { switchBattery() },
            { aircraftName },
            { chooseAircraft() }
        )
        frame.addView(mainMenu.root, FrameLayout.LayoutParams(full))

        applyMenuMode()
        applyOverlayPrefs()
        applyTiles()
        applyFlightConfig()

        // a fresh start (not a turn of the screen or a return from the background): logo and version for 2 seconds
        if (savedInstanceState == null && Prefs.bool(this, "splashOn", true)) {
            val sp = SplashOverlay(this)
            splash = sp
            frame.addView(sp.root, FrameLayout.LayoutParams(full))
            ui.postDelayed(splashHide, SplashOverlay.SHOW_MS)
            ui.postDelayed(autoCheck, 6000)          // a quiet look for news a few seconds after a fresh start
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    override fun onBackPressed() {
        if (splash?.isShowing == true) splash?.dismiss()
        else if (editingLayout) endLayoutEdit()
        else if (mainMenu.isShowing) mainMenu.hide()
        else if (warnPanel.isShowing) warnPanel.hide()
        else if (checkPanel.isShowing) checkPanel.hide()
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

    private fun anyOverlayOpen(): Boolean = mainMenu.isShowing || rulesPanel.isShowing || warnPanel.isShowing || checkPanel.isShowing || settingsPanel.isShowing || editingLayout

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
        reloadChecks()
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
        ui.removeCallbacks(splashHide)
        ui.removeCallbacks(autoCheck)
        updater.release()
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
        val parsed = RuleParser.parse(Prefs.rules(this), Battery.macros(this))
        engine = RuleEngine(parsed, speaker)          // a new engine starts silent
        sdkScaler = when (parsed.settings.scale) {
            "percent" -> ChannelScaler(false, ChannelScaler.Mode.PERCENT)
            "permille" -> ChannelScaler(false, ChannelScaler.Mode.PERMILLE)
            "us" -> ChannelScaler(false, ChannelScaler.Mode.MICROS)
            else -> ChannelScaler(true, ChannelScaler.Mode.PERCENT)
        }
        if (parsed.errors.isNotEmpty()) {
            Toast.makeText(this, tr("Sound rules: {0} error(s). Open the rules editor.", parsed.errors.size), Toast.LENGTH_LONG).show()
        }
    }

    private fun reloadChecks() {
        val text = Prefs.checkRules(this)
        val parsed = RuleParser.parse(text, Battery.macros(this))
        val model = CheckModel(parsed.rules)
        checkModel = model
        checkEngine = RuleEngine(parsed, null, model)
        if (parsed.errors.isNotEmpty()) {
            Toast.makeText(this, tr("Pre-flight checks: {0} error(s). Open Settings, then the checks.", parsed.errors.size), Toast.LENGTH_LONG).show()
        }
    }

    private fun reloadWarnings() {
        val text = Prefs.warningRules(this)
        val macros = Battery.macros(this)
        warningBar.load(text, macros)
        val errors = RuleParser.parse(text, macros).errors
        if (errors.isNotEmpty()) {
            Toast.makeText(this, tr("Warning rules: {0} error(s). Open Settings, then the warning rules.", errors.size), Toast.LENGTH_LONG).show()
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
            if (stale) noFeedDetail.text = tr("NO VIDEO  \u00B7  {0}", camera.barText())

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
                    reloadChecks()
                }
                val numbers = Variables.numbers(sample, scaler, batteryCells)
                flightStats.update(numbers, now)
                if (flightStats.armed()) {                      // height and distance exist only in flight, and rules can use them
                    if (!flightStats.heightNow().isNaN()) numbers["height"] = flightStats.heightNow()
                    if (!flightStats.distanceNow().isNaN()) numbers["dist"] = flightStats.distanceNow()
                }
                latestNumbers = numbers
                latestTexts = sample.texts
                engine?.update(numbers, sample.texts, now)
                warningBar.update(numbers, sample.texts, now)
                checkEngine?.update(numbers, sample.texts, now)
                if (now - lastTileUpdate >= 200) {
                    lastTileUpdate = now
                    updateTiles(numbers, sample.texts, now)
                }
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
            if (sample == null && editingLayout && now - lastTileUpdate >= 200) {
                lastTileUpdate = now
                updateTiles(latestNumbers, latestTexts, now)
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
                if (testVisible) tr("TEST CHANNELS ARE ON - the radio is not being read\n")
                else if (sdkLink == null) tr("NO SDK IN THIS BUILD - the radio is not being read\n")
                else tr("SDK IS NOT RUNNING - the radio is not being read\n")
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
            Toast.makeText(this, tr("Android lists no cameras"), Toast.LENGTH_LONG).show()
            return
        }
        val names = Array(list.size) { list[it].second }
        Immersive.show(
            AlertDialog.Builder(this)
                .setTitle(tr("Choose the HDMI camera"))
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
        override fun cameraId(): String = camera.cameraId ?: tr("auto")
        override fun chooseCamera() = this@MainActivity.chooseCamera()
        override fun rotation(): Int = camera.rotation
        override fun rotateVideo() = camera.rotateNext()
        override fun fill(): Boolean = camera.fill
        override fun toggleFill() = camera.toggleFill()
        override fun speechOn(): Boolean = !speaker.muted
        override fun setSpeechOn(on: Boolean) { speaker.muted = !on }
        override fun speakTest() {
            if (speaker.muted) Toast.makeText(this@MainActivity, tr("Speech is off"), Toast.LENGTH_SHORT).show()
            else if (!speaker.languageOk()) Toast.makeText(this@MainActivity, voiceMissingText(), Toast.LENGTH_LONG).show()
            else speaker.say(I18n.tIn(Lang.speechCode(this@MainActivity), "Sound test"), true)
        }

        override fun languageLabel(): String {
            val pref = Prefs.language(this@MainActivity)
            return if (pref == "auto") tr("Automatic ({0})", Lang.nativeName(I18n.language())) else Lang.nativeName(pref)
        }
        override fun cycleLanguage() {
            val i = Lang.choices.indexOf(Prefs.language(this@MainActivity)).coerceAtLeast(0)
            Prefs.setLanguage(this@MainActivity, Lang.choices[(i + 1) % Lang.choices.size])
            Lang.apply(this@MainActivity)
            this@MainActivity.recreate()          // every screen is built again in the new language
        }
        override fun speechLanguageLabel(): String {
            val p = Prefs.speechLanguage(this@MainActivity)
            return if (p == "same" || !I18n.isSupported(p)) tr("Same as the app ({0})", Lang.nativeName(I18n.language())) else Lang.nativeName(p)
        }
        override fun cycleSpeechLanguage() {
            val order = arrayOf("same", "en", "tr", "de", "zh")
            val i = order.indexOf(Prefs.speechLanguage(this@MainActivity)).coerceAtLeast(0)
            Prefs.setSpeechLanguage(this@MainActivity, order[(i + 1) % order.size])
            speaker.setLanguage(Lang.locale(Lang.speechCode(this@MainActivity)))
            Toast.makeText(this@MainActivity, if (speaker.languageOk()) tr("The voice now speaks {0}.", Lang.nativeName(Lang.speechCode(this@MainActivity))) else voiceMissingText(), Toast.LENGTH_LONG).show()
        }
        override fun exportRules() = this@MainActivity.exportRules()
        override fun importRules() = this@MainActivity.importRules()
        override fun hasRulesBackup(): Boolean = Prefs.rulesBackup(this@MainActivity) != null
        override fun restoreRules() = this@MainActivity.restoreRules()
        override fun resetRules() = this@MainActivity.resetRules()
        override fun openRulesText() { startActivity(Intent(this@MainActivity, RulesActivity::class.java)) }
        override fun aircraftName(): String = this@MainActivity.aircraftName
        override fun chooseAircraft() = this@MainActivity.chooseAircraft()
        override fun manageAircraft() = this@MainActivity.manageAircraft()
        override fun editPacks() = this@MainActivity.editPacks()
        override fun batteryLabel(): String = batteryName
        override fun cycleBattery() = switchBattery()
        override fun editBatteries() = this@MainActivity.editBatteries()
        override fun resetWarningRules() {
            Prefs.setWarningRules(this@MainActivity, DefaultWarnings.text())
            reloadWarnings()
        }
        override fun resetCheckRules() {
            Prefs.setCheckRules(this@MainActivity, DefaultChecks.text())
            reloadChecks()
        }
        override fun exportSettings() = this@MainActivity.exportSettings()
        override fun importSettings() = this@MainActivity.importSettings()
        override fun hasSettingsBackup(): Boolean = Prefs.settingsBackup(this@MainActivity) != null
        override fun restoreSettings() = this@MainActivity.restoreSettings()
        override fun updateLine(): String = tr("Installed: version {0} (build {1})   Channel: {2}", updater.installedName(), updater.installedCode(), (if (updater.channel() == "stable") tr("Stable") else tr("Nightly")))
        override fun checkUpdatesNow() {
            settingsPanel.hide()
            updater.check(true)
        }
        override fun updateChannelLabel(): String = if (updater.channel() == "stable") tr("Stable (tested releases only)") else tr("Nightly (newest build of every push)")
        override fun cycleUpdateChannel() {
            Prefs.setUpdateChannel(this@MainActivity, if (updater.channel() == "stable") "nightly" else "stable")
        }
        override fun updateAuto(): Boolean = Prefs.bool(this@MainActivity, "updateAuto", true)
        override fun setUpdateAuto(on: Boolean) { Prefs.setBool(this@MainActivity, "updateAuto", on) }
        override fun splashOn(): Boolean = Prefs.bool(this@MainActivity, "splashOn", true)
        override fun setSplashOn(on: Boolean) { Prefs.setBool(this@MainActivity, "splashOn", on) }
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

        override fun tileOn(tile: String): Boolean = Prefs.bool(this@MainActivity, tile + "On", tile != "chart")
        override fun setTileOn(tile: String, on: Boolean) {
            Prefs.setBool(this@MainActivity, tile + "On", on)
            applyTiles()
        }
        override fun tileSizeLabel(tile: String): String = sizeNames[Prefs.int(this@MainActivity, tile + "Size", 1).coerceIn(0, 2)]
        override fun cycleTileSize(tile: String) = cycleTile(tile + "Size", 1, 3)
        override fun tilePositionLabel(tile: String): String = tilePositionNames[Prefs.int(this@MainActivity, tile + "Pos", defaultTilePosition(tile)).coerceIn(0, 4)]
        override fun cycleTilePosition(tile: String) = cycleTile(tile + "Pos", defaultTilePosition(tile), 5)
        override fun tileOpacityLabel(tile: String): String = tileOpacityNames[Prefs.int(this@MainActivity, tile + "Opacity", 1).coerceIn(0, 2)]
        override fun cycleTileOpacity(tile: String) = cycleTile(tile + "Opacity", 1, 3)
        override fun editCharts() = this@MainActivity.editCharts()
        override fun editLayout() {
            settingsPanel.hide()
            startLayoutEdit()
        }
        override fun resetLayout() = this@MainActivity.resetLayout()
        override fun editArmDetection() = this@MainActivity.editArmDetection()
        override fun editStatsRows() = this@MainActivity.editStatsRows()
        override fun editCheckRules() {
            settingsPanel.hide()
            checkPanel.show()
        }

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
        override fun channelBarsOn(): Boolean = Prefs.bool(this@MainActivity, "barsOn", true)
        override fun setChannelBarsOn(on: Boolean) {
            Prefs.setBool(this@MainActivity, "barsOn", on)
            applyOverlayPrefs()
        }
        override fun editChannelBars() = this@MainActivity.editChannelBars()
        override fun warningBarOn(): Boolean = Prefs.bool(this@MainActivity, "warnBarOn", true)
        override fun setWarningBarOn(on: Boolean) {
            Prefs.setBool(this@MainActivity, "warnBarOn", on)
            applyOverlayPrefs()
        }
        override fun warningBarPositionLabel(): String = if (Prefs.int(this@MainActivity, "warnBarPos", 0) == 0) tr("top") else tr("bottom")
        override fun cycleWarningBarPosition() = cyclePref("warnBarPos", 0, 2)
        override fun editWarningRules() {
            settingsPanel.hide()
            warnPanel.show()
        }
        override fun previewWarnings() {
            val now = SystemClock.elapsedRealtime()
            warningBar.showTest(tr("BATTERY CRITICAL 3.48 V per cell"), 2, now, 4000)
            settingsPanel.hide()
        }
    }

    private val sizeNames = arrayOf(tr("small"), tr("medium"), tr("large"))
    private val positionNames = arrayOf(tr("bottom center"), tr("bottom left"), tr("bottom right"), tr("top left"), tr("top right"))
    private val opacityNames = arrayOf("35%", "65%", "95%")

    private fun cyclePref(key: String, def: Int, count: Int) {
        Prefs.setInt(this, key, (Prefs.int(this, key, def) + 1) % count)
        applyOverlayPrefs()
    }

    // ------------------------------------------------------------------ flight overlays

    private val tilePositionNames = arrayOf(tr("top left"), tr("top right"), tr("bottom left"), tr("bottom right"), tr("center"))
    private val tileOpacityNames = arrayOf("50%", "75%", "100%")

    private fun defaultTilePosition(tile: String): Int = when (tile) {
        "stats" -> 0
        "radar" -> 3
        "chart" -> 1
        else -> 4
    }

    private fun cycleTile(key: String, def: Int, count: Int) {
        Prefs.setInt(this, key, (Prefs.int(this, key, def) + 1) % count)
        applyTiles()
    }

    private fun placeTile(v: View, pos: Int) {
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.gravity = when (pos) {
            0 -> Gravity.TOP or Gravity.START
            1 -> Gravity.TOP or Gravity.END
            2 -> Gravity.BOTTOM or Gravity.START
            3 -> Gravity.BOTTOM or Gravity.END
            else -> Gravity.CENTER
        }
        lp.setMargins(dp(10), dp(52), dp(10), dp(48))
        v.layoutParams = lp
    }

    /** Size, place, opacity and on/off of the statistics card, the radar and the checklist. */
    private fun applyTiles() {
        statsOn = Prefs.bool(this, "statsOn", true)
        radarOn = Prefs.bool(this, "radarOn", true)
        checkOn = Prefs.bool(this, "checkOn", true)
        chartOn = Prefs.bool(this, "chartOn", false)
        applyCharts()
        chartView.setLevel(Prefs.int(this, "chartSize", 1).coerceIn(0, 2))
        chartView.alpha = floatArrayOf(0.5f, 0.75f, 1f)[Prefs.int(this, "chartOpacity", 1).coerceIn(0, 2)]
        layoutTile(chartView, "chart") { placeTile(chartView, Prefs.int(this, "chartPos", defaultTilePosition("chart")).coerceIn(0, 4)) }
        val alpha = floatArrayOf(0.5f, 0.75f, 1f)
        statsView.setTextSp(floatArrayOf(12f, 15f, 19f)[Prefs.int(this, "statsSize", 1).coerceIn(0, 2)])
        statsView.alpha = alpha[Prefs.int(this, "statsOpacity", 1).coerceIn(0, 2)]
        layoutTile(statsView, "stats") { placeTile(statsView, Prefs.int(this, "statsPos", defaultTilePosition("stats")).coerceIn(0, 4)) }
        radarView.setSizeDp(intArrayOf(110, 150, 210)[Prefs.int(this, "radarSize", 1).coerceIn(0, 2)])
        radarView.alpha = alpha[Prefs.int(this, "radarOpacity", 1).coerceIn(0, 2)]
        layoutTile(radarView, "radar") { placeTile(radarView, Prefs.int(this, "radarPos", defaultTilePosition("radar")).coerceIn(0, 4)) }
        checkView.setTextSp(floatArrayOf(13f, 16f, 20f)[Prefs.int(this, "checkSize", 1).coerceIn(0, 2)])
        checkView.alpha = alpha[Prefs.int(this, "checkOpacity", 1).coerceIn(0, 2)]
        layoutTile(checkView, "check") { placeTile(checkView, Prefs.int(this, "checkPos", defaultTilePosition("check")).coerceIn(0, 4)) }
    }

    private fun applyFlightConfig() {
        val profile = Battery.active(this)
        batteryCells = profile.cells
        batteryName = Battery.label(this)
        aircraftName = Aircraft.activeName(this)
        flightStats.configure(
            Prefs.int(this, "armCh", 5).coerceIn(1, 16),
            Prefs.int(this, "armHigh", 1750).toDouble(),
            Prefs.int(this, "armLow", 1650).toDouble(),
            batteryCells
        )
    }

    private fun num0(v: Double): String = if (v.isNaN()) "-" else Math.round(v).toString()
    private fun num1(v: Double): String = if (v.isNaN()) "-" else String.format(Locale.US, "%.1f", v)
    private fun clock(ms: Long): String = String.format(Locale.US, "%d:%02d", ms / 60000, (ms / 1000) % 60)

    /** Refresh the three cards (about five times a second). */
    private fun updateTiles(numbers: Map<String, Double>, texts: Map<String, String>, now: Long) {
        chartModel.sample(numbers, now)
        val showChart = chartOn && chartModel.names.isNotEmpty()
        chartView.visibility = if (showChart) View.VISIBLE else View.GONE
        if (showChart) chartView.refresh(now)
        val fs = flightStats
        val flight = fs.armed() || fs.hasFlight() || editingLayout

        val showStats = statsOn && flight
        statsView.visibility = if (showStats) View.VISIBLE else View.GONE
        if (showStats) {
            val rows = Prefs.int(this, "statsRows", 7)
            val lines = ArrayList<FlightStatsView.Line>()
            if (rows and 1 != 0) lines.add(FlightStatsView.Line(tr("SPEED"), num0(fs.speedNow()) + " / " + num0(fs.maxSpeed()) + " km/h"))
            if (rows and 2 != 0) lines.add(FlightStatsView.Line(tr("HEIGHT"), num0(fs.heightNow()) + " / " + num0(fs.maxHeight()) + " m"))
            if (rows and 4 != 0) {
                val limit = Prefs.int(this, "currentLimit", 50).toDouble()
                val scale = maxOf(limit * 1.2, if (fs.peakCurrent().isNaN()) 0.0 else fs.peakCurrent() * 1.05, 20.0)
                lines.add(FlightStatsView.Line(tr("CURRENT"), num1(fs.currentNow()) + " / " + num1(fs.peakCurrent()) + " A",
                    if (fs.currentNow().isNaN()) Double.NaN else fs.currentNow() / scale,
                    if (fs.peakCurrent().isNaN()) Double.NaN else fs.peakCurrent() / scale,
                    limit / scale))
            }
            if (rows and 8 != 0) lines.add(FlightStatsView.Line(tr("DISTANCE"), num0(fs.distanceNow()) + " / " + num0(fs.maxDistance()) + " m"))
            if (rows and 16 != 0) lines.add(FlightStatsView.Line(tr("MIN LINK"), num0(fs.minLinkQuality()) + " %"))
            if (rows and 32 != 0) lines.add(FlightStatsView.Line(tr("MIN CELL"), if (fs.minCellVolts().isNaN()) "-" else String.format(Locale.US, "%.2f V", fs.minCellVolts())))
            statsView.setContent((if (fs.armed()) tr("FLIGHT  ") else tr("LAST FLIGHT  ")) + clock(fs.armedMs(now)), lines)
        }

        val showRadar = radarOn && (fs.hasHome() || editingLayout)
        radarView.visibility = if (showRadar) View.VISIBLE else View.GONE
        if (showRadar) radarView.invalidate()

        val cm = checkModel
        val showCheck = checkOn && cm != null && (!fs.armed() || editingLayout) && (!cm.allUnknown() || editingLayout)
        checkView.visibility = if (showCheck) View.VISIBLE else View.GONE
        if (showCheck && cm != null) {
            val rows = ArrayList<ChecklistView.Row>()
            for (it in cm.items(numbers, texts)) {
                rows.add(ChecklistView.Row(it.label, when (it.state) {
                    CheckModel.State.PASS -> 1
                    CheckModel.State.FAIL -> 2
                    else -> 0
                }))
            }
            val footer: String
            val color: Int
            if (cm.ready()) {
                footer = tr("READY")
                color = 0xFF66BB6A.toInt()
            } else if (cm.fail() > 0) {
                footer = if (cm.fail() == 1) tr("NOT READY: 1 check failed") else tr("NOT READY: {0} checks failed", cm.fail())
                color = 0xFFEF5350.toInt()
            } else {
                footer = tr("WAITING FOR DATA")
                color = 0xFFB0BEC5.toInt()
            }
            checkView.setContent(tr("PRE-FLIGHT CHECK"), aircraftName + "  \u00B7  " + batteryName, rows, footer, color)
        }
    }

    /** Which channel is the arm switch, and when it counts as armed (it also needs the cell count for volts per cell). */
    private fun editArmDetection() {
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(8), dp(16), dp(8))
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
        val ch = field(tr("Arm switch channel (1 to 16, AUX1 is channel 5)"), Prefs.int(this, "armCh", 5))
        val high = field(tr("Counts as armed at or above (microseconds)"), Prefs.int(this, "armHigh", 1750))
        val low = field(tr("Counts as disarmed below (microseconds)"), Prefs.int(this, "armLow", 1650))
        val hint = TextView(this)
        hint.text = tr("The gap between the two thresholds stops a bump of the switch from arming or disarming the statistics. The switch must stay put for 0.3 seconds.")
        hint.textSize = 12f
        hint.setPadding(0, dp(8), 0, 0)
        body.addView(hint)
        val scroll = ScrollView(this)
        scroll.addView(body)
        val dlg = AlertDialog.Builder(this)
            .setTitle(tr("Arm detection"))
            .setView(scroll)
            .setPositiveButton(tr("Save"), null)
            .setNegativeButton(tr("Cancel"), null)
            .create()
        Immersive.show(dlg)
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val c = ch.text.toString().trim().toIntOrNull()
            val h = high.text.toString().trim().toIntOrNull()
            val l = low.text.toString().trim().toIntOrNull()
            if (c == null || c < 1 || c > 16) Toast.makeText(this, tr("The channel is 1 to 16"), Toast.LENGTH_SHORT).show()
            else if (h == null || l == null || h < 1000 || h > 2100 || l < 900 || l > h) Toast.makeText(this, tr("Use microseconds, with the disarmed value not above the armed one"), Toast.LENGTH_LONG).show()
            else {
                Prefs.setInt(this, "armCh", c)
                Prefs.setInt(this, "armHigh", h)
                Prefs.setInt(this, "armLow", l)
                applyFlightConfig()
                dlg.dismiss()
            }
        }
    }

    /** Which rows the statistics card shows, and the current line to stay under. */
    private fun editStatsRows() {
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(8), dp(16), dp(8))
        val names = arrayOf(tr("Speed (now / maximum)"), tr("Height above the arming point (now / maximum)"), tr("Current (now / peak, with a limit line)"),
            tr("Distance from home (now / maximum)"), tr("Lowest link quality"), tr("Lowest volts per cell"))
        val mask = Prefs.int(this, "statsRows", 7)
        val boxes = ArrayList<CheckBox>()
        for (i in names.indices) {
            val c = CheckBox(this)
            c.text = names[i]
            c.isChecked = (mask and (1 shl i)) != 0
            body.addView(c)
            boxes.add(c)
        }
        val t = TextView(this)
        t.text = tr("Current limit line (amps)")
        t.setPadding(0, dp(10), 0, 0)
        body.addView(t)
        val limit = EditText(this)
        limit.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        limit.setText(Prefs.int(this, "currentLimit", 50).toString())
        body.addView(limit)
        val hint = TextView(this)
        hint.text = tr("Height comes from the GPS altitude relative to where you armed, so expect a few metres of error. The bar turns red when the current is above the limit line.")
        hint.textSize = 12f
        hint.setPadding(0, dp(8), 0, 0)
        body.addView(hint)
        val scroll = ScrollView(this)
        scroll.addView(body)
        val dlg = AlertDialog.Builder(this)
            .setTitle(tr("Flight statistics rows"))
            .setView(scroll)
            .setPositiveButton(tr("Save"), null)
            .setNegativeButton(tr("Cancel"), null)
            .create()
        Immersive.show(dlg)
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val a = limit.text.toString().trim().toIntOrNull()
            if (a == null || a < 5 || a > 300) {
                Toast.makeText(this, tr("The current limit is 5 to 300 amps"), Toast.LENGTH_SHORT).show()
            } else {
                var m = 0
                for (i in names.indices) if (boxes[i].isChecked) m = m or (1 shl i)
                Prefs.setInt(this, "statsRows", m)
                Prefs.setInt(this, "currentLimit", a)
                dlg.dismiss()
            }
        }
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
        sticksView.setBars(AuxBars.decode(Prefs.barsConfig(this)), Prefs.bool(this, "barsOn", true))
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
        layoutTile(sticksView, "sticks") { sticksView.layoutParams = lp }

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

    /** Eight bars, four on each side of the sticks: on or off, which channel, and the name shown on the overlay. */
    private fun editChannelBars() {
        val bars = AuxBars.decode(Prefs.barsConfig(this))
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(8), dp(16), dp(8))

        val boxes = ArrayList<CheckBox>()
        val chans = ArrayList<EditText>()
        val nameFields = ArrayList<EditText>()
        val reverseBoxes = ArrayList<CheckBox>()
        for (i in 0 until AuxBars.SLOTS) {
            if (i == 0 || i == 4) {
                val head = TextView(this)
                head.text = if (i == 0) tr("Left of the sticks") else tr("Right of the sticks")
                head.textSize = 14f
                head.setTypeface(null, Typeface.BOLD)
                head.setTextColor(0xFF9CDCFE.toInt())
                head.setPadding(0, dp(12), 0, dp(2))
                body.addView(head)
                val cols = TextView(this)
                cols.text = tr("on      channel      name on the overlay                 reverse")
                cols.textSize = 11f
                body.addView(cols)
            }
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val on = CheckBox(this)
            on.isChecked = bars[i].enabled
            row.addView(on)
            val ch = EditText(this)
            ch.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            ch.setText(bars[i].channel.toString())
            ch.setSingleLine(true)
            row.addView(ch, LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT))
            val name = EditText(this)
            name.setText(bars[i].name)
            name.setSingleLine(true)
            name.filters = arrayOf(android.text.InputFilter.LengthFilter(AuxBars.MAX_NAME))
            row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val rev = CheckBox(this)
            rev.isChecked = bars[i].invert
            row.addView(rev)
            body.addView(row)
            reverseBoxes.add(rev)
            boxes.add(on)
            chans.add(ch)
            nameFields.add(name)
        }
        val hint = TextView(this)
        hint.text = tr("A bar fills with the channel value. Grey-blue = low, amber = middle, green = high. Names are written along the bar; a short name reads best.\n\nReverse: tick it for a switch that is reversed in the radio, so the bar shows the switch the way you flip it (up = full and green). Only the picture changes: sound and warning rules still use the real channel values.")
        hint.textSize = 12f
        hint.setPadding(0, dp(10), 0, 0)
        body.addView(hint)

        val scroll = ScrollView(this)
        scroll.addView(body)
        val dlg = AlertDialog.Builder(this)
            .setTitle(tr("Channel bars"))
            .setView(scroll)
            .setPositiveButton(tr("Save"), null)
            .setNegativeButton(tr("Cancel"), null)
            .setNeutralButton(tr("Defaults"), null)
            .create()
        Immersive.show(dlg)
        dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val d = AuxBars.defaults()
            for (i in 0 until AuxBars.SLOTS) {
                boxes[i].isChecked = d[i].enabled
                chans[i].setText(d[i].channel.toString())
                nameFields[i].setText(d[i].name)
                reverseBoxes[i].isChecked = false
            }
        }
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val list = ArrayList<AuxBars.Bar>()
            var bad: String? = null
            for (i in 0 until AuxBars.SLOTS) {
                val ch = chans[i].text.toString().trim().toIntOrNull()
                if (ch == null || ch < 1 || ch > 16) {
                    bad = tr("Bar {0}: the channel must be 1 to 16", (i + 1))
                    break
                }
                list.add(AuxBars.Bar(boxes[i].isChecked, ch, AuxBars.cleanName(nameFields[i].text.toString(), ch), reverseBoxes[i].isChecked))
            }
            if (bad != null) {
                Toast.makeText(this, bad, Toast.LENGTH_SHORT).show()
            } else {
                Prefs.setBarsConfig(this, AuxBars.encode(list))
                applyOverlayPrefs()
                dlg.dismiss()
            }
        }
    }

    /** One dialog for: stick mode (1 to 4), which channel is roll / pitch / throttle / yaw, and direction. */
    private fun editStickChannels() {
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(8), dp(16), dp(8))

        var mode = Prefs.int(this, "stickMode", 2).coerceIn(1, 4)
        val modeNames = arrayOf(
            tr("Mode 1: left = pitch + yaw, right = throttle + roll"),
            tr("Mode 2: left = throttle + yaw, right = pitch + roll"),
            tr("Mode 3: left = throttle + roll, right = pitch + yaw"),
            tr("Mode 4: left = pitch + roll, right = throttle + yaw")
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
        val roll = field(tr("Roll channel (1 to 16)"), Prefs.int(this, "stickRoll", 1))
        val pitch = field(tr("Pitch channel"), Prefs.int(this, "stickPitch", 2))
        val throttle = field(tr("Throttle channel"), Prefs.int(this, "stickThrottle", 3))
        val yaw = field(tr("Yaw channel"), Prefs.int(this, "stickYaw", 4))

        val mask = Prefs.int(this, "stickInvert", 0)
        val labels = arrayOf(tr("Left stick up/down drawn reversed"), tr("Left stick left/right drawn reversed"),
            tr("Right stick up/down drawn reversed"), tr("Right stick left/right drawn reversed"))
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
        hint.text = tr("The sticks show the channel values the radio sends. Common order: roll 1, pitch 2, throttle 3, yaw 4. Turn a direction round if the dot moves the wrong way.")
        hint.textSize = 12f
        hint.setPadding(0, dp(8), 0, 0)
        body.addView(hint)

        val scroll = ScrollView(this)
        scroll.addView(body)
        val dlg = AlertDialog.Builder(this)
            .setTitle(tr("Stick view"))
            .setView(scroll)
            .setPositiveButton(tr("Save"), null)
            .setNegativeButton(tr("Cancel"), null)
            .create()
        Immersive.show(dlg)
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val values = listOf(roll, pitch, throttle, yaw).map { it.text.toString().trim().toIntOrNull() }
            if (values.any { it == null || it < 1 || it > 16 }) {
                Toast.makeText(this, tr("Channels are numbers from 1 to 16"), Toast.LENGTH_SHORT).show()
            } else if (values.toSet().size < 4) {
                Toast.makeText(this, tr("Each of the four needs its own channel"), Toast.LENGTH_SHORT).show()
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

    private fun voiceMissingText(): String =
        tr("Android has no {0} voice installed. Install it in Android Settings, Text-to-speech output.", Lang.nativeName(Lang.speechCode(this)))

    private fun aboutText(): String {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
        val sdk = if (!AxSdkJni.loaded) tr("Built without the RadioMaster SDK files (test channels only).")
        else if (sdkRunning) tr("RadioMaster SDK: running.")
        else tr("RadioMaster SDK: not running{0}.", (sdkError?.let { " ($it)" } ?: ""))
        return tr("AX12 Video Monitor and Announcer, version {0}\n{1}", version, sdk) + "\n" + tr("Thanks to RadioMaster for publishing the AX SDK.")
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
                    .setTitle(tr("Sound rules saved"))
                    .setMessage(tr("This device has no file picker, so the file was saved here:\n{0}\n\nCopy it with a USB cable or adb. To import, put a file with the same name in that folder.", f.absolutePath))
                    .setPositiveButton("OK", null)
                    .create()
            )
        } catch (e: Exception) {
            Toast.makeText(this, tr("Could not save the rules: {0}", e.message), Toast.LENGTH_LONG).show()
        }
    }

    // ------------------------------------------------------------------ all settings in one file

    private val requestExportSettings = 43
    private val requestImportSettings = 44
    private val settingsFileName = "ax-hud-settings.txt"

    private fun settingsText(): String =
        SettingsFile.export(Prefs.exportMap(this), SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()), updater.installedName())

    /** For a device without a file picker: the app's own folder, reachable over USB. */
    private fun saveToAppFolder(name: String, text: String, title: String) {
        try {
            val f = File(getExternalFilesDir(null) ?: filesDir, name)
            f.writeText(text, Charsets.UTF_8)
            Immersive.show(
                AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(tr("This device has no file picker, so the file was saved here:\n{0}\n\nCopy it with a USB cable or adb. To import, put a file with the same name in that folder.", f.absolutePath))
                    .setPositiveButton("OK", null)
                    .create()
            )
        } catch (e: Exception) {
            Toast.makeText(this, tr("Could not save the settings: {0}", e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun exportSettings() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TITLE, settingsFileName)
        try {
            startActivityForResult(intent, requestExportSettings)
        } catch (e: ActivityNotFoundException) {
            saveToAppFolder(settingsFileName, settingsText(), tr("Settings saved"))
        }
    }

    private fun writeSettings(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(settingsText().toByteArray(Charsets.UTF_8)) }
            Toast.makeText(this, tr("Settings saved"), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, tr("Could not save the settings: {0}", e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun importSettings() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        try {
            startActivityForResult(intent, requestImportSettings)
        } catch (e: ActivityNotFoundException) {
            val f = File(getExternalFilesDir(null) ?: filesDir, settingsFileName)
            if (f.exists()) askImportSettings(readTextLimited(f.inputStream())) else {
                Toast.makeText(this, tr("No file picker, and no {0} to import", f.absolutePath), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun readSettings(uri: Uri) {
        try {
            val stream = contentResolver.openInputStream(uri)
            if (stream == null) Toast.makeText(this, tr("Could not open the file"), Toast.LENGTH_LONG).show()
            else askImportSettings(readTextLimited(stream))
        } catch (e: Exception) {
            Toast.makeText(this, tr("Could not read the file: {0}", e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun askImportSettings(raw: String) {
        val summary = SettingsFile.inspect(raw)
        if (!summary.usable) {
            Immersive.show(AlertDialog.Builder(this).setTitle(tr("Cannot import")).setMessage(summary.message).setPositiveButton("OK", null).create())
            return
        }
        Immersive.show(
            AlertDialog.Builder(this)
                .setTitle(tr("Import all settings"))
                .setMessage(tr("The file has {0}\n\nThis replaces the settings it contains. Your current settings are kept as a backup, and Settings can restore them.", summary.message))
                .setPositiveButton(tr("Import")) { _, _ ->
                    Prefs.setSettingsBackup(this, settingsText())          // the state before, for "restore"
                    Prefs.applyAll(this, summary.values)
                    afterSettingsChanged(tr("Imported {0} settings", summary.entries))
                }
                .setNegativeButton(tr("Cancel"), null)
                .create()
        )
    }

    /** Swaps the current settings with the backup (press again to swap forward). */
    private fun restoreSettings() {
        val backup = Prefs.settingsBackup(this) ?: return
        val summary = SettingsFile.inspect(backup)
        if (!summary.usable) return
        val current = settingsText()
        Prefs.applyAll(this, summary.values)
        Prefs.setSettingsBackup(this, current)
        afterSettingsChanged(tr("Settings restored"))
    }

    private fun afterSettingsChanged(message: String) {
        Lang.apply(this)                      // the language may have changed
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        recreate()                            // every screen is built again with the new settings
    }

    // ------------------------------------------------------------------ moving and resizing the cards

    private var editingLayout = false
    private lateinit var layoutEditor: LayoutEditor
    private lateinit var rootFrame: FrameLayout

    private val tileNames = arrayOf("chart", "radar", "stats", "check", "sticks")      // bottom to top

    private fun tileView(name: String): View = when (name) {
        "sticks" -> sticksView
        "stats" -> statsView
        "radar" -> radarView
        "check" -> checkView
        else -> chartView
    }

    private fun tileLabel(name: String): String = when (name) {
        "sticks" -> tr("Sticks")
        "stats" -> tr("Statistics")
        "radar" -> tr("Radar")
        "check" -> tr("Checklist")
        else -> tr("Charts")
    }

    /** True once the user has moved a card: it then has a place of its own and no longer sits in a corner. */
    private fun isFree(tile: String): Boolean = Prefs.int(this, tile + "X", -1) >= 0 && Prefs.int(this, tile + "Y", -1) >= 0

    /**
     * Places a card: in its corner (the settings' position) until the user moves it, afterwards where the user put it and at the size
     * the user chose. The place is a fraction of the free space, so it is right on any screen.
     */
    private fun layoutTile(view: View, tile: String, preset: () -> Unit) {
        if (!isFree(tile)) {
            view.scaleX = 1f
            view.scaleY = 1f
            view.translationX = 0f
            view.translationY = 0f
            preset()
            return
        }
        view.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START)
        applyFreePosition(view, tile)
    }

    private fun applyFreePosition(view: View, tile: String) {
        if (!isFree(tile) || view.width <= 0 || view.height <= 0 || rootFrame.width <= 0) return
        val r = TileLayout.rect(
            rootFrame.width.toDouble(), rootFrame.height.toDouble(), view.width.toDouble(), view.height.toDouble(),
            Prefs.int(this, tile + "X", 0), Prefs.int(this, tile + "Y", 0), TileLayout.fromPercent(Prefs.int(this, tile + "Scale", 100))
        )
        view.pivotX = 0f
        view.pivotY = 0f
        view.scaleX = (r.width / view.width).toFloat()
        view.scaleY = (r.height / view.height).toFloat()
        view.translationX = r.left.toFloat()           // a free card sits at 0,0 in its parent: its offset is its place
        view.translationY = r.top.toFloat()
    }

    /** Keeps a card where it was put when its own size changes (a statistics row appears, a chart is added). */
    private fun watchTileSize(tile: String) {
        tileView(tile).addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (!layoutEditor.dragging) applyFreePosition(v, tile)
        }
    }

    private val layoutHost = object : LayoutEditor.Host {
        override fun tiles(): List<LayoutEditor.Tile> =
            tileNames.filter { tileView(it).visibility == View.VISIBLE && tileView(it).width > 0 }.map { LayoutEditor.Tile(it, tileLabel(it), tileView(it)) }

        override fun isFree(tile: LayoutEditor.Tile): Boolean = this@MainActivity.isFree(tile.name)

        override fun enterFree(tile: LayoutEditor.Tile) {
            if (isFree(tile.name)) return
            val v = tile.view
            val f = TileLayout.fractions(rootFrame.width.toDouble(), rootFrame.height.toDouble(), v.width.toDouble(), v.height.toDouble(), 1.0,
                (v.left + v.translationX).toDouble(), (v.top + v.translationY).toDouble())
            Prefs.setInt(this@MainActivity, tile.name + "X", f[0])
            Prefs.setInt(this@MainActivity, tile.name + "Y", f[1])
            Prefs.setInt(this@MainActivity, tile.name + "Scale", 100)
            v.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START)
            applyFreePosition(v, tile.name)
        }

        override fun placed(tile: LayoutEditor.Tile, fx: Int, fy: Int, percent: Int) {
            Prefs.setInt(this@MainActivity, tile.name + "X", fx)
            Prefs.setInt(this@MainActivity, tile.name + "Y", fy)
            Prefs.setInt(this@MainActivity, tile.name + "Scale", percent)
            applyFreePosition(tile.view, tile.name)
        }

        override fun done() = endLayoutEdit()

        override fun reset() {
            resetLayout()
        }
    }

    private fun startLayoutEdit() {
        editingLayout = true
        lastTileUpdate = 0
        layoutEditor.show()
    }

    private fun endLayoutEdit() {
        editingLayout = false
        layoutEditor.hide()
        lastTileUpdate = 0
        settingsPanel.show()
    }

    /** Every card back in its corner at normal size. */
    private fun resetLayout() {
        for (t in tileNames) {
            Prefs.setInt(this, t + "X", -1)
            Prefs.setInt(this, t + "Y", -1)
            Prefs.setInt(this, t + "Scale", 100)
        }
        applyTiles()
        applyOverlayPrefs()
    }

    // ------------------------------------------------------------------ live charts

    private val liveSource = object : LiveValues {
        override fun numbers(): Map<String, Double> = latestNumbers
        override fun texts(): Map<String, String> = latestTexts
    }

    private fun applyCharts() {
        chartModel.configure(ChartConfig.parse(Prefs.chartSeries(this)), ChartConfig.windowSeconds(Prefs.int(this, "chartWindow", 1)))
    }

    /** Up to four values to chart, and how many seconds the graphs show. */
    private fun editCharts() {
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(8), dp(16), dp(8))
        class Row(val box: LinearLayout, val name: EditText)
        val rows = ArrayList<Row>()
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        fun addRow(text: String) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = Gravity.CENTER_VERTICAL
            val name = EditText(this)
            name.setSingleLine(true)
            name.hint = "up_lq"
            name.setText(text)
            line.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val pick = Button(this)
            pick.text = tr("List")
            pick.setOnClickListener { VariablePicker.show(this, liveSource, true) { picked -> name.setText(picked) } }
            line.addView(pick)
            val del = Button(this)
            del.text = "X"
            del.setOnClickListener {
                list.removeView(line)
                rows.removeAll { it.box === line }
            }
            line.addView(del)
            list.addView(line)
            rows.add(Row(line, name))
        }
        for (n in ChartConfig.parse(Prefs.chartSeries(this))) addRow(n)
        body.addView(list)
        val add = Button(this)
        add.text = tr("Add a chart")
        add.setOnClickListener { if (rows.size < ChartConfig.MAX_SERIES) addRow("") }
        body.addView(add)
        var window = Prefs.int(this, "chartWindow", 1).coerceIn(0, ChartConfig.WINDOWS.size - 1)
        val windowButton = Button(this)
        windowButton.isAllCaps = false
        windowButton.text = tr("Time window: {0} s  (tap to change)", ChartConfig.windowSeconds(window))
        windowButton.setOnClickListener {
            window = (window + 1) % ChartConfig.WINDOWS.size
            windowButton.text = tr("Time window: {0} s  (tap to change)", ChartConfig.windowSeconds(window))
        }
        body.addView(windowButton)
        val hint = TextView(this)
        hint.text = tr("Each chart shows one value over the last seconds, with its own scale. Pick values from the list (up to four). Link quality is always drawn from 0 to 100, so a small wobble stays small.")
        hint.textSize = 12f
        hint.setPadding(0, dp(8), 0, 0)
        body.addView(hint)
        val scroll = ScrollView(this)
        scroll.addView(body)
        val dlg = AlertDialog.Builder(this)
            .setTitle(tr("Live charts"))
            .setView(scroll)
            .setPositiveButton(tr("Save"), null)
            .setNegativeButton(tr("Cancel"), null)
            .create()
        Immersive.show(dlg)
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val typed = rows.map { it.name.text.toString().trim() }.filter { it.isNotEmpty() }
            if (typed.any { !Regex("[A-Za-z_][A-Za-z0-9_]{0,23}").matches(it) }) {
                Toast.makeText(this, tr("Use names from the list, such as up_lq or cell."), Toast.LENGTH_LONG).show()
            } else {
                Prefs.setChartSeries(this, ChartConfig.encode(ChartConfig.parse(typed.joinToString(","))))
                Prefs.setInt(this, "chartWindow", window)
                applyTiles()
                dlg.dismiss()
            }
        }
    }

    // ------------------------------------------------------------------ aircraft and packs

    private fun chooseAircraft() {
        val store = Aircraft.store(this)
        val list = store.list()
        val labels = Array(list.size) { i -> if (list[i].id == store.activeId()) tr("{0}  (in use)", list[i].name) else list[i].name }
        Immersive.show(
            AlertDialog.Builder(this)
                .setTitle(tr("Choose the aircraft"))
                .setItems(labels) { _, which -> switchAircraft(list[which].id) }
                .setNegativeButton(tr("Cancel"), null)
                .create()
        )
    }

    /** Saves this aircraft's settings, loads the other one's, and builds the screens again. */
    private fun switchAircraft(id: String) {
        if (id == Aircraft.store(this).activeId()) return
        if (!Aircraft.switchTo(this, id)) return
        Toast.makeText(this, tr("Aircraft: {0}", Aircraft.activeName(this)), Toast.LENGTH_LONG).show()
        recreate()
    }

    private fun manageAircraft() {
        val store = Aircraft.store(this)
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(8), dp(16), dp(8))
        class Row(val id: String, val name: EditText)
        val rows = ArrayList<Row>()
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        fun addRow(a: AircraftStore.Aircraft) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = Gravity.CENTER_VERTICAL
            val name = EditText(this)
            name.setSingleLine(true)
            name.filters = arrayOf(android.text.InputFilter.LengthFilter(AircraftStore.MAX_NAME))
            name.setText(a.name)
            line.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (a.id == store.activeId()) {
                val inUse = TextView(this)
                inUse.text = tr("in use")
                inUse.textSize = 12f
                inUse.setPadding(dp(6), 0, dp(6), 0)
                line.addView(inUse)
            }
            val del = Button(this)
            del.text = "X"
            del.setOnClickListener {
                val err = store.remove(a.id)
                if (err != null) Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                else {
                    list.removeView(line)
                    rows.removeAll { it.id == a.id }
                }
            }
            line.addView(del)
            list.addView(line)
            rows.add(Row(a.id, name))
        }
        for (a in store.list()) addRow(a)
        body.addView(list)
        fun newName(): String {
            var n = store.list().size + 1
            while (store.validateName(tr("Aircraft {0}", n), null) != null) n++
            return tr("Aircraft {0}", n)
        }
        fun addAircraft(values: Map<String, Any>) {
            if (store.list().size >= AircraftStore.MAX) {
                Toast.makeText(this, tr("At most {0} aircraft.", AircraftStore.MAX), Toast.LENGTH_SHORT).show()
                return
            }
            val a = store.add(newName(), values)
            if (a != null) addRow(a)
        }
        val addCopy = Button(this)
        addCopy.text = tr("Add: copy of the current")
        addCopy.setOnClickListener { addAircraft(Aircraft.live(this)) }
        body.addView(addCopy)
        val addNew = Button(this)
        addNew.text = tr("Add: starting settings")
        addNew.setOnClickListener { addAircraft(Aircraft.startingValues(this)) }
        body.addView(addNew)
        val scroll = ScrollView(this)
        scroll.addView(body)
        val dlg = AlertDialog.Builder(this)
            .setTitle(tr("Aircraft"))
            .setView(scroll)
            .setPositiveButton(tr("Save"), null)
            .setNegativeButton(tr("Cancel"), null)
            .create()
        Immersive.show(dlg)
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            var problem: String? = null
            for (r in rows) {
                problem = store.rename(r.id, r.name.text.toString())
                if (problem != null) break
            }
            if (problem != null) {
                Toast.makeText(this, problem, Toast.LENGTH_LONG).show()
            } else {
                Aircraft.save(this, store)
                applyFlightConfig()
                settingsPanel.refresh()
                dlg.dismiss()
            }
        }
    }

    private fun editPacks() {
        val profiles = Battery.profiles(this)
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(8), dp(16), dp(8))
        val head = TextView(this)
        head.text = tr("name                    battery profile              mAh")
        head.textSize = 11f
        body.addView(head)
        class Row(val box: LinearLayout, val name: EditText, val profile: Button, val mah: EditText)
        val rows = ArrayList<Row>()
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        fun addRow(p: Packs.Pack) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = Gravity.CENTER_VERTICAL
            val name = EditText(this)
            name.setSingleLine(true)
            name.filters = arrayOf(android.text.InputFilter.LengthFilter(Packs.MAX_NAME))
            name.setText(p.name)
            line.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val prof = Button(this)
            prof.isAllCaps = false
            prof.text = p.profile
            prof.setOnClickListener {
                val names = profiles.map { it.name }
                val i = names.indexOfFirst { it.equals(prof.text.toString(), ignoreCase = true) }
                prof.text = names[(i + 1) % names.size]            // the next profile
            }
            line.addView(prof)
            val mah = EditText(this)
            mah.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            mah.setSingleLine(true)
            mah.setText(p.capacityMah.toString())
            line.addView(mah, LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT))
            val del = Button(this)
            del.text = "X"
            del.setOnClickListener {
                list.removeView(line)
                rows.removeAll { it.box === line }
            }
            line.addView(del)
            list.addView(line)
            rows.add(Row(line, name, prof, mah))
        }
        for (p in Battery.packs(this)) addRow(p)
        body.addView(list)
        val add = Button(this)
        add.text = tr("Add pack")
        add.setOnClickListener {
            if (rows.size < Packs.MAX) addRow(Packs.Pack(tr("Pack {0}", rows.size + 1), Battery.active(this).name, 0))
        }
        body.addView(add)
        val hint = TextView(this)
        hint.text = tr("Each pack uses a battery profile (its limits). Choosing a pack in the menu switches to its profile. The capacity is for your records (0 = unknown). With no packs, the menu switches the profile directly.")
        hint.textSize = 12f
        hint.setPadding(0, dp(8), 0, 0)
        body.addView(hint)
        val scroll = ScrollView(this)
        scroll.addView(body)
        val dlg = AlertDialog.Builder(this)
            .setTitle(tr("Packs"))
            .setView(scroll)
            .setPositiveButton(tr("Save"), null)
            .setNegativeButton(tr("Cancel"), null)
            .create()
        Immersive.show(dlg)
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val out = ArrayList<Packs.Pack>()
            var bad = false
            for (r in rows) {
                val mah = r.mah.text.toString().trim().toIntOrNull()
                if (mah == null) { bad = true; break }
                out.add(Packs.Pack(Packs.cleanName(r.name.text.toString()), r.profile.text.toString(), mah))
            }
            val problem = if (bad) tr("Enter numbers in every field") else Packs.validate(out)
            if (problem != null) {
                Toast.makeText(this, problem, Toast.LENGTH_LONG).show()
            } else {
                Battery.setPacks(this, out)
                Battery.applyActivePack(this)
                applyBattery()
                settingsPanel.refresh()
                dlg.dismiss()
            }
        }
    }

    // ------------------------------------------------------------------ battery profiles

    private fun switchBattery() {
        Battery.cycle(this)
        applyBattery()
        Toast.makeText(this, tr("Battery profile: {0}\n{1}", Battery.label(this), BatteryProfiles.limitsText(Battery.active(this))), Toast.LENGTH_LONG).show()
        speaker.say(I18n.tIn(Lang.speechCode(this), "Battery {0}", Battery.spokenName(this)), false)
    }

    /** The cell count, the limits and every rule follow the active profile. */
    private fun applyBattery() {
        applyFlightConfig()
        reloadRules()
        reloadWarnings()
        reloadChecks()
    }

    private fun editBatteries() {
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(8), dp(16), dp(8))
        val head = TextView(this)
        head.text = tr("name                  cells    full    low    critical")
        head.textSize = 11f
        body.addView(head)
        class Row(val box: LinearLayout, val name: EditText, val cells: EditText, val full: EditText, val low: EditText, val crit: EditText)
        val rows = ArrayList<Row>()
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        fun volts(v: Double) = String.format(Locale.US, "%.2f", v)
        fun addRow(p: BatteryProfiles.Profile) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = Gravity.CENTER_VERTICAL
            fun num(text: String, widthDp: Int, decimal: Boolean): EditText {
                val e = EditText(this)
                e.inputType = if (decimal) android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL else android.text.InputType.TYPE_CLASS_NUMBER
                e.setSingleLine(true)
                e.setText(text)
                line.addView(e, LinearLayout.LayoutParams(dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT))
                return e
            }
            val name = EditText(this)
            name.setSingleLine(true)
            name.filters = arrayOf(android.text.InputFilter.LengthFilter(BatteryProfiles.MAX_NAME))
            name.setText(p.name)
            line.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val cells = num(p.cells.toString(), 48, false)
            val full = num(volts(p.full), 64, true)
            val low = num(volts(p.low), 64, true)
            val crit = num(volts(p.crit), 64, true)
            val del = Button(this)
            del.text = "X"
            del.setOnClickListener {
                list.removeView(line)
                rows.removeAll { it.box === line }
            }
            line.addView(del)
            list.addView(line)
            rows.add(Row(line, name, cells, full, low, crit))
        }
        for (p in Battery.profiles(this)) addRow(p)
        body.addView(list)
        val add = Button(this)
        add.text = tr("Add profile")
        add.setOnClickListener { if (rows.size < BatteryProfiles.MAX) addRow(BatteryProfiles.Profile("New", 6, 4.15, 3.70, 3.55)) }
        body.addView(add)
        val hint = TextView(this)
        hint.text = tr("Volts per cell. Critical must be below low, and low below full. Rules use cell and the names \$low, \$crit and \$full.")
        hint.textSize = 12f
        hint.setPadding(0, dp(8), 0, 0)
        body.addView(hint)
        val scroll = ScrollView(this)
        scroll.addView(body)
        val activeName = Battery.active(this).name
        val dlg = AlertDialog.Builder(this)
            .setTitle(tr("Battery profiles"))
            .setView(scroll)
            .setPositiveButton(tr("Save"), null)
            .setNegativeButton(tr("Cancel"), null)
            .create()
        Immersive.show(dlg)
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val out = ArrayList<BatteryProfiles.Profile>()
            var bad = false
            for (r in rows) {
                val cells = r.cells.text.toString().trim().toIntOrNull()
                val full = r.full.text.toString().trim().toDoubleOrNull()
                val low = r.low.text.toString().trim().toDoubleOrNull()
                val crit = r.crit.text.toString().trim().toDoubleOrNull()
                if (cells == null || full == null || low == null || crit == null) { bad = true; break }
                out.add(BatteryProfiles.Profile(BatteryProfiles.cleanName(r.name.text.toString()), cells, full, low, crit))
            }
            val problem = if (bad) tr("Enter numbers in every field") else BatteryProfiles.validate(out)
            if (problem != null) {
                Toast.makeText(this, problem, Toast.LENGTH_LONG).show()
            } else {
                Battery.setProfiles(this, out)
                val idx = out.indexOfFirst { it.name.equals(activeName, ignoreCase = true) }
                Prefs.setInt(this, "batActive", if (idx < 0) 0 else idx)
                applyBattery()
                dlg.dismiss()
            }
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
                Toast.makeText(this, tr("No file picker, and no {0} to import", f.absolutePath), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == requestExportSettings) { writeSettings(uri); return }
        if (requestCode == requestImportSettings) { readSettings(uri); return }
        if (requestCode == requestExport) {
            try {
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(exportText().toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, tr("Sound rules saved"), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, tr("Could not save the rules: {0}", e.message), Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == requestImport) {
            try {
                val stream = contentResolver.openInputStream(uri)
                if (stream == null) Toast.makeText(this, tr("Could not open the file"), Toast.LENGTH_LONG).show()
                else askImport(readTextLimited(stream))
            } catch (e: Exception) {
                Toast.makeText(this, tr("Could not read the file: {0}", e.message), Toast.LENGTH_LONG).show()
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
            Immersive.show(AlertDialog.Builder(this).setTitle(tr("Cannot import")).setMessage(summary.message).setPositiveButton("OK", null).create())
            return
        }
        Immersive.show(
            AlertDialog.Builder(this)
            .setTitle(tr("Import sound rules"))
            .setMessage(tr("The file has {0}\n\nThis replaces your current rules. They are kept as a backup, and Settings can restore them.", summary.message))
            .setPositiveButton(tr("Import")) { _, _ ->
                Prefs.setRulesBackup(this, Prefs.rules(this))
                Prefs.setRules(this, text)
                reloadRules()
                Toast.makeText(this, tr("Imported {0} rules", summary.rules), Toast.LENGTH_LONG).show()
                settingsPanel.refresh()
            }
            .setNegativeButton(tr("Cancel"), null)
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
        Prefs.setRules(this, DefaultRules.text(this))
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
