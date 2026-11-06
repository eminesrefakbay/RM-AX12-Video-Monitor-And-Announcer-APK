package dev.ax12.announcer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
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
    private lateinit var menuButton: Button
    private var menuPopupOpen = false
    private val hideMenuRunnable = Runnable {
        if (!menuPopupOpen && !Prefs.menuAlways(this)) menuButton.visibility = View.GONE
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
        menuButton.setOnClickListener { showMenu(it) }

        rulesPanel = RulesPanel(
            this, speaker,
            object : LiveValues {
                override fun numbers(): Map<String, Double> = latestNumbers
                override fun texts(): Map<String, String> = latestTexts
            },
            { reloadRules() },
            { startActivity(Intent(this, RulesActivity::class.java)) }
        )
        settingsPanel = SettingsPanel(this, settingsHost)
        val frame = findViewById<FrameLayout>(R.id.rootFrame)
        val full = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        frame.addView(rulesPanel.root, full)
        frame.addView(settingsPanel.root, FrameLayout.LayoutParams(full))

        applyMenuMode()
    }

    override fun onBackPressed() {
        if (settingsPanel.isShowing) settingsPanel.hide()
        else if (rulesPanel.isShowing) rulesPanel.hide()
        else super.onBackPressed()
    }

    // ------------------------------------------------------------------ menu button that hides itself

    /** Any touch on the screen brings the MENU button back for a few seconds. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) showMenuButton(4000)
        return super.dispatchTouchEvent(ev)
    }

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

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
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

    /** 20 times a second: NO VIDEO panel, read the radio, run the rules, refresh the debug panels. */
    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            if (lastTickAt != 0L) uiTiming.add(now, now - lastTickAt)
            lastTickAt = now

            val stale = now - camera.lastFrameMs > 1000
            noFeed.visibility = if (stale) View.VISIBLE else View.GONE
            if (stale) noFeedDetail.text = camera.statusLine()

            val useFake = testVisible || sdkLink == null || !sdkRunning
            val link: RadioLink = if (useFake) fakeLink else sdkLink!!
            val scaler = if (useFake) fakeScaler else sdkScaler
            val sample = link.poll()
            if (sample != null) {
                if (scaler.observe(sample.raw)) engine?.reset()      // the radio's unit became clear: start silent again
                val numbers = Variables.numbers(sample, scaler)
                latestNumbers = numbers
                latestTexts = sample.texts
                engine?.update(numbers, sample.texts, now)
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

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Sound rules")
        popup.menu.add(0, 2, 1, if (speaker.muted) "Speech is OFF - tap to turn on" else "Mute speech")
        popup.menu.add(0, 3, 2, "Settings")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> rulesPanel.show()
                2 -> speaker.muted = !speaker.muted
                3 -> settingsPanel.show()
            }
            true
        }
        menuPopupOpen = true
        popup.setOnDismissListener {
            menuPopupOpen = false
            showMenuButton(4000)
        }
        popup.show()
    }

    private fun chooseCamera() {
        val list = camera.listCameras()
        if (list.isEmpty()) {
            Toast.makeText(this, "Android lists no cameras", Toast.LENGTH_LONG).show()
            return
        }
        val names = Array(list.size) { list[it].second }
        AlertDialog.Builder(this)
            .setTitle("Choose the HDMI camera")
            .setItems(names) { _, which -> camera.select(list[which].first) }
            .show()
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
            AlertDialog.Builder(this)
                .setTitle("Sound rules saved")
                .setMessage("This device has no file picker, so the file was saved here:\n" + f.absolutePath +
                        "\n\nCopy it with a USB cable or adb. To import, put a file with the same name in that folder.")
                .setPositiveButton("OK", null)
                .show()
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
            AlertDialog.Builder(this).setTitle("Cannot import").setMessage(summary.message).setPositiveButton("OK", null).show()
            return
        }
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
            .show()
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
