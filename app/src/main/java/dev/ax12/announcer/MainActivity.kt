package dev.ax12.announcer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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

/**
 * Full-screen video; grey "NO VIDEO" panel when no frames arrive; spoken announcements
 * from the sound rules. Everything the radio does stays untouched: this app only reads.
 */
class MainActivity : Activity() {

    private lateinit var texture: TextureView
    private lateinit var noFeed: View
    private lateinit var noFeedDetail: TextView
    private lateinit var status: TextView
    private lateinit var testPanel: View
    private lateinit var camera: CameraPreview
    private lateinit var speaker: Speaker

    private var engine: RuleEngine? = null
    private val fake = FakeChannelSource()
    private val sdk = AxSdkChannelSource()
    private var testVisible = false
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        texture = findViewById(R.id.texture)
        noFeed = findViewById(R.id.noFeed)
        noFeedDetail = findViewById(R.id.noFeedDetail)
        status = findViewById(R.id.status)
        testPanel = findViewById(R.id.testPanel)

        speaker = Speaker(this)
        camera = CameraPreview(this, texture)

        buildTestPanel()
        findViewById<Button>(R.id.menuButton).setOnClickListener { showMenu(it) }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        reloadRules()
        if (hasCameraPermission()) camera.start() else requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        ui.post(tick)
    }

    override fun onPause() {
        ui.removeCallbacks(tick)
        camera.stop()
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

    private fun reloadRules() {
        val parsed = RuleParser.parse(Prefs.rules(this))
        engine = RuleEngine(parsed, speaker)          // a new engine starts silent
        if (parsed.errors.isNotEmpty()) {
            Toast.makeText(this, "Sound rules: " + parsed.errors.size + " error(s). Open the rules editor.", Toast.LENGTH_LONG).show()
        }
    }

    /** 20 times a second: show or hide the NO VIDEO panel, read channels, run the rules. */
    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()

            val stale = now - camera.lastFrameMs > 1000
            noFeed.visibility = if (stale) View.VISIBLE else View.GONE
            if (stale) noFeedDetail.text = camera.statusLine()

            val source: ChannelSource = if (testVisible || !sdk.available) fake else sdk
            val v = source.read()
            if (v != null) {
                val map = HashMap<String, Double>()
                for (i in v.indices) map["ch" + (i + 1)] = v[i]
                engine?.update(map, now)
                if (testVisible) {
                    val sb = StringBuilder()
                    for (i in v.indices) sb.append("ch").append(i + 1).append("=").append(v[i].toInt()).append(if (i % 4 == 3) "\n" else "  ")
                    status.text = sb.toString().trim() + "\n" + camera.statusLine()
                }
            }
            ui.postDelayed(this, 50)
        }
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Edit sound rules")
        popup.menu.add(0, 2, 1, "Choose camera")
        popup.menu.add(0, 3, 2, "Rotate video (now " + camera.rotation + " deg)")
        popup.menu.add(0, 4, 3, if (testVisible) "Hide test channels" else "Test channels")
        popup.menu.add(0, 5, 4, "Speak test")
        popup.menu.add(0, 6, 5, "Scale: " + (if (camera.fill) "fill (stretch)" else "fit") + "  - tap to change")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> startActivity(android.content.Intent(this, RulesActivity::class.java))
                2 -> chooseCamera()
                3 -> camera.rotateNext()
                4 -> setTestVisible(!testVisible)
                5 -> speaker.say("Sound test", true)
                6 -> camera.toggleFill()
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
                    fake.values[i] = us.toDouble()
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
