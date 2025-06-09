package com.escom.temperaturabluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.escom.temperaturabluetooth.ui.theme.TemperaturaBluetoothTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemperaturaBluetoothTheme {
                BluetoothApp()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BluetoothApp() {
    val viewModel: BluetoothViewModel = viewModel()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Definir los permisos necesarios según la versión de Android
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
    
    val permissionsState = rememberMultiplePermissionsState(permissions = permissions)
    
    LaunchedEffect(key1 = Unit) {
        permissionsState.launchMultiplePermissionRequest()
    }
    
    LaunchedEffect(key1 = permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            viewModel.initializeBluetooth(context)
        }
    }
    
    Scaffold { innerPadding ->
        if (permissionsState.allPermissionsGranted) {
            BluetoothContent(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            PermissionDeniedContent(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            )
        }
    }
}

@Composable
fun PermissionDeniedContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Se requieren permisos de Bluetooth para usar esta aplicación",
            fontSize = 18.sp
        )
    }
}

@Composable
fun BluetoothContent(
    viewModel: BluetoothViewModel,
    modifier: Modifier = Modifier
) {
    val deviceList by viewModel.deviceList.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val receivedData by viewModel.receivedData.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val temperatureHistory by viewModel.temperatureHistory.collectAsState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Lector de Datos Bluetooth",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        if (isConnected) {
            ConnectedDeviceContent(
                receivedData = receivedData,
                temperatureHistory = temperatureHistory,
                onDisconnect = { viewModel.closeConnection() }
            )
        } else {
            DeviceListContent(
                deviceList = deviceList,
                isScanning = isScanning,
                onScanClick = { viewModel.scanForDevices() },
                onDeviceClick = { viewModel.connectToDevice(it) }
            )
        }
    }
}

@Composable
fun ConnectedDeviceContent(
    receivedData: String,
    temperatureHistory: List<TemperaturePoint>,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Datos recibidos:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = receivedData.ifEmpty { "Esperando datos..." },
                    fontSize = 16.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Añadir la gráfica de temperatura
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            TemperatureChart(
                temperaturePoints = temperatureHistory,
                modifier = Modifier.padding(8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Desconectar")
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceListContent(
    deviceList: List<BluetoothDevice>,
    isScanning: Boolean,
    onScanClick: () -> Unit,
    onDeviceClick: (BluetoothDevice) -> Unit
) {
    Column {
        Button(
            onClick = onScanClick,
            enabled = !isScanning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isScanning) "Escaneando..." else "Buscar dispositivos")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Dispositivos disponibles:",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (deviceList.isEmpty()) {
            Text(
                text = "No se encontraron dispositivos emparejados",
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            LazyColumn {
                items(deviceList) { device ->
                    DeviceItem(
                        device = device,
                        onClick = { onDeviceClick(device) }
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceItem(
    device: BluetoothDevice,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .   clickable { onClick() }
                .padding(16.dp)
        ) {
            Text(
                text = device.name ?: "Dispositivo desconocido",
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = device.address)
        }
    }
}