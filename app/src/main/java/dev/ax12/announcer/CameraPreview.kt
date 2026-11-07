// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView

/**
 * Camera2 preview on a TextureView, built for an HDMI-in "camera".
 *  - picks the camera you chose (or an external one, or the first one);
 *  - keeps retrying when the camera disappears (cable pulled, VRX off);
 *  - [lastFrameMs] is refreshed for every frame, so the app can tell "no video";
 *  - the picture can be rotated in 90-degree steps and the choice is remembered.
 *
 * If your tested Camera2 test app already handles the HDMI source in a special way,
 * paste that part into openCamera() / startSession().
 */
class CameraPreview(private val activity: Activity, private val view: TextureView) {

    private val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val main = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var running = false
    private var bufW = 1920
    private var bufH = 1080

    @Volatile var lastFrameMs = 0L
        private set
    @Volatile private var message = tr("Starting...")

    var rotation: Int = Prefs.rotation(activity)
        private set
    var cameraId: String? = Prefs.cameraId(activity)
        private set

    /** false = fit (keep the picture's own shape); true = stretch to fill the view.
     *  Some HDMI sources arrive rotated AND stretched; turn the picture back with Rotate, then use Fill. */
    var fill: Boolean = Prefs.fill(activity)
        private set

    private val timing = TimingStats(5000)

    /** How the video is arriving, from the app's side: frame rate and the gaps between frames. */
    fun videoStats(): String {
        val s = timing.summary(SystemClock.elapsedRealtime())
        return String.format(java.util.Locale.US, tr("Video: %.1f frames/s   gap between frames: 95%% under %d ms, worst %d ms (last 5 s)"),
            s.perSecond, s.p95Ms, s.maxMs)
    }

    private val retry = Runnable { if (running) openCamera() }

    init {
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                if (running) openCamera()
                applyTransform()
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) { applyTransform() }
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                val now = SystemClock.elapsedRealtime()
                if (lastFrameMs != 0L) timing.add(now, now - lastFrameMs)
                lastFrameMs = now
            }
        }
    }

    fun start() {
        if (running) return
        running = true
        thread = HandlerThread("camera").also { it.start() }
        handler = Handler(thread!!.looper)
        if (view.isAvailable) openCamera()
    }

    fun stop() {
        running = false
        main.removeCallbacks(retry)
        closeCamera()
        thread?.quitSafely()
        thread = null
        handler = null
    }

    /** One short line for the status bar shown when there is no video. */
    /** What the camera is doing or what is wrong, in words (shown on the no-video screen). */
    fun status(): String = message

    fun barText(): String = tr("Camera {0}  {1}x{2}  \u00B7  {3}", (cameraId ?: tr("Auto")), bufW, bufH, message)

    /** One line for the "NO VIDEO" panel. */
    fun statusLine(): String = tr("Camera {0}   buffer {1}x{2}   rotation {3} {4}   view {5}x{6}\n{7}", (cameraId ?: tr("Auto")), bufW, bufH, rotation, (if (fill) tr("Fill") else tr("Fit")), view.width, view.height, message)

    /** (id, description) of every camera Android shows. Pick the HDMI input here. */
    fun listCameras(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for (id in manager.cameraIdList) {
            val c = manager.getCameraCharacteristics(id)
            val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> "front"
                CameraCharacteristics.LENS_FACING_BACK -> "back"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
                else -> "?"
            }
            val orient = c.get(CameraCharacteristics.SENSOR_ORIENTATION)
            out.add(Pair(id, tr("Camera {0}  ({1}, sensor {2} deg)", id, facing, orient)))
        }
        return out
    }

    fun select(id: String) {
        Prefs.setCameraId(activity, id)
        cameraId = id
        main.removeCallbacks(retry)
        closeCamera()
        if (running) openCamera()
    }

    fun toggleFill() {
        fill = !fill
        Prefs.setFill(activity, fill)
        applyTransform()
    }

    fun rotateNext() {
        rotation = (rotation + 90) % 360
        Prefs.setRotation(activity, rotation)
        applyTransform()
    }

    // ------------------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        if (!running || device != null) return
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            message = tr("Camera permission needed")
            return
        }
        val id = chooseCameraId()
        if (id == null) {
            message = tr("No camera found - check the HDMI cable and the video receiver")
            scheduleRetry()
            return
        }
        try {
            chooseSize(id)
            message = tr("Opening camera {0}...", id)
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) {
                    device = d
                    startSession(d)
                }
                override fun onDisconnected(d: CameraDevice) {
                    d.close()
                    device = null
                    message = tr("Camera disconnected")
                    scheduleRetry()
                }
                override fun onError(d: CameraDevice, error: Int) {
                    d.close()
                    device = null
                    message = tr("Camera error {0}", error)
                    scheduleRetry()
                }
            }, handler)
        } catch (e: Exception) {
            message = tr("Cannot open camera: {0}", e.message)
            scheduleRetry()
        }
    }

    private fun startSession(d: CameraDevice) {
        val st = view.surfaceTexture
        if (st == null) {
            message = tr("No surface yet")
            scheduleRetry()
            return
        }
        st.setDefaultBufferSize(bufW, bufH)
        val surface = Surface(st)
        try {
            val request = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            request.addTarget(surface)
            d.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    session = s
                    try {
                        s.setRepeatingRequest(request.build(), null, handler)
                        message = tr("Waiting for video...")
                    } catch (e: Exception) {
                        message = tr("Preview failed: {0}", e.message)
                        scheduleRetry()
                    }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) {
                    message = tr("Camera session failed")
                    scheduleRetry()
                }
            }, handler)
        } catch (e: Exception) {
            message = tr("Session error: {0}", e.message)
            scheduleRetry()
        }
        main.post { applyTransform() }
    }

    private fun chooseCameraId(): String? {
        val ids = manager.cameraIdList
        val saved = cameraId
        if (saved != null && ids.contains(saved)) return saved
        for (id in ids) {
            val facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) return id
        }
        return if (ids.isNotEmpty()) ids[0] else null
    }

    private fun chooseSize(id: String) {
        val map = manager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(SurfaceTexture::class.java)
        if (sizes != null && sizes.isNotEmpty()) {
            var best = sizes[0]
            var bestArea = -1L
            for (s in sizes) {
                val area = s.width.toLong() * s.height
                if (s.width <= 1920 && s.height <= 1080 && area > bestArea) {
                    best = s
                    bestArea = area
                }
            }
            bufW = best.width
            bufH = best.height
        }
    }

    private fun scheduleRetry() {
        closeCamera()
        main.removeCallbacks(retry)
        main.postDelayed(retry, 2000)
    }

    private fun closeCamera() {
        try { session?.close() } catch (e: Exception) { }
        session = null
        try { device?.close() } catch (e: Exception) { }
        device = null
    }

    /** Rotate the picture, either fitted inside the view or stretched to fill it. */
    fun applyTransform() {
        val vw = view.width.toFloat()
        val vh = view.height.toFloat()
        if (vw == 0f || vh == 0f) return
        val rot = ((rotation % 360) + 360) % 360
        val cx = vw / 2f
        val cy = vh / 2f
        val m = Matrix()
        if (fill) {
            // The texture is already stretched over the whole view. After a 90/270 turn it would sit
            // sideways, so swap the stretch first: the turned picture then fills the view exactly.
            if (rot % 180 == 0) m.setScale(1f, 1f, cx, cy) else m.setScale(vh / vw, vw / vh, cx, cy)
        } else {
            val dispW = if (rot % 180 == 0) bufW.toFloat() else bufH.toFloat()
            val dispH = if (rot % 180 == 0) bufH.toFloat() else bufW.toFloat()
            val f = minOf(vw / dispW, vh / dispH)
            // Undo the stretch of the texture, then turn about the centre.
            m.setScale(bufW * f / vw, bufH * f / vh, cx, cy)
        }
        m.postRotate(rot.toFloat(), cx, cy)
        view.setTransform(m)
    }
}
