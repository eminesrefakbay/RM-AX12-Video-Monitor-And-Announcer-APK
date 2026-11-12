// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import java.util.ArrayDeque
import java.util.UUID

/**
 * The Bluetooth LE link to the antenna tracker (see [TrackerProtocol]): this app is the central and keeps a connection to one tracker, writes
 * the aircraft packet and commands to it, and reads its status notifications. A tracker that was chosen once is connected by its address, so
 * no search (and no location permission) is needed any more. If the connection drops, it is made again, a little slower each time.
 */
class TrackerLink(private val context: Context) {
    enum class State { OFF, SEARCHING, CONNECTING, CONNECTED, LOST }

    @Volatile var state = State.OFF
        private set
    @Volatile var status: TrackerProtocol.Status? = null
        private set
    @Volatile var statusAtMs = 0L
        private set
    @Volatile var deviceName = ""
        private set

    /** Called on the main thread when the state or the status changed. */
    var onChange: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private var gatt: BluetoothGatt? = null
    private var aircraftChar: BluetoothGattCharacteristic? = null
    private var commandChar: BluetoothGattCharacteristic? = null
    private var address = ""
    private var wanted = false
    private var backoffMs = BACKOFF_START_MS
    @Volatile private var writeBusy = false
    private val commands = ArrayDeque<ByteArray>()
    private var scanCallback: ScanCallback? = null
    private val reconnect = Runnable { open() }

    /** True when this radio has Bluetooth LE. */
    fun available(): Boolean = adapter != null && context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    /** True when Bluetooth is switched on. */
    fun enabled(): Boolean = adapter?.isEnabled == true

    private fun set(s: State) {
        if (state == s) return
        state = s
        main.post { onChange?.invoke() }
    }

    /** Keeps a connection to the tracker with this address (switches if it was another one). */
    fun connectTo(addr: String, name: String) {
        if (addr != address) { closeGatt(); status = null }
        address = addr
        deviceName = name
        wanted = true
        backoffMs = BACKOFF_START_MS
        main.removeCallbacks(reconnect)
        main.post { open() }
    }

    /** Lets the tracker go (the tracker holds its last position). */
    fun disconnect() {
        wanted = false
        main.removeCallbacks(reconnect)
        closeGatt()
        set(State.OFF)
    }

    private fun open() {
        if (!wanted || address.isEmpty()) return
        val a = adapter
        if (a == null || !a.isEnabled) {
            set(State.LOST)
            main.postDelayed(reconnect, backoffMs)
            backoffMs = minOf(backoffMs * 2, BACKOFF_MAX_MS)
            return
        }
        closeGatt()
        try {
            val device = a.getRemoteDevice(address)
            set(State.CONNECTING)
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            Diagnostics.event("tracker", "could not connect: " + e.javaClass.simpleName, "warn")
            lost()
        }
    }

    private fun closeGatt() {
        writeBusy = false
        aircraftChar = null
        commandChar = null
        val g = gatt
        gatt = null
        if (g != null) {
            try { g.disconnect() } catch (e: Exception) { /* already gone */ }
            try { g.close() } catch (e: Exception) { /* already gone */ }
        }
    }

    private fun lost() {
        closeGatt()
        if (!wanted) return
        set(State.LOST)
        main.removeCallbacks(reconnect)
        main.postDelayed(reconnect, backoffMs)
        backoffMs = minOf(backoffMs * 2, BACKOFF_MAX_MS)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, st: Int, newState: Int) {
            if (g !== gatt) return
            if (newState == BluetoothProfile.STATE_CONNECTED && st == BluetoothGatt.GATT_SUCCESS) {
                backoffMs = BACKOFF_START_MS
                if (!g.discoverServices()) main.post { lost() }
            } else {
                main.post { if (g === gatt) lost() }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, st: Int) {
            if (g !== gatt) return
            val svc = g.getService(UUID.fromString(TrackerProtocol.SERVICE))
            val air = svc?.getCharacteristic(UUID.fromString(TrackerProtocol.AIRCRAFT))
            val stat = svc?.getCharacteristic(UUID.fromString(TrackerProtocol.STATUS))
            val cmd = svc?.getCharacteristic(UUID.fromString(TrackerProtocol.COMMAND))
            if (st != BluetoothGatt.GATT_SUCCESS || air == null || stat == null || cmd == null) {
                Diagnostics.event("tracker", "the device is not an AX-HUD tracker (its service was not found)", "warn")
                main.post { if (g === gatt) lost() }
                return
            }
            aircraftChar = air
            commandChar = cmd
            g.setCharacteristicNotification(stat, true)
            val cccd = stat.getDescriptor(CCCD)
            if (cccd == null) { main.post { if (g === gatt) lost() }; return }
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            writeBusy = true
            if (!g.writeDescriptor(cccd)) { writeBusy = false; main.post { if (g === gatt) lost() } }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, st: Int) {
            if (g !== gatt) return
            writeBusy = false
            if (st == BluetoothGatt.GATT_SUCCESS) {
                Diagnostics.event("tracker", "connected to the tracker")
                set(State.CONNECTED)
            } else {
                main.post { if (g === gatt) lost() }
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (g !== gatt || c.uuid != UUID.fromString(TrackerProtocol.STATUS)) return
            val s = TrackerProtocol.decodeStatus(c.value) ?: return
            status = s
            statusAtMs = SystemClock.elapsedRealtime()
            main.post { onChange?.invoke() }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, st: Int) {
            writeBusy = false
            main.post { pump() }
        }
    }

    /** Sends the next command, if there is one and the link is free. */
    private fun pump(): Boolean {
        val g = gatt
        val c = commandChar
        if (state != State.CONNECTED || g == null || c == null || writeBusy) return false
        val cmd: ByteArray = synchronized(commands) { commands.peekFirst() } ?: return false
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        c.value = cmd
        writeBusy = true
        val ok = try { g.writeCharacteristic(c) } catch (e: Exception) { false }
        if (!ok) { writeBusy = false; return false }
        synchronized(commands) { commands.pollFirst() }
        return true
    }

    /** Queues a command for the tracker; it is sent when the link is free (and kept until the tracker is connected). */
    fun sendCommand(bytes: ByteArray) {
        synchronized(commands) {
            if (commands.size >= MAX_QUEUED_COMMANDS) commands.pollFirst()
            commands.addLast(bytes)
        }
        main.post { pump() }
    }

    /** Sends the aircraft packet if the link is free; false when it was skipped (the next one follows 200 ms later). */
    fun sendAircraft(packet: ByteArray): Boolean {
        val g = gatt
        val c = aircraftChar
        if (state != State.CONNECTED || g == null || c == null || writeBusy) return false
        if (synchronized(commands) { commands.isNotEmpty() } && pump()) return false     // a command goes first
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        c.value = packet
        writeBusy = true
        val ok = try { g.writeCharacteristic(c) } catch (e: Exception) { false }
        if (!ok) writeBusy = false
        return ok
    }

    /** One tracker found by a search. */
    class Found(val address: String, val name: String, val rssi: Int)

    /** Searches for trackers for a few seconds. Needs the location permission (Android asks for it to search for Bluetooth devices). */
    fun search(onFound: (Found) -> Unit, onFinished: (started: Boolean) -> Unit, durationMs: Long = SEARCH_MS) {
        val scanner = try { adapter?.bluetoothLeScanner } catch (e: Exception) { null }
        if (scanner == null || !enabled()) { onFinished(false); return }
        stopSearch()
        val seen = HashSet<String>()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val rec = result.scanRecord
                val name = rec?.deviceName ?: result.device.name ?: ""
                val isTracker = rec?.serviceUuids?.contains(ParcelUuid.fromString(TrackerProtocol.SERVICE)) == true || name == TrackerProtocol.DEVICE_NAME
                if (isTracker && seen.add(result.device.address)) onFound(Found(result.device.address, if (name.isEmpty()) TrackerProtocol.DEVICE_NAME else name, result.rssi))
            }

            override fun onScanFailed(errorCode: Int) {
                Diagnostics.event("tracker", "the search could not start (error " + errorCode + ")", "warn")
                if (scanCallback === this) { scanCallback = null; onFinished(false) }
            }
        }
        scanCallback = cb
        try {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
        } catch (e: Exception) {
            scanCallback = null
            Diagnostics.event("tracker", "the search could not start: " + e.javaClass.simpleName, "warn")
            onFinished(false)
            return
        }
        main.postDelayed({
            if (scanCallback === cb) { stopSearch(); onFinished(true) }
        }, durationMs)
    }

    fun stopSearch() {
        val cb = scanCallback ?: return
        scanCallback = null
        try { adapter?.bluetoothLeScanner?.stopScan(cb) } catch (e: Exception) { /* Bluetooth was switched off */ }
    }

    companion object {
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val BACKOFF_START_MS = 2000L
        const val BACKOFF_MAX_MS = 10000L
        const val SEARCH_MS = 12000L
        const val MAX_QUEUED_COMMANDS = 8
    }
}

/** What the Settings buttons ask the tracker to do. */
object TrackerKind {
    const val AUTO = 0
    const val HOLD = 1
    const val CENTRE = 2
    const val SWEEP = 3
    const val CAL_START = 4
    const val CAL_STOP = 5
}
