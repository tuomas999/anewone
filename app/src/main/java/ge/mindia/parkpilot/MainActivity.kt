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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
        setContent { ParkPilotScreen() }
    }

    @Composable
    private fun ParkPilotScreen() {
        val store = remember { ParkingStore(this@MainActivity) }
        var mode by remember { mutableStateOf(store.detectionMode()) }
        var carName by remember { mutableStateOf(store.selectedCarName()) }
        var session by remember { mutableStateOf(store.session()) }
        var monitoring by remember { mutableStateOf(store.monitoringEnabled()) }
        var candidates by remember { mutableStateOf(store.candidates()) }
        var pendingLot by remember { mutableStateOf(store.pendingLot()) }
        var diagnostics by remember { mutableStateOf(store.diagnostics()) }
        var pairedCars by remember { mutableStateOf<List<PairedCar>>(emptyList()) }
        var showCarPicker by remember { mutableStateOf(false) }
        var manualQuery by remember { mutableStateOf("") }
        var manualResults by remember { mutableStateOf<List<ParkingCandidate>>(emptyList()) }
        var statusMessage by remember { mutableStateOf<String?>(null) }

        fun reload() {
            mode = store.detectionMode()
            carName = store.selectedCarName()
            session = store.session()
            monitoring = store.monitoringEnabled()
            candidates = store.candidates()
            pendingLot = store.pendingLot()
            diagnostics = store.diagnostics()
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) reload() }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pairedCars = bondedCars()
                showCarPicker = true
            } else statusMessage = "Bluetooth მოწყობილობების სანახავად ნებართვა საჭიროა."
        }

        val monitorLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasCoreMonitorPermissions(mode)) {
                startMonitoring()
                monitoring = true
                statusMessage = if (mode == DetectionMode.BLUETOOTH && !hasActivityRecognition())
                    "მონიტორინგი ჩაირთო Bluetooth + GPS fallback რეჟიმში; Activity Recognition არ არის დაშვებული."
                else "მონიტორინგი ჩაირთო."
            } else statusMessage = "მონიტორინგისთვის საჭირო ძირითადი ნებართვები არ არის გაცემული."
        }

        fun chooseCandidate(candidate: ParkingCandidate) {
            store.selectPendingLot(candidate.code)
            pendingLot = candidate.code
            startActivity(
                ParkingHandoffActivity.intent(
                    this@MainActivity,
                    ParkingHandoffActivity.Mode.START,
                    candidate.code
                )
            )
        }

        MaterialTheme {
            Surface(Modifier.fillMaxSize()) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("ParkPilot", style = MaterialTheme.typography.headlineLarge)
                    Text("თბილისის ზონალური პარკირების ჭკვიანი დამხმარე")
                    Text(ParkingLots.datasetInfo(this@MainActivity), style = MaterialTheme.typography.bodySmall)
                    statusMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("მანქანის ამოცნობის რეჟიმი", style = MaterialTheme.typography.titleMedium)
                            Text("ParkPilot ერთ სიგნალს არ ენდობა: მანქანის კავშირი, მოძრაობის ტიპი, GPS და ლოტის მისამართი ერთად ფასდება.", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    if (monitoring) {
                                        statusMessage = "რეჟიმის შესაცვლელად ჯერ გამორთე მონიტორინგი."
                                    } else {
                          ,�H�@L��                              onValueChange = {
                                    manualQuery = it
                                    manualResults = if (it.length >= 2) ParkingLots.search(this@MainActivity, it) else emptyList()
                                },
                                label = { Text("A091 ან ყაზბეგის 5") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            manualResults.take(8).forEach { result ->
                                TextButton(onClick = { chooseCandidate(result) }, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.fillMaxWidth()) { Text(result.code); Text(result.address, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("დიაგნოსტიკა / ტესტი", style = MaterialTheme.typography.titleMedium)
                            Text("Activity: ${diagnostics.motionActivity ?: "—"}")
                            Text("Confidence: ${diagnostics.confidenceSummary ?: "—"}")
                            Text("ბოლო მოვლენა: ${diagnostics.lastEvent ?: "—"}")
                            if (diagnostics.lastEventAt > 0) Text(formatTime(diagnostics.lastEventAt), style = MaterialTheme.typography.bodySmall)
                            Text("ბოლო მისამართი: ${diagnostics.lastAddress ?: "—"}")
                            if (diagnostics.lastAccuracyMeters >= 0) Text("GPS სიზუსტე: ${diagnostics.lastAccuracyMeters.toInt()} მ")
                            Text("კანდიდატები: ${diagnostics.candidateSummary ?: "—"}", style = MaterialTheme.typography.bodySmall)
                            HorizontalDivider()
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(enabled = monitoring && mode == DetectionMode.BLUETOOTH, onClick = {
                                    startService(Intent(this@MainActivity, CarMonitorService::class.java).setAction(CarMonitorService.ACTION_TEST_DISCONNECT))
                                }) { Text("TEST BT stop") }
                                OutlinedButton(enabled = monitoring, onClick = {
                                    startService(Intent(this@MainActivity, CarMonitorService::class.java).setAction(CarMonitorService.ACTION_TEST_MOTION_PARK))
                                }) { Text("TEST motion") }
                            }
                            OutlinedButton(enabled = monitoring, onClick = {
                                startService(Intent(this@MainActivity, CarMonitorService::class.java).setAction(CarMonitorService.ACTION_TEST_RECONNECT))
                            }) { Text("TEST reconnect") }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { diagnostics = store.diagnostics() }) { Text("განახლება") }
                                OutlinedButton(onClick = { shareDiagnostics(store) }) { Text("ლოგის გაზიარება") }
                            }
                        }
                    }
                }
            }
        }

        if (showCarPicker) {
            AlertDialog(
                onDismissRequest = { showCarPicker = false },
                title = { Text("აირჩიე მანქანის მოწყობილობა") },
                text = {
                    if (pairedCars.isEmpty()) Text("დაწყვილებული Bluetooth მოწყობილობები ვერ მოიძებნა.")
                    else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(pairedCars) { car ->
                            TextButton(onClick = {
                                store.selectCar(car.address, car.name); carName = car.name; mode = DetectionMode.BLUETOOTH; showCarPicker = false
                            }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth()) { Text(car.name); Text(car.address, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showCarPicker = false }) { Text("დახურვა") } }
            )
        }
    }

    private fun bondedCars(): List<PairedCar> {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val adapter = getSystemService(BluetoothManager::class.java).adapter ?: return emptyList()
        return adapter.bondedDevices.map { device -> PairedCar(device.address, device.name?.takeIf { it.isNotBlank() } ?: device.address) }
            .sortedBy { it.name.lowercase() }
    }

    private fun requestedMonitorPermissions(mode: DetectionMode): Array<String> = buildList {
        if (mode == DetectionMode.BLUETOOTH) add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private fun coreMonitorPermissions(mode: DetectionMode): Array<String> = buildList {
        if (mode == DetectionMode.BLUETOOTH) add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (mode == DetectionMode.MOTION_ONLY) add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private fun hasCoreMonitorPermissions(mode: DetectionMode): Boolean = coreMonitorPermissions(mode).all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAllRequestedPermissions(mode: DetectionMode): Boolean = requestedMonitorPermissions(mode).all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasActivityRecognition(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    private fun startMonitoring() {
        ContextCompat.startForegroundService(this, Intent(this, CarMonitorService::class.java))
    }

    private fun shareDiagnostics(store: ParkingStore) {
        val report = store.diagnosticReport()
        val share = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "ParkPilot ${BuildConfig.VERSION_NAME} diagnostics")
            .putExtra(Intent.EXTRA_TEXT, report)
        startActivity(Intent.createChooser(share, "ParkPilot ლოგის გაზიარება"))
    }

    private fun formatTime(value: Long): String = DateFormat.getMediumDateFormat(this).format(Date(value)) + " " + DateFormat.getTimeFormat(this).format(Date(value))
}
