package com.escom.temperaturabluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.util.UUID

// Clase para representar un punto de datos de temperatura
data class TemperaturePoint(
    val value: Float,
    val timestamp: Long = System.currentTimeMillis()
)

class BluetoothViewModel : ViewModel() {
    
    private val _deviceList = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val deviceList: StateFlow<List<BluetoothDevice>> = _deviceList.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _receivedData = MutableStateFlow("")
    val receivedData: StateFlow<String> = _receivedData.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    // Historial de temperaturas para la gráfica
    private val _temperatureHistory = MutableStateFlow<List<TemperaturePoint>>(emptyList())
    val temperatureHistory: StateFlow<List<TemperaturePoint>> = _temperatureHistory.asStateFlow()
    
    // Número máximo de puntos a mostrar en la gráfica
    private val maxDataPoints = 20
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    
    // UUID estándar para dispositivos SPP (Serial Port Profile)
    private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    
    @SuppressLint("MissingPermission")
    fun initializeBluetooth(context: Context) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        // Verificar si el Bluetooth está disponible
        if (bluetoothAdapter == null) {
            // El dispositivo no soporta Bluetooth
            return
        }
    }
    
    @SuppressLint("MissingPermission")
    fun scanForDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            
            // Obtener dispositivos emparejados
            val pairedDevices = bluetoothAdapter?.bondedDevices ?: setOf()
            _deviceList.value = pairedDevices.toList()
            
            _isScanning.value = false
        }
    }
    
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Crear un socket Bluetooth
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                
                // Cancelar descubrimiento porque ralentiza la conexión
                bluetoothAdapter?.cancelDiscovery()
                
                // Conectar al dispositivo
                bluetoothSocket?.connect()
                
                // Obtener el stream de entrada
                inputStream = bluetoothSocket?.inputStream
                
                _isConnected.value = true
                
                // Iniciar la lectura de datos
                readData()
                
            } catch (e: IOException) {
                _isConnected.value = false
                closeConnection()
            }
        }
    }
    
    private fun readData() {
        viewModelScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(1024)
            var bytes: Int
            
            // Mantener la escucha mientras esté conectado
            while (_isConnected.value) {
                try {
                    // Leer datos del inputStream
                    bytes = inputStream?.read(buffer) ?: -1
                    
                    if (bytes > 0) {
                        val data = String(buffer, 0, bytes)
                        
                        try {
                            // Procesar el formato "temperatura,voltaje" que envía el ESP32
                            val parts = data.trim().split(",")
                            if (parts.size == 2) {
                                val temperatura = parts[0].toFloat()
                                val voltaje = parts[1].toFloat()
                                
                                _receivedData.value = "Temperatura: $temperatura°C | Voltaje: $voltaje V"
                                
                                // Agregar el nuevo punto de temperatura al historial
                                addTemperaturePoint(temperatura)
                            } else {
                                // Si no tiene el formato esperado, mostrar los datos crudos
                                _receivedData.value = data
                            }
                        } catch (e: Exception) {
                            // Si hay error en el procesamiento, mostrar los datos crudos
                            _receivedData.value = data
                        }
                    }
                } catch (e: IOException) {
                    _isConnected.value = false
                    break
                }
            }
        }
    }
    
    // Función para agregar un nuevo punto de temperatura al historial
    private fun addTemperaturePoint(temperature: Float) {
        val newPoint = TemperaturePoint(temperature)
        val currentList = _temperatureHistory.value.toMutableList()
        
        // Agregar el nuevo punto
        currentList.add(newPoint)
        
        // Limitar el número de puntos para no sobrecargar la gráfica
        if (currentList.size > maxDataPoints) {
            currentList.removeAt(0)
        }
        
        _temperatureHistory.value = currentList
    }
    
    fun closeConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isConnected.value = false
                inputStream?.close()
                bluetoothSocket?.close()
            } catch (e: IOException) {
                // Manejar error al cerrar la conexión
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        closeConnection()
    }
}