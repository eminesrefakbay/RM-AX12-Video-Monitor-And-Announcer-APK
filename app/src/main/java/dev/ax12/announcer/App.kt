package dev.ax12.announcer

import android.app.Application

/** Loads the language before any screen is built. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Lang.apply(this)
    }
}
