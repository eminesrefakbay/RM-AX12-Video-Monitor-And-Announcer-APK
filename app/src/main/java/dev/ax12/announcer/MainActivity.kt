// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

/**
 * Full-screen video; grey "NO VIDEO" panel when no frames arrive; spoken announcements from the
 * sound rules. The radio is only read, never written.
 */
class MainActivity : Activity() {

    private lateinit var texture: TextureView
    private lateinit var noFeed: View
    private lateinit var noFeedDetail: TextView
    private lateinit var noVideo: NoVideoView
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
    /** The first time (and after a guide update), once the splash and the permission dialog are out of the way. */
    private val guideCheck = Runnable {
        if (!isFinishing && FirstRun.shouldShow(Prefs.guideSeen(this)) && !anyOverlayOpen() && splash?.isShowing != true) welcomeGuide.show()
    }
    private lateinit var warnPanel: RulesPanel
    private lateinit var checkPanel: RulesPanel
    private lateinit var statsView: FlightStatsView
    private lateinit var radarView: RadarView
    private lateinit var checkView: ChecklistView
    private val checkCard = CheckCardState()          // the pre-flight card can be closed with its X
    private lateinit var chartView: ChartView
    private lateinit var connectBanner: ConnectingBanner
    private lateinit var aboutPanel: AboutPanel
    private lateinit var welcomeGuide: WelcomeGuide

    // ---- flight logs and replay
    private lateinit var dashboard: DashboardPage
    private var dashboardOn = false                      // the page: the video with its cards, or the dashboard (cards only)
    private lateinit var logDir: File
    private lateinit var flightLogger: FlightLogger
    private lateinit var logViewer: LogViewerPanel
    private lateinit var extras: FlightExtras
    private lateinit var diagPanel: InfoPanel
    private lateinit var replayUi: ReplayOverlay
    private var replay: ReplayEngine? = null
    private var replayStats = FlightStats()               // the replay's own statistics: the live ones are never touched
    private var replayChart = ChartModel()
    private var replayLastReal = 0L
    private var replayRow = -1
    private var replayChartLast = 0L
    private var replayTilesLast = 0L
    private var replayCells = 6
    private val replayNumbers = HashMap<String, Double>()
    private val replayTexts = HashMap<String, String>()
    private val fader = CardFader()             // cards fade in and out
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
    private companion object {
        const val HOLD_REBUILD_MS = 10_000L     // a rebuild or a permission dialog: the screen is back within seconds
        const val HOLD_OPENED_MS = 40_000L      // a file picker, the rules editor, the browser: a little longer, but never for ever
    }

    private val sdkLinkHolder = lazy { if (AxSdkJni.loaded) AxSdkRadioLink(applicationContext) else null }
    private val sdkLink: AxSdkRadioLink? get() = sdkLinkHolder.value
    /** The link if it exists already; never waits for the native libraries to load (the panels use this while the SDK starts). */
    private val sdkLinkIfReady: AxSdkRadioLink? get() = if (sdkLinkHolder.isInitialized()) sdkLinkHolder.value else null
    private var sdkError: String? = null
    @Volatile private var sdkRunning = false
    @Volatile private var sdkStarting = false       // the SDK is being started on its own thread (start-up method "like RadioMaster's demo")
    private var sdkGeneration = 0                    // tells a late answer of an old start from the current one

    @Volatile private var testVisible = false
    private var varsVisible = false
    private var lastVarsUpdate = 0L
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var pollThread: HandlerThread
    private lateinit var pollHandler: Handler
    private val pollLock = Any()
    @Volatile private var pollEnabled = false
    private var pollGeneration = 0L
    private data class PollFrame(
        val link: RadioLink,
        val sample: RadioSample?,
        val waitingForSdk: Boolean,
        val useFake: Boolean,
        val polledAt: Long
    )
    private val latestPoll = AtomicReference<PollFrame?>(null)
    private val pendingSamples = ConcurrentLinkedQueue<PollFrame>()
    private var lastProcessedPoll: PollFrame? = null
    private val uiTiming = TimingStats(5000)
    private var lastTickAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pollThread = HandlerThread("radio-poll").also { it.start() }
        pollHandler = Handler(pollThread.looper)
        Sunlight.on = Prefs.bool(this, "sunlight", false)
        Shapes.style = ShapeGeometry.clampStyle(Prefs.int(this, "shape", 0))
        StartupState.mark(StartupTimeline.SCREEN_CREATED)
        Icons.load(this)
        setContentView(R.layout.activity_main)

        texture = findViewById(R.id.texture)
        noFeed = findViewById(R.id.noFeed)
        noFeedDetail = findViewById(R.id.noFeedDetail)
        noVideo = NoVideoView(this)
        (noFeed as FrameLayout).addView(noVideo, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
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
        warningText.gravity = Gravity.CENTER_VERTICAL
        warningText.minimumHeight = dp(CardStyle.MIN_TOUCH_DP)       // a thick bar: 48 dp
        warningText.setPadding(dp(16), dp(8), dp(16), dp(8))
        CardStyle.fitText(warningText, 14, 18)
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
        extras = FlightExtras(this, frame, speaker, flightStats) { aircraftName }
        diagPanel = InfoPanel(this, { tr("Diagnostics") }) { buildDiagnostics(it) }
        connectBanner = ConnectingBanner(this)
        frame.addView(connectBanner.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).also { it.topMargin = dp(64) })
        layoutEditor = LayoutEditor(this, layoutHost)
        checkView = ChecklistView(this)
        checkView.visibility = View.GONE
        checkView.onClose = { checkCard.close() }
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
        aboutPanel = AboutPanel(this) { aboutText() }
        welcomeGuide = WelcomeGuide(this) { }
        logDir = File(getExternalFilesDir(null) ?: filesDir, "flights")
        flightLogger = FlightLogger(logDir, FlightLogger.Wall { System.currentTimeMillis() }, FlightLogger.MetaSource { logMeta() })
        logViewer = LogViewerPanel(this, { logDir }, { if (flightLogger.isLogging()) flightLogger.currentFile() else null }, { f -> startReplayFrom(f) })
        replayUi = ReplayOverlay(this, { replay?.toggle() }, { replay?.cycleSpeed() }, { f -> replaySeek(f) }, { stopReplay() })
        replayUi.attach(frame, 2)                       // the backdrop sits above the video and its no-video screen, below every card
        dashboard = DashboardPage(this, object : DashboardPage.Host {
            override fun stats(): FlightStats = if (replay != null) replayStats else flightStats
            override fun chart(): ChartModel = if (replay != null) replayChart else chartModel
            override fun statsContent(fs: FlightStats, now: Long) = this@MainActivity.statsContent(fs, now)
            override fun pickVariable(onPicked: (String) -> Unit) { VariablePicker.show(this@MainActivity, liveValues, false, onPicked) }
            override fun editEnded() {}                      // the tick returns straight to the dashboard, however editing was started
        })
        frame.addView(dashboard.root, FrameLayout.LayoutParams(full))
        // above the dashboard page (elevation 6 dp): the warnings, the connecting banner, the MENU button and the replay tag
        warningText.elevation = dp(7).toFloat()
        connectBanner.view.elevation = dp(7).toFloat()
        replayUi.tag.elevation = dp(16).toFloat()
        dashboardOn = Prefs.bool(this, "dashPage", false)
        if (dashboardOn) dashboard.showNow()
        reloadLogConfig()
        frame.addView(rulesPanel.root, full)
        frame.addView(warnPanel.root, FrameLayout.LayoutParams(full))
        frame.addView(checkPanel.root, FrameLayout.LayoutParams(full))
        frame.addView(settingsPanel.root, FrameLayout.LayoutParams(full))
        frame.addView(aboutPanel.root, FrameLayout.LayoutParams(full))
        frame.addView(welcomeGuide.root, FrameLayout.LayoutParams(full))
        frame.addView(logViewer.root, FrameLayout.LayoutParams(full))
        frame.addView(extras.packPanel.root, FrameLayout.LayoutParams(full))
        frame.addView(extras.positionPanel.root, FrameLayout.LayoutParams(full))
        frame.addView(diagPanel.root, FrameLayout.LayoutParams(full))

        mainMenu = MainMenu(
            this,
            { !speaker.muted },
            { rulesPanel.show() },
            { speaker.muted = !speaker.muted },
            { settingsPanel.show() },
            { batteryName },
            { switchBattery() },
            { aircraftName },
            { chooseAircraft() },
            { dashboardOn },
            { toggleDashboard() },
            { logViewer.show() },
            { Sunlight.on },
            { toggleSunlight() },
            { exitApp() }
        )
        frame.addView(mainMenu.root, FrameLayout.LayoutParams(full))

        applyMenuMode()
        applyOverlayPrefs()
        Look.apply(frame)
        menuButton.elevation = dp(8).toFloat()
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
        else if (welcomeGuide.isShowing) welcomeGuide.back()
        else if (dashboard.isEditing) dashboard.endEdit()
        else if (replay != null) stopReplay()
        else if (diagPanel.isShowing) diagPanel.hide()
        else if (extras.packPanel.isShowing) extras.packPanel.hide()
        else if (extras.positionPanel.isShowing) extras.positionPanel.hide()
        else if (logViewer.isShowing) logViewer.hide()
        else if (editingLayout) endLayoutEdit()
        else if (aboutPanel.isShowing) aboutPanel.back()
        else if (mainMenu.isShowing) mainMenu.hide()
        else if (warnPanel.isShowing) warnPanel.hide()
        else if (checkPanel.isShowing) checkPanel.hide()
        else if (settingsPanel.isShowing) settingsPanel.hide()
        else if (rulesPanel.isShowing) rulesPanel.hide()
        else super.onBackPressed()
    }

    // ------------------------------------------------------------------ menu button that hides itself

    /** Any touch on the screen brings the MENU button back for a few seconds. */
    // ---- the hold that opens the layout editor on the video page: one spot, five seconds, no swiping
    private var holdX = 0f
    private var holdY = 0f
    private val holdFire = Runnable {
        if (videoEditAllowed()) {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            startLayoutEdit()
        }
    }

    /** Not on the dashboard, not while editing, not over a panel, not while the aircraft is armed (nothing may pop up over the picture in flight). */
    private fun videoEditAllowed(): Boolean = !(dashboardOn || editingLayout || anyOverlayOpen() || flightStats.armed() || splash?.isShowing == true)

    private fun onMenuButton(x: Float, y: Float): Boolean {
        if (menuButton.visibility != View.VISIBLE) return false
        val at = IntArray(2)
        menuButton.getLocationOnScreen(at)
        return x >= at[0] && x <= at[0] + menuButton.width && y >= at[1] && y <= at[1] + menuButton.height
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && !anyOverlayOpen()) showMenuButton(4000)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                ui.removeCallbacks(holdFire)
                holdX = ev.rawX
                holdY = ev.rawY
                if (videoEditAllowed() && !onMenuButton(ev.rawX, ev.rawY)) ui.postDelayed(holdFire, HoldGesture.HOLD_MS)
            }
            MotionEvent.ACTION_MOVE ->
                if (HoldGesture.moved((ev.rawX - holdX).toDouble(), (ev.rawY - holdY).toDouble(), HoldGesture.SLOP_DP * resources.displayMetrics.density.toDouble())) ui.removeCallbacks(holdFire)
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> ui.removeCallbacks(holdFire)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun anyOverlayOpen(): Boolean = mainMenu.isShowing || rulesPanel.isShowing || warnPanel.isShowing || checkPanel.isShowing || settingsPanel.isShowing || aboutPanel.isShowing || welcomeGuide.isShowing || logViewer.isShowing || extras.packPanel.isShowing || extras.positionPanel.isShowing || diagPanel.isShowing || replay != null || dashboard.isEditing || editingLayout

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
        StartupState.mark(StartupTimeline.SCREEN_RESUMED)
        hideSystemUi()
        reloadRules()
        reloadWarnings()
        reloadChecks()
        reloadLogConfig()
        startSdk()
        synchronized(pollLock) {
            pollGeneration++
            pollEnabled = true
            pollHandler.post(radioPoll)
        }
        if (hasCameraPermission()) { if (!dashboardOn) camera.start() } else {
            holdLink(HOLD_REBUILD_MS)                    // the permission dialog pauses the screen for a moment
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        }
        ui.post(tick)
        ui.removeCallbacks(guideCheck)
        ui.postDelayed(guideCheck, SplashOverlay.SHOW_MS + 1200L)
    }

    override fun onPause() {
        synchronized(pollLock) {
            pollGeneration++
            pollEnabled = false
            pollHandler.removeCallbacks(radioPoll)
            pendingSamples.clear()
            latestPoll.set(null)
        }
        ui.removeCallbacks(tick)
        ui.removeCallbacks(guideCheck)
        ui.removeCallbacks(holdFire)
        extras.onPause()                                // a landing that waits for the rest voltage is counted now
        flightLogger.close()                            // the file is complete whenever the screen is left
        flightLogger.problem()?.let { Diagnostics.event("logger", it, "warn") }
        camera.stop()
        forgetSdk()                                     // the radio link itself is shut down by App, when no screen of the app is on show
        super.onPause()
    }

    override fun onDestroy() {
        synchronized(pollLock) {
            pollGeneration++
            pollEnabled = false
            pollHandler.removeCallbacks(radioPoll)
            pendingSamples.clear()
            latestPoll.set(null)
        }
        pollThread.quitSafely()
        ui.removeCallbacks(splashHide)
        ui.removeCallbacks(autoCheck)
        flightLogger.close()
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
        if (results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED && !dashboardOn) camera.start()
    }

    private fun hasCameraPermission() =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun hideSystemUi() {
        Immersive.apply(this)
    }

    // ------------------------------------------------------------------ radio link

    /**
     * Starts the radio link on a background thread, as RadioMaster's demo does (the screen and the camera are not held up).
     * While it starts, no data is shown, and no pretend data either. If the link is running already (the screen paused
     * itself for a moment and came back) the SDK answers "already running" and nothing is started again.
     */
    private fun startSdk() {
        SdkThread.cancelScheduledStop()                 // back within the hold time: keep the link that is running
        if (!StartupState.connected) StartupState.nativeBaseMs = 0L       // a new start: count the seconds from its beginning
        val generation = ++sdkGeneration
        sdkStarting = true
        SdkThread.run {
            var link: AxSdkRadioLink? = null
            var error: String? = null
            try {
                link = sdkLink                          // may load the native libraries: not on the screen's thread
                if (link != null) {
                    StartupState.mark(StartupTimeline.SDK_CALLED)
                    error = link.start()
                    StartupState.mark(StartupTimeline.SDK_RETURNED)
                    Diagnostics.event("link", if (error == null) "radio link started" else error, if (error == null) "info" else "error")
                }
            } catch (t: Throwable) {
                error = tr("SDK start failed ({0})", t.javaClass.simpleName)       // always report back, or the screen would wait for ever
            }
            runOnUiThread {
                if (generation == sdkGeneration) {
                    sdkError = error
                    sdkRunning = link != null && error == null
                    sdkStarting = false
                    error?.let { Toasts.make(this, it, Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    /**
     * Our own actions that take the whole app off the display and bring it back (a rebuild, a file picker, the browser) call this
     * first: the radio link then stays up for [ms] instead of being shut down and started again, which costs about 12 seconds
     * without stick data. The rules editor and any other screen of this app keep the link up for as long as they are open
     * (see App). Leaving the app any other way (Home, another app) still releases the radio's serial port at once.
     */
    private fun holdLink(ms: Long) {
        App.visibility.hold(SystemClock.elapsedRealtime(), ms)
    }

    override fun recreate() {
        holdLink(HOLD_REBUILD_MS)
        super.recreate()
    }

    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        holdLink(HOLD_OPENED_MS)                         // every screen this app opens itself: pickers, the rules editor, the browser
        super.startActivityForResult(intent, requestCode, options)
    }

    /** This screen no longer reads the radio link (it may well still be running: see App). */
    private fun forgetSdk() {
        sdkGeneration++                                 // a start that is still running must not report back
        sdkStarting = false
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
            Toasts.make(this, tr("Sound rules: {0} error(s). Open the rules editor.", parsed.errors.size), Toast.LENGTH_LONG).show()
        }
    }

    private fun reloadChecks() {
        checkCard.reopen()                               // a new aircraft, pack or set of checks: the card is wanted again
        val text = Prefs.checkRules(this)
        val parsed = RuleParser.parse(text, Battery.macros(this))
        val model = CheckModel(parsed.rules)
        checkModel = model
        checkEngine = RuleEngine(parsed, null, model)
        if (parsed.errors.isNotEmpty()) {
            Toasts.make(this, tr("Pre-flight checks: {0} error(s). Open Settings, then the checks.", parsed.errors.size), Toast.LENGTH_LONG).show()
        }
    }

    private fun reloadWarnings() {
        val text = Prefs.warningRules(this)
        val macros = Battery.macros(this)
        warningBar.load(text, macros)
        val errors = RuleParser.parse(text, macros).errors
        if (errors.isNotEmpty()) {
            Toasts.make(this, tr("Warning rules: {0} error(s). Open Settings, then the warning rules.", errors.size), Toast.LENGTH_LONG).show()
        }
    }

    /** 20 times a second: NO VIDEO panel, read the radio, run the rules, refresh the debug panels. */
    private val radioPoll = object : Runnable {
        override fun run() {
            val generation = synchronized(pollLock) {
                if (!pollEnabled) return
                pollGeneration
            }
            val waitingForSdk = sdkStarting && !testVisible
            val availableSdk = if (waitingForSdk) sdkLinkIfReady else sdkLink
            val useFake = testVisible || (!waitingForSdk && (availableSdk == null || !sdkRunning))
            val link: RadioLink = if (useFake || waitingForSdk) fakeLink else availableSdk!!
            val sample = if (waitingForSdk) null else link.poll()
            synchronized(pollLock) {
                if (pollEnabled && generation == pollGeneration) {
                    val frame = PollFrame(link, sample, waitingForSdk, useFake, SystemClock.elapsedRealtime())
                    latestPoll.set(frame)
                    if (sample != null) pendingSamples.add(frame)
                    pollHandler.postDelayed(this, 50)
                }
            }
        }
    }

    /** 20 times a second: update the UI, rules and debug panels from the newest radio reading. */
    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            if (lastTickAt != 0L) uiTiming.add(now, now - lastTickAt)
            lastTickAt = now

            val stale = now - camera.lastFrameMs > 1000 && replay == null       // a replay has its own backdrop
            fader.set(noFeed, stale)                          // the grey no-video screen fades in and out
            if (stale) {
                noVideo.update(camera.status())
                noFeedDetail.text = camera.barText()          // the technical line stays small along the bottom
            }

            // the MENU button must not sit on top of a panel's own buttons
            if (anyOverlayOpen()) {
                menuButton.visibility = View.GONE
                overlayWasOpen = true
            } else if (overlayWasOpen) {
                overlayWasOpen = false
                if (Prefs.menuAlways(this@MainActivity)) menuButton.visibility = View.VISIBLE else showMenuButton(4000)
            }

            val poll = latestPoll.get()
            val waitingForSdk = poll?.waitingForSdk ?: (sdkStarting && !testVisible)
            var liveSampleUpdated = false
            while (true) {
                val frame = pendingSamples.poll() ?: break
                val sample = frame.sample ?: continue
                val sampleNow = frame.polledAt
                val link = frame.link
                val scaler = if (frame.useFake || frame.waitingForSdk) fakeScaler else sdkScaler
                lastProcessedPoll = frame
                liveSampleUpdated = true
                if (scaler.observe(sample.raw)) {                    // the radio's unit became clear: start again
                    engine?.reset()
                    reloadWarnings()
                    reloadChecks()
                }
                if (sample.cfgReady && link is AxSdkRadioLink) {      // the first value that can move the sticks has reached the screen
                    if (!StartupState.connected) Diagnostics.event("link", "radio configuration loaded after " + String.format(java.util.Locale.US, "%.1f", StartupState.secondsSinceLinkStart()) + " s")
                    StartupState.connected = true
                    StartupState.mark(StartupTimeline.FIRST_UI)
                    StartupState.logLaunch(this@MainActivity, sdkLink)
                }
                val numbers = Variables.numbers(sample, scaler, batteryCells)
                flightStats.update(numbers, sampleNow)
                if (flightStats.armed()) {                      // height and distance exist only in flight, and rules can use them
                    if (!flightStats.heightNow().isNaN()) numbers["height"] = flightStats.heightNow()
                    if (!flightStats.distanceNow().isNaN()) numbers["dist"] = flightStats.distanceNow()
                }
                extras.tick(sampleNow, numbers)                  // distance, energy, link loss, landing and the pack book
                latestNumbers = numbers
                latestTexts = sample.texts
                flightLogger.update(sampleNow, flightStats.armed(), numbers, sample.texts)  // a log from arming to disarming, when it is switched on
                if (replay != null && flightStats.armed()) stopReplay()                  // an armed aircraft: the live screen comes first
                if (replay == null) {
                    engine?.update(numbers, sample.texts, sampleNow)
                    warningBar.update(numbers, sample.texts, sampleNow)
                    checkEngine?.update(numbers, sample.texts, sampleNow)
                    if (sampleNow - lastTileUpdate >= 200) {
                        lastTileUpdate = sampleNow
                        updateTiles(numbers, sample.texts, sampleNow)
                    }
                }
                if (testVisible) {
                    val sb = StringBuilder()
                    for (i in 0 until 16) sb.append("ch").append(i + 1).append("=").append(numbers["ch" + (i + 1)]!!.toInt())
                        .append(if (i % 4 == 3) "\n" else "  ")
                    status.text = sb.toString().trim() + "\n" + link.name + ": " + sample.info + "\n" + camera.statusLine() + "\n" + camera.videoStats()
                }
            }
            if (liveSampleUpdated && replay == null) {
                if (sticksEnabled) sticksView.update(latestNumbers)
                dashboard.updateFast(latestNumbers, latestTexts)
            }
            if (varsVisible && now - lastVarsUpdate > 250) {
                lastVarsUpdate = now
                val displayPoll = lastProcessedPoll
                if (poll?.sample != null && displayPoll?.sample != null) {
                    val scaler = if (displayPoll.useFake || displayPoll.waitingForSdk) fakeScaler else sdkScaler
                    varsText.text = describe(displayPoll.link, displayPoll.sample, latestNumbers, scaler)
                } else {
                    varsText.text = (if (waitingForSdk) tr("The SDK is starting in the background...") else tr("No data from the radio yet...")) + "\n\n" + StartupState.text(sdkLinkIfReady)
                }
            }
            // while the radio link comes up (about 12 s): say so, count the seconds, and say what to check if it takes long
            if (replay != null) replayStep(now)
            extras.clock(now)                                // the approval window: the chip and the spoken warnings
            val connecting = (sdkStarting || sdkRunning) && !StartupState.connected && !testVisible && sdkError == null && replay == null
            fader.set(connectBanner.view, connecting)
            if (connecting) connectBanner.update(StartupState.secondsSinceLinkStart(now))
            if (poll?.sample == null && editingLayout && now - lastTileUpdate >= 200) {
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
                if (testVisible) tr("Test channels are on - the radio is not being read\n")
                else if (sdkLink == null) tr("No SDK in this build - the radio is not being read\n")
                else tr("The SDK is not running - the radio is not being read\n")
            )
        }
        if (sdkError != null) sb.append("SDK: ").append(sdkError).append('\n')
        sb.append(sample.info).append('\n')
        sb.append(camera.videoStats()).append('\n')
        sb.append('\n').append(StartupState.text(sdkLinkIfReady))
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
            Toasts.make(this, tr("Android lists no cameras"), Toast.LENGTH_LONG).show()
            return
        }
        val names = Array(list.size) { list[it].second }
        Immersive.show(
            Sunlight.builder(this)
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
        override fun cameraId(): String = camera.cameraId ?: tr("Auto")
        override fun chooseCamera() = this@MainActivity.chooseCamera()
        override fun rotation(): Int = camera.rotation
        override fun rotateVideo() = camera.rotateNext()
        override fun fill(): Boolean = camera.fill
        override fun toggleFill() = camera.toggleFill()
        override fun speechOn(): Boolean = !speaker.muted
        override fun setSpeechOn(on: Boolean) { speaker.muted = !on }
        override fun speakTest() {
            if (speaker.muted) Toasts.make(this@MainActivity, tr("Speech is off"), Toast.LENGTH_SHORT).show()
            else if (!speaker.languageOk()) Toasts.make(this@MainActivity, voiceMissingText(), Toast.LENGTH_LONG).show()
            else speaker.say(I18n.tIn(Lang.speechCode(this@MainActivity), "Sound test"), true)
        }

        override fun languageLabel(): String {
            val pref = Prefs.language(this@MainActivity)
            return if (pref == "auto") tr("Automatic ({0})", Lang.nativeName(I18n.language())) else Lang.nativeName(pref)
        }
        override fun cycleLanguage() {
            val i = Lang.choices.indexOf(Prefs.language(this@MainActivity)).coerceAtLeast(0)
            var next = Lang.choices[(i + 1) % Lang.choices.size]
            if (next == "zh" && !Lang.hasChineseFont()) {       // skip a language that would show as empty boxes, and say why
                Toasts.make(this@MainActivity, tr("This device has no Chinese font, so the Chinese screen language is not available."), Toast.LENGTH_LONG).show()
                next = Lang.choices[(i + 2) % Lang.choices.size]
            }
            Prefs.setLanguage(this@MainActivity, next)
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
            Toasts.make(this@MainActivity, if (speaker.languageOk()) tr("The voice now speaks {0}.", Lang.nativeName(Lang.speechCode(this@MainActivity))) else voiceMissingText(), Toast.LENGTH_LONG).show()
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
        override fun sunlightOn(): Boolean = Sunlight.on
        override fun shapeLabel(): String = Shapes.label(Shapes.style)
        override fun cycleShape() {
            Shapes.style = (Shapes.style + 1) % ShapeGeometry.COUNT
            Prefs.setInt(this@MainActivity, "shape", Shapes.style)
            invalidateTree(rootFrame)
            warningBar.restyle()
            if (settingsPanel.isShowing) settingsPanel.skin()
        }
        override fun setSunlightOn(on: Boolean) = this@MainActivity.setSunlight(on)
        override fun setMenuAlways(on: Boolean) {
            Prefs.setMenuAlways(this@MainActivity, on)
            applyMenuMode()
        }
        override fun variablesOn(): Boolean = varsVisible
        override fun setVariablesOn(on: Boolean) = setVarsVisible(on)
        override fun testChannelsOn(): Boolean = testVisible
        override fun setTestChannelsOn(on: Boolean) = setTestVisible(on)
        override fun about(): String = this@MainActivity.aboutText()
        override fun openAbout() = aboutPanel.show()
        override fun openGuide() = welcomeGuide.show()

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
        override fun editDashboard() {
            settingsPanel.hide()
            if (!dashboardOn) setDashboard(true)
            dashboard.startEdit()
        }
        override fun resetDashboard() = dashboard.resetLayout()
        override fun saveDashboardLayout() = dashboard.saveLayoutDialog()
        override fun loadDashboardLayout() = dashboard.loadSavedLayoutDialog()
        override fun deleteDashboardLayout() = dashboard.deleteSavedLayoutDialog()
        override fun chooseDashboardPreset() = dashboard.presetDialog()
        override fun horizonInvertPitch(): Boolean = Prefs.bool(this@MainActivity, "horizonInvPitch", false)
        override fun setHorizonInvertPitch(on: Boolean) { Prefs.setBool(this@MainActivity, "horizonInvPitch", on); dashboard.rebuild() }
        override fun horizonInvertRoll(): Boolean = Prefs.bool(this@MainActivity, "horizonInvRoll", false)
        override fun setHorizonInvertRoll(on: Boolean) { Prefs.setBool(this@MainActivity, "horizonInvRoll", on); dashboard.rebuild() }
        override fun logOn(): Boolean = Prefs.logOn(this@MainActivity)
        override fun setLogOn(on: Boolean) { Prefs.setLogOn(this@MainActivity, on); reloadLogConfig() }
        override fun logRateLabel(): String = tr("{0} per second", FlightLogger.RATES_HZ[Prefs.logRate(this@MainActivity)])
        override fun cycleLogRate() {
            Prefs.setInt(this@MainActivity, "logRate", (Prefs.logRate(this@MainActivity) + 1) % FlightLogger.RATES_HZ.size)
            reloadLogConfig()
        }
        override fun logFieldsLabel(): String = tr("{0} values", LogFields.selection(Prefs.logFields(this@MainActivity)).size)
        override fun editLogFields() {
            val live = object : LiveValues {
                override fun numbers(): Map<String, Double> = latestNumbers
                override fun texts(): Map<String, String> = latestTexts
            }
            LogFieldPicker.show(this@MainActivity, live, LogFields.selection(Prefs.logFields(this@MainActivity)), Prefs.int(this@MainActivity, "armCh", 5).coerceIn(1, 16)) { chosen ->
                Prefs.setLogFields(this@MainActivity, LogFields.join(chosen))
                reloadLogConfig()
                settingsPanel.refresh()
            }
        }
        override fun logKeepLabel(): String = tr("{0} flights", FlightLogger.KEEP[Prefs.logKeep(this@MainActivity)])
        override fun cycleLogKeep() {
            Prefs.setInt(this@MainActivity, "logKeep", (Prefs.logKeep(this@MainActivity) + 1) % FlightLogger.KEEP.size)
            reloadLogConfig()
        }
        override fun logStatus(): String = if (flightLogger.isLogging()) tr("Logging now: {0} rows", flightLogger.rows()) else flightLogger.problem()
        override fun logFolder(): String = logDir.absolutePath
        override fun openLogViewer() = logViewer.show()

        override fun windowOn(): Boolean = Prefs.bool(this@MainActivity, "winOn", false)
        override fun setWindowOn(on: Boolean) { Prefs.setBool(this@MainActivity, "winOn", on); extras.reload() }
        override fun windowStartLabel(): String = ApprovalWindow.hm(Prefs.int(this@MainActivity, "winStart", 960))
        override fun pickWindowStart() = pickClock(tr("Opens at"), "winStart", 960)
        override fun windowEndLabel(): String = ApprovalWindow.hm(Prefs.int(this@MainActivity, "winEnd", 1080))
        override fun pickWindowEnd() = pickClock(tr("Closes at"), "winEnd", 1080)
        override fun windowTwoHours() {
            val c = java.util.Calendar.getInstance()
            val nowMin = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
            Prefs.setInt(this@MainActivity, "winStart", nowMin)
            Prefs.setInt(this@MainActivity, "winEnd", (nowMin + 120) % 1440)
            extras.reload()
        }
        override fun windowWarnLabel(): String {
            val w = Prefs.string(this@MainActivity, "winWarn", ApprovalWindow.DEFAULT_WARN)
            return if (w.isBlank()) tr("None") else w
        }
        override fun editWindowWarn() = this@MainActivity.editWindowWarn()
        override fun windowClockNote(): String {
            val c = java.util.Calendar.getInstance()
            return ApprovalWindow.hm(c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE))
        }
        override fun windowSpeechOn(): Boolean = Prefs.bool(this@MainActivity, "winSpeech", true)
        override fun setWindowSpeechOn(on: Boolean) { Prefs.setBool(this@MainActivity, "winSpeech", on) }
        override fun debriefOn(): Boolean = Prefs.bool(this@MainActivity, "debriefOn", true)
        override fun setDebriefOn(on: Boolean) { Prefs.setBool(this@MainActivity, "debriefOn", on) }
        override fun lossSpeechOn(): Boolean = Prefs.bool(this@MainActivity, "lossSpeech", true)
        override fun setLossSpeechOn(on: Boolean) { Prefs.setBool(this@MainActivity, "lossSpeech", on) }
        override fun openPackBook() {
            settingsPanel.hide()
            extras.packPanel.show()
        }
        override fun openPosition() {
            settingsPanel.hide()
            extras.positionPanel.show()
        }
        override fun openDiagnostics() {
            settingsPanel.hide()
            diagPanel.show()
        }
        override fun showStartupTimings() = this@MainActivity.showStartupTimings()
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
        override fun warningBarPositionLabel(): String = if (Prefs.int(this@MainActivity, "warnBarPos", 0) == 0) tr("Top") else tr("Bottom")
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

    private val sizeNames = arrayOf(tr("Small"), tr("Medium"), tr("Large"))
    private val positionNames = arrayOf(tr("Bottom center"), tr("Bottom left"), tr("Bottom right"), tr("Top left"), tr("Top right"))
    private val opacityNames = arrayOf("35%", "65%", "95%")

    private fun cyclePref(key: String, def: Int, count: Int) {
        Prefs.setInt(this, key, (Prefs.int(this, key, def) + 1) % count)
        applyOverlayPrefs()
    }

    // ------------------------------------------------------------------ flight overlays

    private val tilePositionNames = arrayOf(tr("Top left"), tr("Top right"), tr("Bottom left"), tr("Bottom right"), tr("Center"))
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
        fader.setOpacity(chartView, floatArrayOf(0.5f, 0.75f, 1f)[Prefs.int(this, "chartOpacity", 1).coerceIn(0, 2)])
        layoutTile(chartView, "chart") { placeTile(chartView, Prefs.int(this, "chartPos", defaultTilePosition("chart")).coerceIn(0, 4)) }
        val alpha = floatArrayOf(0.5f, 0.75f, 1f)
        statsView.setTextSp(floatArrayOf(12f, 15f, 19f)[Prefs.int(this, "statsSize", 1).coerceIn(0, 2)])
        fader.setOpacity(statsView, alpha[Prefs.int(this, "statsOpacity", 1).coerceIn(0, 2)])
        layoutTile(statsView, "stats") { placeTile(statsView, Prefs.int(this, "statsPos", defaultTilePosition("stats")).coerceIn(0, 4)) }
        radarView.setSizeDp(intArrayOf(110, 150, 210)[Prefs.int(this, "radarSize", 1).coerceIn(0, 2)])
        fader.setOpacity(radarView, alpha[Prefs.int(this, "radarOpacity", 1).coerceIn(0, 2)])
        layoutTile(radarView, "radar") { placeTile(radarView, Prefs.int(this, "radarPos", defaultTilePosition("radar")).coerceIn(0, 4)) }
        checkView.setTextSp(floatArrayOf(13f, 16f, 20f)[Prefs.int(this, "checkSize", 1).coerceIn(0, 2)])
        fader.setOpacity(checkView, alpha[Prefs.int(this, "checkOpacity", 1).coerceIn(0, 2)])
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

    // ------------------------------------------------------------------ flight logs and replay

    /** Hands the logging settings to the logger (at start, when the screen comes back, and after a change in Settings). */
    private fun reloadLogConfig() {
        flightLogger.configure(Prefs.logOn(this), Prefs.logRate(this), LogFields.selection(Prefs.logFields(this)), Prefs.logKeep(this))
        extras.reload()
    }

    /** What the top of every log says: the app, the aircraft, the pack and the arm switch (a replay needs the last ones). */
    private fun logMeta(): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }
        m["app"] = "AX-HUD " + version
        m["aircraft"] = aircraftName
        m["pack"] = batteryName
        m["cells"] = batteryCells.toString()
        m["arm_ch"] = Prefs.int(this, "armCh", 5).coerceIn(1, 16).toString()
        m["arm_high"] = Prefs.int(this, "armHigh", 1750).toString()
        m["arm_low"] = Prefs.int(this, "armLow", 1650).toString()
        return m
    }

    /** Reads a log on a background thread, then starts its replay. */
    private fun startReplayFrom(file: File) {
        Toasts.make(this, tr("Loading the log..."), Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val log = LogFormat.readFile(file, 4_000_000)
                runOnUiThread { if (!isFinishing) startReplay(log, file.name) }
            } catch (e: Exception) {
                runOnUiThread { Toasts.make(this, tr("The log cannot be read: {0}", e.message ?: ""), Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun startReplay(log: FlightLog, name: String) {
        if (flightStats.armed()) return                                   // flying: the live screen comes first
        if (log.rows < 2) { Toasts.make(this, tr("The log cannot be read: {0}", name), Toast.LENGTH_LONG).show(); return }
        stopReplay()
        logViewer.hide()
        settingsPanel.hide()
        mainMenu.hide()
        replay = ReplayEngine(log)
        replayCells = log.metaNumber("cells", batteryCells.toDouble()).toInt().coerceAtLeast(1)
        replayLastReal = SystemClock.elapsedRealtime()
        val shown = LogStore.Entry()
        shown.name = name
        shown.started = log.meta("started", "")
        val title = tr("REPLAY") + "  " + LogText.dateTime(shown) + (if (log.meta("aircraft", "").isEmpty()) "" else "  \u00B7  " + log.meta("aircraft", ""))
        replayUi.show(title)
        rebuildReplay(0)
        val missing = LogFields.missingForReplay(log.columns.toList(), log.metaNumber("arm_ch", 5.0).toInt())
        if (missing.isNotEmpty()) Toasts.make(this, tr("This log lacks values that some cards need: {0}", missing.joinToString(", ")), Toast.LENGTH_LONG).show()
        replay?.play()
    }

    private fun stopReplay() {
        if (replay == null) return
        replay = null
        replayUi.hide()
        radarView.bind(flightStats)                                       // the live cards come back on the next tick
        chartView.bind(chartModel)
    }

    /** Builds the statistics and the chart up to a position, from the start of the log (after a start or a seek). */
    private fun rebuildReplay(position: Long) {
        val r = replay ?: return
        val log = r.log()
        replayStats = FlightStats()
        replayStats.configure(
            log.metaNumber("arm_ch", Prefs.int(this, "armCh", 5).toDouble()).toInt().coerceIn(1, 16),
            log.metaNumber("arm_high", Prefs.int(this, "armHigh", 1750).toDouble()),
            log.metaNumber("arm_low", Prefs.int(this, "armLow", 1650).toDouble()),
            replayCells
        )
        radarView.bind(replayStats)
        replayChart = ChartModel()
        val windowSeconds = ChartConfig.windowSeconds(Prefs.int(this, "chartWindow", 1))
        replayChart.configure(ChartConfig.parse(Prefs.chartSeries(this)), windowSeconds)
        chartView.bind(replayChart)
        replayChartLast = 0L
        val row = r.rowAt(position)
        if (row >= 0) {
            val chartFrom = r.rowForWindow(position, windowSeconds * 1000L)
            var i = 0
            r.feed(0, row, ReplayEngine.Sink { now, numbers, _ -> replayFeed(now, numbers, i++ >= chartFrom) })
        }
        replayRow = row
    }

    /** One logged row goes through the same steps as a live one: statistics, height and distance, volts per cell, the chart. */
    private fun replayFeed(now: Long, numbers: Map<String, Double>, chart: Boolean) {
        val m = HashMap<String, Double>(numbers)
        replayStats.update(m, now)
        if (replayStats.armed()) {
            if (!replayStats.heightNow().isNaN()) m["height"] = replayStats.heightNow()
            if (!replayStats.distanceNow().isNaN()) m["dist"] = replayStats.distanceNow()
        }
        BatteryProfiles.addDerived(m, replayCells)
        if (chart && now - replayChartLast >= 200) {                      // five samples a second, as in the live chart
            replayChart.sample(m, now)
            replayChartLast = now
        }
    }

    /** Moves the replay on by the time that has passed and shows its present row on the cards. */
    private fun replayStep(now: Long) {
        val r = replay ?: return
        warningText.visibility = View.GONE                                // no warnings in a replay
        val dt = now - replayLastReal
        replayLastReal = now
        r.advance(dt.coerceIn(0L, 250L))
        val cur = r.currentRow()
        if (cur > replayRow) {
            r.feed(replayRow + 1, cur, ReplayEngine.Sink { t, numbers, _ -> replayFeed(t, numbers, true) })
            replayRow = cur
        }
        r.fill(cur, replayNumbers, replayTexts)
        val m = HashMap<String, Double>(replayNumbers)
        if (replayStats.armed()) {
            if (!replayStats.heightNow().isNaN()) m["height"] = replayStats.heightNow()
            if (!replayStats.distanceNow().isNaN()) m["dist"] = replayStats.distanceNow()
        }
        BatteryProfiles.addDerived(m, replayCells)
        if (sticksEnabled) sticksView.update(m)
        dashboard.updateFast(m, replayTexts)
        if (now - replayTilesLast >= 100) {
            replayTilesLast = now
            updateTiles(m, replayTexts, ReplayEngine.clock(r.positionMs()), replayStats, replayChart, false)
        }
        replayUi.update(r.positionMs(), r.durationMs(), r.playing(), r.speed())
    }

    private fun replaySeek(fraction: Double) {
        val r = replay ?: return
        r.seek((fraction * r.durationMs()).toLong())
        rebuildReplay(r.positionMs())
    }

    private fun num0(v: Double): String = if (v.isNaN()) "-" else Math.round(v).toString()
    private fun num1(v: Double): String = if (v.isNaN()) "-" else String.format(Locale.US, "%.1f", v)
    private fun clock(ms: Long): String = String.format(Locale.US, "%d:%02d", ms / 60000, (ms / 1000) % 60)

    /** The title and the lines of the statistics card (the rows chosen in Settings), for the video page and for the dashboard. */
    private fun statsContent(fs: FlightStats, now: Long): Pair<String, List<FlightStatsView.Line>> {
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
        return Pair((if (fs.armed()) tr("FLIGHT  ") else tr("LAST FLIGHT  ")) + clock(fs.armedMs(now)), lines)
    }

    /** Switches the page: the video with its cards, or the dashboard (cards only). The camera rests while the dashboard is shown. */
    private fun toggleDashboard() { setDashboard(!dashboardOn) }

    private fun setDashboard(on: Boolean) {
        dashboardOn = on
        Prefs.setBool(this, "dashPage", on)
        if (on) {
            dashboard.show()
            camera.stop()
        } else {
            dashboard.hide()
            if (hasCameraPermission()) camera.start()
        }
        showMenuButton(4000)
    }

    /** Refresh the three cards (about five times a second). */
    private fun updateTiles(numbers: Map<String, Double>, texts: Map<String, String>, now: Long,
                            fs: FlightStats = flightStats, chart: ChartModel = chartModel, sampleChart: Boolean = true) {
        if (sampleChart) chart.sample(numbers, now)
        val showChart = chartOn && chart.names.isNotEmpty()
        fader.set(chartView, showChart)
        if (showChart) chartView.refresh(now)
        val flight = fs.armed() || fs.hasFlight() || editingLayout

        val showStats = statsOn && flight
        fader.set(statsView, showStats)
        if (showStats) {
            val content = statsContent(fs, now)
            statsView.setContent(content.first, content.second)
        }

        dashboard.update(now)
        val showRadar = radarOn && (fs.hasHome() || editingLayout)
        fader.set(radarView, showRadar)
        if (showRadar) radarView.refresh(fs)

        val cm = checkModel
        checkCard.update(flightStats.armed())               // an armed aircraft ends the pre-flight period: the card is back for the next one
        val showCheck = checkOn && cm != null && (!fs.armed() || editingLayout) && (!cm.allUnknown() || editingLayout) && checkCard.allowed(editingLayout) && replay == null
        fader.set(checkView, showCheck)
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
    // ------------------------------------------------------------------ diagnostics and the bug report

    private fun logFolder(): java.io.File = java.io.File(getExternalFilesDir(null) ?: filesDir, "flights")

    private fun diagReport(): String = Diagnostics.report(this, sdkLinkIfReady, flightLogger, latestNumbers, logFolder())

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("AX-HUD", text))
    }

    private fun issueUrl(): String = "https://github.com/eminesrefakbay/RM-AX12-Video-Monitor-And-Announcer-APK/issues/new"

    @Suppress("DEPRECATION")        // versionCode: the old field is what API levels below 28 offer
    private fun buildDiagnostics(p: InfoPanel) {
        val info = try { packageManager.getPackageInfo(packageName, 0) } catch (e: Exception) { null }
        val code = if (info == null) 0L else if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        val log = Diagnostics.log
        val summary = p.card()
        p.line(summary, tr("Version {0} (build {1})", info?.versionName ?: "?", code), 17f, Sunlight.c(Color.WHITE), bold = true)
        p.line(summary, tr("{0} {1}, Android {2}", Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE), 13f, Sunlight.c(CardStyle.LABEL), topDp = 2)
        val linkLine = if (StartupState.connected) tr("Radio link: connected after {0} s", String.format(java.util.Locale.US, "%.1f", StartupState.secondsSinceLinkStart()))
        else tr("Radio link: not connected")
        p.line(summary, linkLine, 13f, if (StartupState.connected) Sunlight.c(CardStyle.OK) else Sunlight.c(CardStyle.WARN), topDp = 2)
        p.line(summary, tr("Events: {0} ({1} warnings, {2} errors)", log.size(), log.count("warn"), log.count("error")), 13f, Sunlight.c(CardStyle.LABEL), topDp = 2)

        val crash = Diagnostics.crashText()
        if (crash != null) {
            val c = p.card()
            p.line(c, tr("The app stopped unexpectedly"), 16f, Sunlight.c(CardStyle.BAD), bold = true)
            p.line(c, crash.trim().split("\n").take(6).joinToString("\n"), 11f, Sunlight.c(0xFFDDE3E8.toInt()), topDp = 4)
            p.button(c, tr("Delete the crash record")) {
                Diagnostics.clearCrash()
                p.refresh()
            }
        }

        val actions = p.card()
        p.line(actions, tr("The report holds no GPS positions, no aircraft or pack names and nothing personal. Read it before you send it."), 12f, Sunlight.c(CardStyle.LABEL))
        p.button(actions, tr("Copy the report")) {
            copyToClipboard(diagReport())
            Toasts.make(this, tr("Report copied. Paste it into the issue."), Toast.LENGTH_LONG).show()
        }
        p.button(actions, tr("Share the report...")) {
            try {
                holdLink(HOLD_OPENED_MS)
                val i = Intent(Intent.ACTION_SEND)
                i.type = "text/plain"
                i.putExtra(Intent.EXTRA_SUBJECT, "AX-HUD bug report")
                i.putExtra(Intent.EXTRA_TEXT, diagReport())
                startActivity(Intent.createChooser(i, tr("Share the report")))
            } catch (e: Exception) {
                Toasts.make(this, tr("Nothing on this radio can take it. Copy the report instead."), Toast.LENGTH_LONG).show()
            }
        }
        p.button(actions, tr("Read the report")) {
            val tv = TextView(this)
            tv.text = diagReport()
            tv.typeface = Typeface.MONOSPACE
            tv.textSize = 11f
            tv.setPadding(dp(16), dp(8), dp(16), dp(8))
            tv.setTextIsSelectable(true)
            val sv = ScrollView(this)
            sv.addView(tv)
            Immersive.show(Sunlight.builder(this).setTitle(tr("The report")).setView(sv).setPositiveButton(tr("Close"), null).create())
        }
        p.button(actions, if (Diagnostics.note.isEmpty()) tr("Add a note...") else tr("Edit the note...")) { editDiagNote(p) }
        p.button(actions, tr("Save the report to a file")) {
            try {
                val f = java.io.File(getExternalFilesDir(null) ?: filesDir, "bug_report.txt")
                f.writeText(diagReport(), Charsets.UTF_8)
                Toasts.make(this, tr("Saved: {0}", f.absolutePath), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toasts.make(this, tr("Could not save the report"), Toast.LENGTH_LONG).show()
            }
        }
        p.button(actions, tr("Open the issue page")) {
            try {
                holdLink(HOLD_OPENED_MS)
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(issueUrl())))
            } catch (e: Exception) {
                copyToClipboard(issueUrl())
                Toasts.make(this, tr("No browser on this radio. The address was copied."), Toast.LENGTH_LONG).show()
            }
        }
        p.button(actions, tr("Clear the events")) {
            Diagnostics.log.clear()
            Toasts.make(this, tr("Events cleared"), Toast.LENGTH_SHORT).show()
            p.refresh()
        }

        val ev = p.card()
        p.line(ev, tr("Recent events"), 15f, Sunlight.c(CardStyle.TITLE), bold = true)
        val list = log.snapshot().takeLast(15).reversed()
        if (list.isEmpty()) p.line(ev, tr("No events yet."), 12f, Sunlight.c(CardStyle.LABEL), topDp = 4)
        for (e in list) {
            val mark = if (e.level == "error") Sunlight.c(CardStyle.BAD) else if (e.level == "warn") Sunlight.c(CardStyle.WARN) else Sunlight.c(0xFFDDE3E8.toInt())
            p.line(ev, BugReport.time(e.wallMs, java.util.TimeZone.getDefault()) + "  " + e.tag + ": " + e.message, 11f, mark, topDp = 3)
        }
    }

    private fun editDiagNote(p: InfoPanel) {
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(8), dp(16), dp(8))
        val hint = TextView(this)
        hint.text = tr("What did you do, and what happened? Positions and addresses are removed from the note automatically.")
        hint.textSize = 12f
        body.addView(hint)
        val e = EditText(this)
        e.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        e.setText(Diagnostics.note)
        e.minLines = 3
        body.addView(e)
        Immersive.show(
            Sunlight.builder(this)
                .setTitle(tr("Describe the problem (optional)"))
                .setView(body)
                .setPositiveButton(tr("OK")) { _, _ ->
                    Diagnostics.note = e.text.toString().take(BugReport.MAX_NOTE)
                    p.refresh()
                }
                .setNegativeButton(tr("Cancel"), null)
                .create()
        )
    }

    private fun toggleSunlight() = setSunlight(!Sunlight.on)

    /** Every view draws itself again (the shape style is read when a view draws). */
    private fun invalidateTree(v: View) {
        v.invalidate()
        if (v is ViewGroup) for (i in 0 until v.childCount) invalidateTree(v.getChildAt(i))
    }

    /** The sunlight theme on or off: kept, and shown at once on the dashboard, the warning bar and the screen that is open. */
    private fun setSunlight(on: Boolean) {
        Sunlight.on = on
        Prefs.setBool(this, "sunlight", on)
        dashboard.applyTheme()
        warningBar.restyle()
        if (settingsPanel.isShowing) settingsPanel.skin()
    }

    /** A clock dialog for one of the two times of the approval window. */
    private fun pickClock(title: String, key: String, default: Int) {
        val minutes = Prefs.int(this, key, default).coerceIn(0, 1439)
        val dlg = TimePickerDialog(this, Sunlight.dialogTheme(), { _, h, m ->
            Prefs.setInt(this, key, h * 60 + m)
            extras.reload()
            settingsPanel.refresh()
        }, minutes / 60, minutes % 60, true)
        dlg.setTitle(title)
        Immersive.show(dlg)
    }

    /** The minutes left at which the voice warns, as numbers separated by commas. */
    private fun editWindowWarn() {
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(8), dp(16), dp(8))
        val hint = TextView(this)
        hint.text = tr("Whole minutes from 1 to 240, at most 8, for example 30,15,10,5,2,1. Empty means no warnings; the end of the window is always spoken.")
        hint.textSize = 12f
        body.addView(hint)
        val e = EditText(this)
        e.inputType = android.text.InputType.TYPE_CLASS_TEXT
        e.setText(Prefs.string(this, "winWarn", ApprovalWindow.DEFAULT_WARN))
        body.addView(e)
        Immersive.show(
            Sunlight.builder(this)
                .setTitle(tr("Warn at (minutes left)"))
                .setView(body)
                .setPositiveButton(tr("OK")) { _, _ ->
                    val marks = ApprovalWindow.parseWarn(e.text.toString())
                    if (marks == null) {
                        Toasts.make(this, tr("Use whole minutes from 1 to 240, at most 8, for example 30,15,10,5,2,1."), Toast.LENGTH_LONG).show()
                    } else {
                        Prefs.setString(this, "winWarn", ApprovalWindow.joinWarn(marks))
                        extras.reload()
                        settingsPanel.refresh()
                    }
                }
                .setNegativeButton(tr("Cancel"), null)
                .create()
        )
    }

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
        val dlg = Sunlight.builder(this)
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
            if (c == null || c < 1 || c > 16) Toasts.make(this, tr("The channel is 1 to 16"), Toast.LENGTH_SHORT).show()
            else if (h == null || l == null || h < 1000 || h > 2100 || l < 900 || l > h) Toasts.make(this, tr("Use microseconds, with the disarmed value not above the armed one"), Toast.LENGTH_LONG).show()
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
        hint.text = tr("Height comes from the GPS altitude relative to where you armed, so expect a few meters of error. The bar turns red when the current is above the limit line.")
        hint.textSize = 12f
        hint.setPadding(0, dp(8), 0, 0)
        body.addView(hint)
        val scroll = ScrollView(this)
        scroll.addView(body)
        val dlg = Sunlight.builder(this)
            .setTitle(tr("Flight statistics rows"))
            .setView(scroll)
            .setPositiveButton(tr("Save"), null)
            .setNegativeButton(tr("Cancel"), null)
            .create()
        Immersive.show(dlg)
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val a = limit.text.toString().trim().toIntOrNull()
            if (a == null || a < 5 || a > 300) {
                Toasts.make(this, tr("The current limit is 5 to 300 amps"), Toast.LENGTH_SHORT).show()
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
        fader.setOpacity(sticksView, floatArrayOf(0.35f, 0.65f, 0.95f)[Prefs.int(this, "sticksOpacity", 1).coerceIn(0, 2)])
        fader.set(sticksView, sticksEnabled)

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
                cols.text = tr("On      Channel      Name on the overlay                 Reverse")
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
        hint.text = tr("A bar fills with the channel value. Gray-blue = low, amber = middle, green = high. Names are written along the bar; a short name reads best.\n\nReverse: tick it for a switch that is reversed in the radio, so the bar shows the switch the way you flip it (up = full and green). Only the picture changes: sound and warning rules still use the real channel values.")
        hint.textSize = 12f
        hint.setPadding(0, dp(10), 0, 0)
        body.addView(hint)

        val scroll = ScrollView(this)
        scroll.addView(body)
        val dlg = Sunlight.builder(this)
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
                Toasts.make(this, bad, Toast.LENGTH_SHORT).show()
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
        val dlg = Sunlight.builder(this)
            .setTitle(tr("Stick view"))
            .setView(scroll)
            .setPositiveButton(tr("Save"), null)
            .setNegativeButton(tr("Cancel"), null)
            .create()
        Immersive.show(dlg)
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val values = listOf(roll, pitch, throttle, yaw).map { it.text.toString().trim().toIntOrNull() }
            if (values.any { it == null || it < 1 || it > 16 }) {
                Toasts.make(this, tr("Channels are numbers from 1 to 16"), Toast.LENGTH_SHORT).show()
            } else if (values.toSet().size < 4) {
                Toasts.make(this, tr("Each of the four needs its own channel"), Toast.LENGTH_SHORT).show()
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
        return tr("AX-HUD, version {0}\n{1}", version, sdk) + "\n" + tr("Thanks to RadioMaster for publishing the AX SDK.")
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
                Sunlight.builder(this)
                    .setTitle(tr("Sound rules saved"))
                    .setMessage(tr("This device has no file picker, so the file was saved here:\n{0}\n\nCopy it with a USB cable or adb. To import, put a file with the same name in that folder.", f.absolutePath))
                    .setPositiveButton("OK", null)
                    .create()
            )
        } catch (e: Exception) {
            Toasts.make(this, tr("Could not save the rules: {0}", e.message), Toast.LENGTH_LONG).show()
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
                Sunlight.builder(this)
                    .setTitle(title)
                    .setMessage(tr("This device has no file picker, so the file was saved here:\n{0}\n\nCopy it with a USB cable or adb. To import, put a file with the same name in that folder.", f.absolutePath))
                    .setPositiveButton("OK", null)
                    .create()
            )
        } catch (e: Exception) {
            Toasts.make(this, tr("Could not save the settings: {0}", e.message), Toast.LENGTH_LONG).show()
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
            Toasts.make(this, tr("Settings saved"), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toasts.make(this, tr("Could not save the settings: {0}", e.message), Toast.LENGTH_LONG).show()
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
                Toasts.make(this, tr("No file picker, and no {0} to import", f.absolutePath), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun readSettings(uri: Uri) {
        try {
            val stream = contentResolver.openInputStream(uri)
            if (stream == null) Toasts.make(this, tr("Could not open the file"), Toast.LENGTH_LONG).show()
            else askImportSettings(readTextLimited(stream))
        } catch (e: Exception) {
            Toasts.make(this, tr("Could not read the file: {0}", e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun askImportSettings(raw: String) {
        val summary = SettingsFile.inspect(raw)
        if (!summary.usable) {
            Immersive.show(Sunlight.builder(this).setTitle(tr("Cannot import")).setMessage(summary.message).setPositiveButton("OK", null).create())
            return
        }
        Immersive.show(
            Sunlight.builder(this)
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
        Toasts.make(this, message, Toast.LENGTH_LONG).show()
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
        lastTileUpdate = 0                              // the tick returns straight to the video, however editing was started
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

    // ------------------------------------------------------------------ start-up timings

    /** The last launches: how long until the first stick value reached the screen, and where the time went. */
    private fun showStartupTimings() {
        val log = StartupState.readLog(this)
        val text = TextView(this)
        text.typeface = android.graphics.Typeface.MONOSPACE
        text.textSize = 11f
        text.setPadding(dp(16), dp(8), dp(16), dp(8))
        text.text = tr("Milliseconds since the app started. \"graph\" is when the first stick value reached the screen. The newest launch is last.") + "\n\n" +
            (if (log.isBlank()) tr("No timings yet. Start the app, wait until the sticks move, and look again.") else log)
        val scroll = ScrollView(this)
        scroll.addView(text)
        Immersive.show(
            Sunlight.builder(this)
                .setTitle(tr("Start-up timings"))
                .setView(scroll)
                .setPositiveButton(tr("Close"), null)
                .setNegativeButton(tr("Clear")) { _, _ -> StartupState.clearLog(this) }
                .create()
        )
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
        val dlg = Sunlight.builder(this)
            .setTitle(tr("Live charts"))
            .setView(scroll)
            .setPositiveButton(tr("Save"), null)
            .setNegativeButton(tr("Cancel"), null)
            .create()
        Immersive.show(dlg)
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val typed = rows.map { it.name.text.toString().trim() }.filter { it.isNotEmpty() }
            if (typed.any { !Regex("[A-Za-z_][A-Za-z0-9_]{0,23}").matches(it) }) {
                Toasts.make(this, tr("Use names from the list, such as up_lq or cell."), Toast.LENGTH_LONG).show()
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
            Sunlight.builder(this)
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
        Toasts.make(this, tr("Aircraft: {0}", Aircraft.activeName(this)), Toast.LENGTH_LONG).show()
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
                inUse.text = tr("In use")
                inUse.textSize = 12f
                inUse.setPadding(dp(6), 0, dp(6), 0)
                line.addView(inUse)
            }
            val del = Button(this)
            del.text = "X"
            del.setOnClickListener {
                val err = store.remove(a.id)
                if (err != null) Toasts.make(this, err, Toast.LENGTH_LONG).show()
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
                Toasts.make(this, tr("At most {0} aircraft.", AircraftStore.MAX), Toast.LENGTH_SHORT).show()
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
        val dlg = Sunlight.builder(this)
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
                Toasts.make(this, problem, Toast.LENGTH_LONG).show()
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
        head.text = tr("Name                    Battery profile              mAh")
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
        val dlg = Sunlight.builder(this)
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
                Toasts.make(this, problem, Toast.LENGTH_LONG).show()
            } else {
                Battery.setPacks(this, out)
                Battery.applyActivePack(this)
                applyBattery()
                settingsPanel.refresh()
                dlg.dismiss()
            }
        }
    }

    // ------------------------------------------------------------------ exit

    /** Closes the app for good: the radio link stops, the radio's serial port is released, and the app leaves the recent apps. */
    private fun exitApp() {
        Immersive.show(
            Sunlight.builder(this)
                .setTitle(tr("Exit AX-HUD?"))
                .setMessage(tr("The radio link stops and the radio's serial port is released. There is no HUD and no voice until you start the app again."))
                .setPositiveButton(tr("Exit")) { _, _ -> finishAndRemoveTask() }
                .setNegativeButton(tr("Cancel"), null)
                .create()
        )
    }

    // ------------------------------------------------------------------ battery profiles

    private fun switchBattery() {
        Battery.cycle(this)
        applyBattery()
        Toasts.make(this, tr("Battery profile: {0}\n{1}", Battery.label(this), BatteryProfiles.limitsText(Battery.active(this))), Toast.LENGTH_LONG).show()
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
        head.text = tr("Name                  Cells    Full    Low    Critical")
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
        val dlg = Sunlight.builder(this)
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
                Toasts.make(this, problem, Toast.LENGTH_LONG).show()
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
                Toasts.make(this, tr("No file picker, and no {0} to import", f.absolutePath), Toast.LENGTH_LONG).show()
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
                Toasts.make(this, tr("Sound rules saved"), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toasts.make(this, tr("Could not save the rules: {0}", e.message), Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == requestImport) {
            try {
                val stream = contentResolver.openInputStream(uri)
                if (stream == null) Toasts.make(this, tr("Could not open the file"), Toast.LENGTH_LONG).show()
                else askImport(readTextLimited(stream))
            } catch (e: Exception) {
                Toasts.make(this, tr("Could not read the file: {0}", e.message), Toast.LENGTH_LONG).show()
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
            Immersive.show(Sunlight.builder(this).setTitle(tr("Cannot import")).setMessage(summary.message).setPositiveButton("OK", null).create())
            return
        }
        Immersive.show(
            Sunlight.builder(this)
            .setTitle(tr("Import sound rules"))
            .setMessage(tr("The file has {0}\n\nThis replaces your current rules. They are kept as a backup, and Settings can restore them.", summary.message))
            .setPositiveButton(tr("Import")) { _, _ ->
                Prefs.setRulesBackup(this, Prefs.rules(this))
                Prefs.setRules(this, text)
                reloadRules()
                Toasts.make(this, tr("Imported {0} rules", summary.rules), Toast.LENGTH_LONG).show()
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
