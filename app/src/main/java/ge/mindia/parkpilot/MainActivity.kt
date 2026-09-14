package ge.mindia.parkpilot

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Date

private data class PairedCar(val address: String, val name: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.createChannels(this)
        setContent {
            ParkPilotTheme {
                ParkPilotScreen()
            }
        }
    }

    @Composable
    private fun ParkPilotScreen() {
        val store = remember { ParkingStore(this@MainActivity) }
        var mode by remember { mutableStateOf(store.detectionMode()) }
        var carName by remember { mutableStateOf(store.selectedCarName()) }
        var monitoring by remember { mutableStateOf(store.monitoringEnabled()) }
        var session by remember { mutableStateOf(store.session()) }
        var candidates by remember { mutableStateOf(store.candidates()) }
        var pendingLot by remember { mutableStateOf(store.pendingLot()) }
        var diagnostics by remember { mutableStateOf(store.diagnostics()) }
        var pairedCars by remember { mutableStateOf<List<PairedCar>>(emptyList()) }
        var showCarPicker by remember { mutableStateOf(false) }
        var showLotSearch by remember { mutableStateOf(false) }
        var showDiagnostics by remember { mutableStateOf(false) }
        var manualQuery by remember { mutableStateOf("") }
        var manualResults by remember { mutableStateOf<List<ParkingCandidate>>(emptyList()) }
        var statusMessage by remember { mutableStateOf<String?>(null) }

        fun reload() {
            mode = store.detectionMode()
            carName = store.selectedCarName()
            monitoring = store.monitoringEnabled()
            session = store.session()
            candidates = store.candidates()
            pendingLot = store.pendingLot()
            diagnostics = store.diagnostics()
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) reload()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                pairedCars = bondedCars()
                showCarPicker = true
            } else {
                statusMessage = "მანქანის არჩევისთვის Bluetooth-ის ნებართვა საჭიროა."
            }
        }

        val monitorPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            if (hasCoreMonitorPermissions(mode)) {
                startMonitoring()
                monitoring = true
                statusMessage = "მონიტორინგი ჩაირთო."
            } else {
                statusMessage = "მონიტორინგისთვის მდებარეობის და საჭირო Bluetooth ნებართვები საჭიროა."
            }
        }

        fun requestCarPicker() {
            if (hasBluetoothPermission()) {
                pairedCars = bondedCars()
                showCarPicker = true
            } else if (Build.VERSION.SDK_INT >= 31) {
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        fun requestMonitoring() {
            val permissions = requestedMonitorPermissions(mode)
            if (permissions.isEmpty() || hasCoreMonitorPermissions(mode)) {
                startMonitoring()
                monitoring = true
                statusMessage = "მონიტორინგი ჩაირთო."
            } else {
                monitorPermissionLauncher.launch(permissions.toTypedArray())
            }
        }

        fun openStartFlow(code: String) {
            store.selectPendingLot(code)
            pendingLot = code
            startActivity(ParkingHandoffActivity.intent(this@MainActivity, ParkingHandoffActivity.Mode.START, code))
        }

        val suggestedLot = pendingLot ?: candidates.firstOrNull()?.code
        val title = when {
            session.active -> "პარკირება აქტიურია"
            suggestedLot != null -> "სავარაუდოდ დააპარკინგე"
            store.carConnected() -> "მანქანასთან დაკავშირებული ხარ"
            store.tripActive() -> "მოძრაობაში ხარ"
            monitoring -> "ParkPilot აკვირდება მოძრაობას"
            else -> "ParkPilot მზადაა"
        }

        val subtitle = when {
            session.active -> buildString {
                append(session.lotCode ?: "აქტიური ლოტი")
                if (session.startedAt > 0) append(" • ${formatTime(session.startedAt)}")
            }
            suggestedLot != null -> buildString {
                append(suggestedLot)
                diagnostics.lastAddress?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
            }
            diagnostics.lastAddress?.isNotBlank() == true -> diagnostics.lastAddress!!
            carName != null -> carName!!
            else -> "აირჩიე მანქანა და ჩართე მონიტორინგი"
        }

        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("ParkPilot", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "პარკირება ნაკლები ქმედებით",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(title, style = MaterialTheme.typography.headlineSmall)
                        Text(subtitle, style = MaterialTheme.typography.bodyLarge)

                        when {
                            session.active -> Button(
                                onClick = {
                                    startActivity(
                                        ParkingHandoffActivity.intent(
                                            this@MainActivity,
                                            ParkingHandoffActivity.Mode.STOP,
                                            session.lotCode
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("გახსენი Parking Tbilisi")
                            }

                            suggestedLot != null -> Button(
                                onClick = { openStartFlow(suggestedLot) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("გახსენი Parking Tbilisi • $suggestedLot")
                            }

                            else -> Button(
                                onClick = {
                                    val result = ParkingTbilisi.open(this@MainActivity)
                                    statusMessage = if (result.success) {
                                        "Parking Tbilisi გაიხსნა."
                                    } else {
                                        "Parking Tbilisi ვერ გაიხსნა: ${result.detail}"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("გახსენი Parking Tbilisi")
                            }
                        }

                        if (!session.active) {
                            OutlinedButton(
                                onClick = { showLotSearch = !showLotSearch },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (showLotSearch) "ლოტის ძებნის დახურვა" else "ლოტის ხელით არჩევა")
                            }
                        }
                    }
                }

                statusMessage?.let {
                    Card(Modifier.fillMaxWidth()) {
                        Text(it, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (showLotSearch && !session.active) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("ლოტის მოძებნა", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = manualQuery,
                                onValueChange = {
                                    manualQuery = it
                                    manualResults = if (it.trim().length >= 2) {
                                        ParkingLots.search(this@MainActivity, it.trim())
                                    } else emptyList()
                                },
                                label = { Text("მაგ. A091 ან ყაზბეგის 7") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            manualResults.take(6).forEach { result ->
                                TextButton(
                                    onClick = { openStartFlow(result.code) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(result.code, style = MaterialTheme.typography.titleSmall)
                                        Text(result.address, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("მანქანის მონიტორინგი", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                mode == DetectionMode.MOTION_ONLY -> "რეჟიმი: მოძრაობა + GPS"
                                carName != null -> "მანქანა: $carName"
                                else -> "მანქანა ჯერ არჩეული არ არის"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            if (monitoring) "ჩართულია" else "გამორთულია",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedButton(onClick = { requestCarPicker() }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (carName == null) "მანქანის არჩევა" else "მანქანის შეცვლა")
                        }

                        if (!monitoring) {
                            Button(onClick = { requestMonitoring() }, modifier = Modifier.fillMaxWidth()) {
                                Text("მონიტორინგის ჩართვა")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    startService(
                                        Intent(this@MainActivity, CarMonitorService::class.java)
                                            .setAction(CarMonitorService.ACTION_STOP_MONITORING)
                                    )
                                    monitoring = false
                                    statusMessage = "მონიტორინგი გამოირთო."
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("მონიტორინგის გამორთვა")
                            }
                        }

                        TextButton(
                            onClick = {
                                mode = if (mode == DetectionMode.BLUETOOTH) DetectionMode.MOTION_ONLY else DetectionMode.BLUETOOTH
                                store.setDetectionMode(mode)
                                statusMessage = if (mode == DetectionMode.MOTION_ONLY) {
                                    "არჩეულია მოძრაობა + GPS რეჟიმი."
                                } else {
                                    "არჩეულია Bluetooth რეჟიმი."
                                }
                            }
                        ) {
                            Text(if (mode == DetectionMode.BLUETOOTH) "Bluetooth-ის გარეშე გამოყენება" else "Bluetooth რეჟიმზე დაბრუნება")
                        }
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("მიმდინარე მდგომარეობა", style = MaterialTheme.typography.titleMedium)
                        Text("მოძრაობა: ${friendlyActivity(store.motionActivity())}")
                        diagnostics.lastAddress?.takeIf { it.isNotBlank() }?.let {
                            Text("ადგილი: $it")
                        }
                        if (diagnostics.lastAccuracyMeters >= 0) {
                            Text("GPS სიზუსტე: ${diagnostics.lastAccuracyMeters.toInt()} მ")
                        }
                    }
                }

                TextButton(onClick = { showDiagnostics = !showDiagnostics }) {
                    Text(if (showDiagnostics) "Diagnostics-ის დამალვა" else "Diagnostics")
                }

                if (showDiagnostics) {
                    DiagnosticsCard(store, monitoring)
                }

                Text(
                    ParkingLots.datasetInfo(this@MainActivity),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showCarPicker) {
            AlertDialog(
                onDismissRequest = { showCarPicker = false },
                title = { Text("აირჩიე მანქანის Bluetooth") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (pairedCars.isEmpty()) {
                            Text("დაპეარებული Bluetooth მოწყობილობები ვერ მოიძებნა.")
                        } else {
                            pairedCars.forEach { car ->
                                TextButton(
                                    onClick = {
                                        store.selectCar(car.address, car.name)
                                        carName = car.name
                                        mode = DetectionMode.BLUETOOTH
                                        showCarPicker = false
                                        statusMessage = "არჩეულია ${car.name}."
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(car.name)
                                        Text(car.address, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCarPicker = false }) { Text("დახურვა") }
                }
            )
        }
    }

    @Composable
    private fun DiagnosticsCard(store: ParkingStore, monitoring: Boolean) {
        val d = store.diagnostics()
        val parkingVersion = ParkingTbilisi.installedVersion(this@MainActivity)
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
                Text("Parking Tbilisi: ${parkingVersion ?: "ვერ განისაზღვრა"}")
                Text("Monitoring: $monitoring")
                Text("Car connected: ${store.carConnected()}")
                Text("Activity: ${d.motionActivity ?: "unknown"}")
                Text("Confidence: ${d.confidenceSummary ?: "—"}")
                Text("Last event: ${d.lastEvent ?: "—"}")
                if (d.lastEventAt > 0) Text(formatTime(d.lastEventAt), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()

                OutlinedButton(
                    onClick = {
                        ParkingTbilisi.open(this@MainActivity)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("TEST • გახსენი Parking Tbilisi")
                }

                if (monitoring) {
                    OutlinedButton(
                        onClick = {
                            startService(
                                Intent(this@MainActivity, CarMonitorService::class.java)
                                    .setAction(CarMonitorService.ACTION_TEST_DISCONNECT)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("TEST • parking detection") }

                    OutlinedButton(
                        onClick = {
                            startService(
                                Intent(this@MainActivity, CarMonitorService::class.java)
                                    .setAction(CarMonitorService.ACTION_TEST_RECONNECT)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("TEST • reconnect") }
                }

                OutlinedButton(
                    onClick = { shareDiagnostics(store) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Diagnostics-ის გაზიარება")
                }
            }
        }
    }

    private fun hasBluetoothPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun bondedCars(): List<PairedCar> {
        if (!hasBluetoothPermission()) return emptyList()
        val adapter = getSystemService(BluetoothManager::class.java).adapter ?: return emptyList()
        return runCatching {
            @Suppress("MissingPermission")
            adapter.bondedDevices.map { device ->
                PairedCar(
                    address = device.address,
                    name = device.name?.takeIf { it.isNotBlank() } ?: device.address
                )
            }.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
    }

    private fun requestedMonitorPermissions(mode: DetectionMode): List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 31 && mode == DetectionMode.BLUETOOTH) add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

    private fun hasCoreMonitorPermissions(mode: DetectionMode): Boolean {
        val location = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val bluetooth = mode != DetectionMode.BLUETOOTH || hasBluetoothPermission()
        return location && bluetooth
    }

    private fun startMonitoring() {
        val store = ParkingStore(this)
        ContextCompat.startForegroundService(this, Intent(this, CarMonitorService::class.java))
        store.setMonitoringEnabled(true)
    }

    private fun shareDiagnostics(store: ParkingStore) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, store.diagnosticReport())
        startActivity(Intent.createChooser(send, "ParkPilot diagnostics"))
    }

    private fun formatTime(time: Long): String = DateFormat.getMediumDateFormat(this).format(Date(time)) +
        " " + DateFormat.getTimeFormat(this).format(Date(time))

    private fun friendlyActivity(activity: MotionActivity): String = when (activity) {
        MotionActivity.IN_VEHICLE -> "მანქანაში"
        MotionActivity.WALKING -> "ფეხით მოძრაობ"
        MotionActivity.ON_FOOT -> "ფეხით"
        MotionActivity.STILL -> "გაჩერებული ხარ"
        MotionActivity.RUNNING -> "სირბილი"
        MotionActivity.ON_BICYCLE -> "ველოსიპედი"
        else -> "უცნობია"
    }
}

@Composable
private fun ParkPilotTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}
