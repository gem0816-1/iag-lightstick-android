package com.iag.lightstick

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.bluetooth.BluetoothStatusCodes
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.UUID

data class StickCandidate(
    val address: String,
    val name: String,
    val rssi: Int,
    val isKnownIag: Boolean,
)

/** IAG BLE controller: ED00/ED01, 5A A5 04 00 01 R G B X. */
class LightStickController(
    context: Context,
    private val log: (String) -> Unit,
    private val onStateChanged: (State, String) -> Unit,
    private val onDevicesChanged: (List<StickCandidate>) -> Unit,
) {
    companion object {
        const val TARGET_NAME = "EMB_4745"
        val SERVICE_UUID: UUID = UUID.fromString("0000ed00-0000-1000-8000-00805f9b34fb")
        val CHAR_UUID: UUID = UUID.fromString("0000ed01-0000-1000-8000-00805f9b34fb")
        private const val SCAN_DURATION_MS = 60_000L
        private const val WRITE_INTERVAL_MS = 90L
    }

    enum class State { IDLE, SCANNING, CONNECTING, CONNECTED, DISCONNECTED }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val discovered = linkedMapOf<String, StickCandidate>()

    @Volatile var state: State = State.IDLE
        private set
    @Volatile var currentColor: String = "#34CFFF"
        private set
    @Volatile private var currentOffByte: Int = 0
    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var controlChar: BluetoothGattCharacteristic? = null
    private var connectedAddress = ""
    private var connectedName = ""
    private var manuallyDisconnected = false
    private var reconnectAttempts = 0
    private var closed = false
    private var reconnectTask: Runnable? = null
    private var scanTimeout: Runnable? = null
    private var keepAliveEnabled = false
    private var lastWriteAt = 0L
    private var writePending = false
    private var pendingFrame: ByteArray? = null
    private var writeTimeout: Runnable? = null

    private val writePump = object : Runnable {
        override fun run() = pumpWrite()
    }
    private val keepAlive = object : Runnable {
        override fun run() {
            if (!keepAliveEnabled || state != State.CONNECTED) return
            pumpWrite()
            handler.postDelayed(this, WRITE_INTERVAL_MS)
        }
    }

    private fun publishState(next: State) {
        if (closed) return
        state = next
        val name = if (next == State.CONNECTED) connectedName else connectedName
        handler.post { if (!closed) onStateChanged(next, name) }
        log("设备状态：$next${if (name.isNotBlank()) " · $name" else ""}")
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (closed || !hasConnectPermission()) { log("请先授予蓝牙权限"); return }
        val bt = adapter
        if (bt == null) {
            log("此设备没有蓝牙适配器")
            publishState(State.DISCONNECTED)
            return
        }
        val enabled = try { bt.isEnabled } catch (_: SecurityException) { false }
        if (!enabled) {
            log("蓝牙未开启，请打开蓝牙后重新搜索")
            publishState(State.DISCONNECTED)
            return
        }
        stopScan()
        discovered.clear()
        handler.post { onDevicesChanged(emptyList()) }
        publishState(State.SCANNING)
        val scanner = try { bt.bluetoothLeScanner } catch (_: SecurityException) { null }
        if (scanner == null) {
            log("BLE 扫描器不可用")
            publishState(State.DISCONNECTED)
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        log("开始搜索 IAG / EMB 荧光棒（最长 60 秒）")
        scanTimeout = Runnable {
            runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
            scanTimeout = null
            if (state == State.SCANNING) publishState(State.IDLE)
            log(if (discovered.isEmpty()) "搜索结束：未发现 IAG 荧光棒，请重新给荧光棒上电后再试" else "搜索结束：可从设备列表连接")
        }.also { handler.postDelayed(it, SCAN_DURATION_MS) }
        try {
            scanner.startScan(null, settings, scanCallback)
        } catch (e: SecurityException) {
            log("没有蓝牙扫描权限：${e.message}")
            stopScan()
            publishState(State.DISCONNECTED)
        } catch (e: Exception) {
            log("启动 BLE 搜索失败：${e.message}")
            stopScan()
            publishState(State.DISCONNECTED)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanTimeout?.let { handler.removeCallbacks(it) }
        scanTimeout = null
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handler.post { handleScanResult(result) }
        }

        private fun handleScanResult(result: ScanResult) {
            if (closed || state != State.SCANNING) return
            val device = result.device
            val address = device.address ?: return
            val name = runCatching { device.name.orEmpty() }.getOrDefault("")
                .ifBlank { result.scanRecord?.deviceName.orEmpty() }
            val advertisesService = result.scanRecord?.serviceUuids?.any { it.uuid == SERVICE_UUID } == true
            val known = name.contains("EMB", true) || name.contains("IAG", true) || advertisesService
            if (!known) return

            val candidate = StickCandidate(
                address = address,
                name = name.ifBlank { if (advertisesService) TARGET_NAME else "IAG 荧光棒" },
                rssi = result.rssi,
                isKnownIag = name.contains("EMB", true) || advertisesService,
            )
            val previous = discovered[address]
            discovered[address] = candidate
            if (previous == null) log("发现设备：${candidate.name} · ${candidate.address} · ${candidate.rssi} dBm")
            handler.post {
                onDevicesChanged(
                    discovered.values.sortedWith(
                        compareByDescending<StickCandidate> { it.isKnownIag }
                            .thenByDescending { it.rssi }
                    )
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            if (closed) return
            log("BLE 搜索失败，错误码 $errorCode")
            stopScan()
            publishState(State.DISCONNECTED)
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(candidate: StickCandidate) {
        if (closed) return
        reconnectTask?.let(handler::removeCallbacks)
        reconnectTask = null
        stopScan()
        manuallyDisconnected = false
        reconnectAttempts = 0
        connectedAddress = candidate.address
        connectedName = candidate.name
        openGatt()
    }

    @SuppressLint("MissingPermission")
    private fun openGatt() {
        val bt = adapter
        val enabled = try { bt != null && hasConnectPermission() && bt.isEnabled } catch (_: SecurityException) { false }
        if (closed || bt == null || !enabled) {
            log("蓝牙不可用或未开启")
            publishState(State.DISCONNECTED)
            return
        }
        closeGatt()
        controlChar = null
        publishState(State.CONNECTING)
        try {
            val device = bt.getRemoteDevice(connectedAddress)
            log("正在连接 ${connectedName.ifBlank { connectedAddress }} …")
            gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK, handler)
        } catch (e: Exception) {
            log("连接失败：${e.message}")
            publishState(State.DISCONNECTED)
            scheduleReconnect()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (g !== gatt) {
                closeDevice(g)
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                log("BLE 已连接，正在读取荧光棒服务…")
                publishState(State.CONNECTING)
                @SuppressLint("MissingPermission")
                runCatching { g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
                @SuppressLint("MissingPermission")
                val started = runCatching { g.discoverServices() }.getOrDefault(false)
                if (!started) {
                    log("启动 GATT 服务发现失败")
                    publishState(State.DISCONNECTED)
                    if (!manuallyDisconnected) scheduleReconnect()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("BLE 连接中断（status=$status）")
                controlChar = null
                synchronized(this@LightStickController) { writePending = false; pendingFrame = null }
                closeDevice(g)
                if (g === gatt) gatt = null
                if (keepAliveEnabled) {
                    handler.removeCallbacks(keepAlive)
                    keepAliveEnabled = false
                }
                publishState(State.DISCONNECTED)
                if (!manuallyDisconnected && connectedAddress.isNotBlank()) scheduleReconnect()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log("读取 GATT 服务失败，status=$status")
                publishState(State.DISCONNECTED)
                if (!manuallyDisconnected) scheduleReconnect()
                return
            }
            val service: BluetoothGattService? = g.getService(SERVICE_UUID)
            val characteristic = service?.getCharacteristic(CHAR_UUID)
            if (service == null || characteristic == null) {
                log("设备缺少 ED00/ED01 控制服务；服务列表：${g.services.joinToString { it.uuid.toString() }}")
                publishState(State.DISCONNECTED)
                if (!manuallyDisconnected) scheduleReconnect()
                return
            }
            controlChar = characteristic
            reconnectAttempts = 0
            currentOffByte = 0
            publishState(State.CONNECTED)
            log("IAG 控制通道 ED00/ED01 已就绪")
            setColorHex(currentColor)
            setKeepAlive(true)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            handler.post {
                if (g !== gatt) return@post
                writeTimeout?.let { handler.removeCallbacks(it) }
                writeTimeout = null
                synchronized(this@LightStickController) { writePending = false }
                if (status != BluetoothGatt.GATT_SUCCESS) log("荧光棒写入失败，status=$status")
                scheduleWritePump()
            }
        }
    }

    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun closeDevice(device: BluetoothGatt) {
        try { device.close() }
        catch (_: SecurityException) { if (!closed) log("蓝牙权限已撤销，连接已停止") }
    }

    private fun scheduleReconnect() {
        if (closed || manuallyDisconnected || connectedAddress.isBlank() || reconnectTask != null) return
        if (reconnectAttempts >= 5) { log("自动重连次数已用完，请重新搜索并连接"); return }
        reconnectAttempts++
        val delay = minOf(6_000L, 800L + reconnectAttempts * 900L)
        log("${delay / 1000.0} 秒后自动重连（${reconnectAttempts}/5）")
        reconnectTask = Runnable {
            reconnectTask = null
            if (!closed && !manuallyDisconnected && state == State.DISCONNECTED) openGatt()
        }.also { handler.postDelayed(it, delay) }
    }

    private fun closeGatt() {
        handler.removeCallbacks(writePump)
        writeTimeout?.let { handler.removeCallbacks(it) }
        writeTimeout = null
        synchronized(this) { writePending = false; pendingFrame = null }
        val old = gatt
        gatt = null
        if (old != null) {
            try { old.disconnect() } catch (_: SecurityException) { if (!closed) log("蓝牙权限已撤销") }
            closeDevice(old)
        }
    }

    fun setColor(red: Int, green: Int, blue: Int, offByte: Int = 0) {
        currentColor = "#%02X%02X%02X".format(
            red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255)
        )
        currentOffByte = offByte and 0xFF
        if (offByte == 0x04) setKeepAlive(false)
        enqueueFrame(colorFrame(currentColor, currentOffByte))
        log("灯色设为 $currentColor")
    }

    fun setColorHex(hex: String) {
        val clean = hex.removePrefix("#")
        if (clean.length != 6) return
        setColor(clean.substring(0, 2).toInt(16), clean.substring(2, 4).toInt(16), clean.substring(4, 6).toInt(16))
    }

    /** Timeline path: color frame without per-frame logging (keep-alive carries the latest). */
    fun sendColorFrame(red: Int, green: Int, blue: Int) {
        currentColor = "#%02X%02X%02X".format(
            red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255)
        )
        currentOffByte = 0
        enqueueFrame(colorFrame(currentColor, 0))
    }

    /** Raw protocol frame（如 RRNN OFF：5A A5 02 00 00 00 01）。 */
    fun sendRawFrame(frame: ByteArray) {
        enqueueFrame(frame)
    }

    /** jiaoben.js transport.shutdown：OFF → black → OFF（设备安全间隔）。 */
    fun shutdownOff() {
        setKeepAlive(false)
        enqueueFrame(StageCompile.OFF)
        handler.postDelayed({ enqueueFrame(StageCompile.BLACK) }, 200)
        handler.postDelayed({ enqueueFrame(StageCompile.OFF) }, 400)
    }

    /** Uses the IAG controller's native flash (0x03) or breathe (0x04) effect command. */
    fun setHardwareEffect(command: Int, hex: String, frequency: Int) {
        if (command != 0x03 && command != 0x04) return
        val clean = hex.removePrefix("#")
        if (clean.length != 6) return
        val color = clean.chunked(2).map { it.toInt(16) }
        val prefix = byteArrayOf(
            0x5A.toByte(), 0xA5.toByte(), 0x05, 0x00, command.toByte(),
            color[0].toByte(), color[1].toByte(), color[2].toByte(),
            frequency.coerceIn(1, 255).toByte(),
        )
        val checksum = prefix.fold(0) { sum, value -> (sum + (value.toInt() and 0xFF)) and 0xFF }
        currentColor = "#${clean.uppercase()}"
        currentOffByte = 0
        setKeepAlive(false)
        enqueueFrame(prefix + checksum.toByte())
        log("设备硬件灯效已设置：${if (command == 0x03) "快闪" else "呼吸"} · $currentColor · $frequency")
    }

    /** Color updates during effects/music are folded into the next keep-alive frame. */
    fun updateKeepAliveColor(red: Int, green: Int, blue: Int) {
        currentColor = "#%02X%02X%02X".format(
            red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255)
        )
        currentOffByte = 0
    }

    fun setKeepAlive(enabled: Boolean) {
        keepAliveEnabled = enabled
        handler.removeCallbacks(keepAlive)
        if (enabled) handler.post(keepAlive)
    }

    /** Official frame (blue.js setColor): 5A A5 04 00 01 R G B + additive checksum of the prefix (black -> 0x04). */
    private fun colorFrame(hex: String, offByte: Int = 0): ByteArray {
        val clean = hex.removePrefix("#")
        val prefix = byteArrayOf(
            0x5A.toByte(), 0xA5.toByte(), 0x04, 0x00, 0x01,
            clean.substring(0, 2).toInt(16).toByte(),
            clean.substring(2, 4).toInt(16).toByte(),
            clean.substring(4, 6).toInt(16).toByte(),
        )
        val checksum = prefix.fold(0) { sum, value -> (sum + (value.toInt() and 0xFF)) and 0xFF }
        return prefix + checksum.toByte()
    }

    private fun enqueueFrame(frame: ByteArray) {
        synchronized(this) { pendingFrame = frame.copyOf() }
        scheduleWritePump()
    }

    private fun scheduleWritePump() {
        handler.removeCallbacks(writePump)
        handler.post(writePump)
    }

    @SuppressLint("MissingPermission")
    private fun pumpWrite() {
        if (state != State.CONNECTED) return
        val g = gatt ?: return
        val ch = controlChar ?: return
        synchronized(this) { if (writePending) return }
        val elapsed = android.os.SystemClock.elapsedRealtime() - lastWriteAt
        if (elapsed < WRITE_INTERVAL_MS) {
            handler.removeCallbacks(writePump)
            handler.postDelayed(writePump, WRITE_INTERVAL_MS - elapsed)
            return
        }
        val frame = synchronized(this) {
            val queued = pendingFrame
            pendingFrame = null
            queued ?: if (keepAliveEnabled) colorFrame(currentColor, currentOffByte) else null
        } ?: return

        val useNoResponse = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0 &&
            ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        val writeType = if (useNoResponse) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val accepted = try {
            synchronized(this) { writePending = true }
            val success = if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(ch, frame, writeType) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                ch.value = frame
                @Suppress("DEPRECATION")
                ch.writeType = writeType
                @Suppress("DEPRECATION")
                g.writeCharacteristic(ch)
            }
            success
        } catch (e: Exception) {
            log("GATT 写入异常：${e.message}")
            false
        }
        if (!accepted) {
            synchronized(this) { writePending = false }
            handler.postDelayed(writePump, 180L)
            return
        }
        lastWriteAt = android.os.SystemClock.elapsedRealtime()
        val timeout = Runnable {
            synchronized(this) { writePending = false }
            log("GATT 写入回调超时，正在恢复发送")
            scheduleWritePump()
        }
        writeTimeout = timeout
        handler.postDelayed(timeout, 900L)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        reconnectTask?.let(handler::removeCallbacks)
        reconnectTask = null
        manuallyDisconnected = true
        stopScan()
        setKeepAlive(false)
        connectedAddress = ""
        connectedName = ""
        reconnectAttempts = 0
        controlChar = null
        closeGatt()
        publishState(State.IDLE)
        log("已手动断开荧光棒")
    }
    /** Lifecycle disposal: release every handler task without posting back to a dead Activity. */
    fun close() {
        closed = true
        manuallyDisconnected = true
        stopScan()
        setKeepAlive(false)
        closeGatt()
        handler.removeCallbacksAndMessages(null)
        reconnectTask = null
    }

}
