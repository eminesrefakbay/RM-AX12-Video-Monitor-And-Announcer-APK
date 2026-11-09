// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScreenCapture {
    fun captureAndSave(activity: Activity, onSaved: () -> Unit, onFailure: () -> Unit) {
        val decor = activity.window.decorView
        val width = decor.width
        val height = decor.height
        if (width <= 0 || height <= 0) {
            onFailure()
            return
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PixelCopy.request(
                activity.window,
                bitmap,
                PixelCopy.OnPixelCopyFinishedListener { result ->
                    if (result == PixelCopy.SUCCESS) saveAsync(activity.applicationContext, bitmap, onSaved, onFailure)
                    else {
                        bitmap.recycle()
                        onFailure()
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } else {
            decor.draw(Canvas(bitmap))
            saveAsync(activity.applicationContext, bitmap, onSaved, onFailure)
        }
    }

    private fun saveAsync(context: Context, bitmap: Bitmap, onSaved: () -> Unit, onFailure: () -> Unit) {
        Thread({
            try {
                save(context, bitmap)
                Handler(Looper.getMainLooper()).post(onSaved)
            } catch (e: IOException) {
                Handler(Looper.getMainLooper()).post(onFailure)
            } catch (e: SecurityException) {
                Handler(Looper.getMainLooper()).post(onFailure)
            } finally {
                bitmap.recycle()
            }
        }, "screenshot-save").start()
    }

    @Throws(IOException::class)
    private fun save(context: Context, bitmap: Bitmap) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val fileName = "AX-HUD-$stamp.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStore(context, bitmap, fileName)
        } else {
            saveToPictures(context, bitmap, fileName)
        }
    }

    @Throws(IOException::class)
    private fun saveToMediaStore(context: Context, bitmap: Bitmap, fileName: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AX-HUD")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create screenshot in MediaStore")
        try {
            val stream = resolver.openOutputStream(uri) ?: throw IOException("Could not open screenshot output")
            stream.use {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Could not encode screenshot")
            }
            val ready = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            if (resolver.update(uri, ready, null, null) <= 0) throw IOException("Could not finish saving screenshot")
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            throw e
        } catch (e: SecurityException) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    @Throws(IOException::class)
    private fun saveToPictures(context: Context, bitmap: Bitmap, fileName: String) {
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AX-HUD")
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Could not create screenshot folder")
        val file = File(directory, fileName)
        FileOutputStream(file).use {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Could not encode screenshot")
        }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
    }
}
