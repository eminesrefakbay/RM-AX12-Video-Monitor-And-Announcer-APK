package dev.ax12.announcer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
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
        findViewById<Button>(R.id.menuButton).setOnClickListener { showMenu(it) }
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
                engine?.update(numbers, sample.texts, now)
                if (testVisible) {
                    val sb = StringBuilder()
                    for (i in 0 until 16) sb.append("ch").append(i + 1).append("=").append(numbers["ch" + (i + 1)]!!.toInt())
                        .append(if (i % 4 == 3) "\n" else "  ")
                    status.text = sb.toString().trim() + "\n" + link.name + ": " + sample.info + "\n" + camera.statusLine()
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
        sb.append(sample.info).append("\n\n")
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
        popup.menu.add(0, 1, 0, "Edit sound rules")
        popup.menu.add(0, 2, 1, "Choose camera")
        popup.menu.add(0, 3, 2, "Rotate video (now " + camera.rotation + " deg)")
        popup.menu.add(0, 4, 3, if (testVisible) "Hide test channels (use the radio)" else "Test channels (instead of the radio)")
        popup.menu.add(0, 5, 4, "Speak test")
        popup.menu.add(0, 6, 5, "Scale: " + (if (camera.fill) "fill (stretch)" else "fit") + "  - tap to change")
        popup.menu.add(0, 7, 6, if (varsVisible) "Hide variables" else "Show variables")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> startActivity(Intent(this, RulesActivity::class.java))
                2 -> chooseCamera()
                3 -> camera.rotateNext()
                4 -> setTestVisible(!testVisible)
                5 -> speaker.say("Sound test", true)
                6 -> camera.toggleFill()
                7 -> setVarsVisible(!varsVisible)
            }
            true
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
