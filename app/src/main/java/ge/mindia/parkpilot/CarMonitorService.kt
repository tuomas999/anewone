package ge.mindia.parkpilot

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class CarMonitorService : Service() {
    private val store by lazy { ParkingStore(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var disconnectAt = 0L
    private var observedConnected: Boolean? = null
    private var bluetoothReceiverRegistered = false
    private var internalReceiverRegistered = false

    private val departureMonitor by lazy {
        DepartureMonitor(this, store) { session, speed, distance ->
            val detail = buildString {
                append("დაძვრა დადასტურდა")
                speed?.let { append(" • ${it.toInt()} km/h") }
                distance?.let { append(" • ${it.toInt()} მ პარკირების წერტილიდან") }
            }
            if (store.stopReminderSentFor(session)) {
                store.recordEvent("$detail • დასრულების reminder უკვე გაგზავნილი იყო")
            } else {
                store.markStopReminderSentFor(session)
                store.recordEvent(detail)
                Notifications.offerStop(this, session, detail)
            }
        }
    }

    private val disconnectRunnable = Runnable {
        val elapsed = System.currentTimeMillis() - disconnectAt
        if (observedConnected == true || elapsed < REAL_DISCONNECT_DELAY_MS || store.session().active) return@Runnable
        evaluateParkingCandidate("Bluetooth 45s stable disconnect")
    }

    private val motionCandidateRunnable = Runnable {
        if (store.session().active) return@Runnable
        evaluateParkingCandidate("Activity transition")
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = handleBluetoothIntent(context, intent)
    }

    private val internalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_ACTIVITY_SIGNAL -> handleActivitySignal(intent)
                ACTION_SESSION_CHANGED -> {
                    if (!store.session().active) {
                        departureMonitor.stop()
                        if (store.carConnected()) {
                            store.prepareNewTripLeg()
                            store.markDriving(source = "parking-ended-after-departure")
                            captureTripAnchor("parking ended / new leg")
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        val mode = store.detectionMode()
        val fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
            if (mode == DetectionMode.BLUETOOTH) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(
            this,
            Notifications.MONITOR_ID,
            Notifications.monitor(this, store.selectedCarName(), mode),
            fgsType
        )
        registerReceivers()
        initializeBluetoothConnectionState()
        ActivityRecognitionManager.register(this) { ok, error ->
            store.recordEvent(if (ok) "Activity Recognition ჩაირთო" else "Activity Recognition ვერ ჩაირთო: ${error ?: "unknown"}")
        }
        store.setMonitoringEnabled(true)
        store.recordEvent("მონიტორინგის სერვისი ჩაირთო: ${mode.name}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_MONITORING -> {
                store.setMonitoringEnabled(false)
                store.recordEvent("მონიტორინგი ხელით გაითიშა")
                ActivityRecognitionManager.unregister(this)
                departureMonitor.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TEST_DISCONNECT -> {
                store.markDriving(source = "TEST")
                store.markCarConnected(System.currentTimeMillis() - 4 * 60_000L)
                handleDisconnected("TEST: disconnect", TEST_DISCONNECT_DELAY_MS)
            }
            ACTION_TEST_RECONNECT -> handleConnected("TEST: reconnect")
            ACTION_TEST_MOTION_PARK -> {
                store.markDriving(source = "TEST:IN_VEHICLE")
                store.markVehicleExited()
                store.recordMotion(MotionActivity.WALKING)
                handler.removeCallbacks(motionCandidateRunnable)
                handler.postDelayed(motionCandidateRunnable, TEST_DISCONNECT_DELAY_MS)
            }
        }
        return START_STICKY
    }

    private fun registerReceivers() {
        if (store.detectionMode() == DetectionMode.BLUETOOTH) {
            val bluetoothFilter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(this, bluetoothReceiver, bluetoothFilter, ContextCompat.RECEIVER_EXPORTED)
            bluetoothReceiverRegistered = true
        }
        val internalFilter = IntentFilter().apply {
            addAction(ACTION_ACTIVITY_SIGNAL)
            addAction(ACTION_SESSION_CHANGED)
        }
        ContextCompat.registerReceiver(this, internalReceiver, internalFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        internalReceiverRegistered = true
    }

    private fun initializeBluetoothConnectionState() {
        if (store.detectionMode() != DetectionMode.BLUETOOTH) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        val adapter = getSystemService(android.bluetooth.BluetoothManager::class.java).adapter ?: return
        val selected = store.selectedCar() ?: return
        listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET).forEach { profileType ->
            adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    val connected = runCatching { proxy.connectedDevices.any { it.address == selected } }.getOrDefault(false)
                    if (connected) handleConnected("Initial Bluetooth profile connected")
                    runCatching { adapter.closeProfileProxy(profile, proxy) }
                }
                override fun onServiceDisconnected(profile: Int) = Unit
            }, profileType)
        }
    }

    private fun handleBluetoothIntent(context: Context, intent: Intent) {
        if (store.detectionMode() != DetectionMode.BLUETOOTH) return
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        val device = intent.bluetoothDevice() ?: return
        if (device.address != store.selectedCar()) return

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> handleConnected("Bluetooth ACL connected")
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> handleDisconnected("Bluetooth ACL disconnected", REAL_DISCONNECT_DELAY_MS)
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
                BluetoothProfile.STATE_CONNECTED -> handleConnected("Bluetooth A2DP connected")
                BluetoothProfile.STATE_DISCONNECTED -> store.recordEvent("Bluetooth A2DP disconnected — ველოდები ACL გათიშვას")
            }
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
                BluetoothProfile.STATE_CONNECTED -> handleConnected("Bluetooth HFP connected")
                BluetoothProfile.STATE_DISCONNECTED -> store.recordEvent("Bluetooth HFP disconnected — ველოდები ACL გათიშვას")
            }
        }
    }

    private fun handleActivitySignal(intent: Intent) {
        val activity = runCatching { MotionActivity.valueOf(intent.getStringExtra(EXTRA_ACTIVITY) ?: MotionActivity.UNKNOWN.name) }
            .getOrDefault(MotionActivity.UNKNOWN)
        val entering = intent.getBooleanExtra(EXTRA_ENTERING, false)

        if (activity == MotionActivity.IN_VEHICLE && entering) {
            if (!store.session().active && !store.hasTripAnchor()) captureTripAnchor("activity IN_VEHICLE")
            if (store.session().active) departureMonitor.start()
            handler.removeCallbacks(motionCandidateRunnable)
            return
        }

        val parkingTransition = (activity == MotionActivity.IN_VEHICLE && !entering) ||
            (entering && activity in setOf(MotionActivity.WALKING, MotionActivity.ON_FOOT, MotionActivity.STILL))
        if (parkingTransition && store.tripActive() && !store.session().active) {
            handler.removeCallbacks(motionCandidateRunnable)
            handler.postDelayed(motionCandidateRunnable, MOTION_SETTLE_DELAY_MS)
        }
    }

    private fun handleDisconnected(source: String, delayMs: Long) {
        observedConnected = false
        disconnectAt = System.currentTimeMillis()
        store.markCarDisconnected(disconnectAt)
        handler.removeCallbacks(disconnectRunnable)
        store.recordEvent("$source — ${delayMs / 1000} წმ დაცდა")
        if (store.detectionMode() == DetectionMode.BLUETOOTH) handler.postDelayed(disconnectRunnable, delayMs)
    }

    private fun handleConnected(source: String) {
        val now = System.currentTimeMillis()
        val wasConnected = store.carConnected()
        val previousDisconnectAt = store.lastCarDisconnectedAt()
        val activeSession = store.session().active
        observedConnected = true

        val newLeg = TripLegPolicy.isNewLeg(
            wasConnected = wasConnected,
            activeParkingSession = activeSession,
            previousDisconnectAt = previousDisconnectAt,
            now = now,
            promptedAt = store.promptedAt()
        )
        if (newLeg) store.prepareNewTripLeg()

        store.markCarConnected(now)
        if (!wasConnected && !activeSession && (newLeg || !store.hasTripAnchor())) captureTripAnchor("car connected")

        handler.removeCallbacks(disconnectRunnable)
        handler.removeCallbacks(motionCandidateRunnable)
        Notifications.cancel(this, Notifications.CANDIDATE_ID)
        store.recordEvent("$source${if (newLeg) " — new trip leg" else if (!wasConnected) " — same trip reconnect" else ""}")
        if (activeSession) departureMonitor.start()
    }

    private fun captureTripAnchor(source: String) {
        LocationResolver.currentRaw(this) { location, error ->
            if (location != null) {
                store.setTripAnchor(location.latitude, location.longitude, location.accuracy)
                store.recordEvent("Trip anchor: $source • accuracy=${location.accuracy.toInt()}m")
            } else {
                store.recordEvent("Trip anchor ვერ მივიღე: ${error ?: "unknown"}")
            }
        }
    }

    private fun evaluateParkingCandidate(source: String) {
        if (store.session().active) return
        LocationResolver.current(this) { resolved, error ->
            if (resolved == null) {
                store.recordEvent("$source — ${error ?: "GPS ვერ განისაზღვრა"}")
                return@current
            }
            val candidates = ParkingLots.candidates(this, resolved)
            val evidence = ParkingDecisionAdapter.evaluate(store, resolved, candidates)
            store.recordLocation(resolved, candidates)
            store.recordEvidence(evidence)
            store.recordEvent("$source — ${evidence.summary()}")

            if (!evidence.shouldPrompt) return@current

            store.setPendingLocation(resolved)
            store.setCandidates(candidates)
            store.markPrompted()
            if (candidates.isEmpty()) {
                Notifications.offerManualLotSelection(
                    this,
                    error ?: resolved.addressLine ?: "ამ მდებარეობასთან ლოტი ავტომატურად ვერ მოიძებნა",
                    evidence
                )
            } else {
                Notifications.offerCandidates(this, candidates, resolved.addressLine, evidence)
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(disconnectRunnable)
        handler.removeCallbacks(motionCandidateRunnable)
        departureMonitor.stop()
        if (bluetoothReceiverRegistered) runCatching { unregisterReceiver(bluetoothReceiver) }
        if (internalReceiverRegistered) runCatching { unregisterReceiver(internalReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun Intent.bluetoothDevice(): BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }

    companion object {
        const val ACTION_STOP_MONITORING = "ge.mindia.parkpilot.STOP_MONITORING"
        const val ACTION_TEST_DISCONNECT = "ge.mindia.parkpilot.TEST_DISCONNECT"
        const val ACTION_TEST_RECONNECT = "ge.mindia.parkpilot.TEST_RECONNECT"
        const val ACTION_TEST_MOTION_PARK = "ge.mindia.parkpilot.TEST_MOTION_PARK"
        const val ACTION_ACTIVITY_SIGNAL = "ge.mindia.parkpilot.ACTIVITY_SIGNAL"
        const val ACTION_SESSION_CHANGED = "ge.mindia.parkpilot.SESSION_CHANGED"
        const val EXTRA_ACTIVITY = "activity"
        const val EXTRA_ENTERING = "entering"
        private const val REAL_DISCONNECT_DELAY_MS = 45_000L
        private const val TEST_DISCONNECT_DELAY_MS = 1_500L
        private const val MOTION_SETTLE_DELAY_MS = 25_000L
    }
}
