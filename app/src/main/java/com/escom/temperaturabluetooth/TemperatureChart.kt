package com.escom.temperaturabluetooth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TemperatureChart(
    temperaturePoints: List<TemperaturePoint>,
    modifier: Modifier = Modifier
) {
    if (temperaturePoints.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No hay datos de temperatura disponibles",
                color = colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        return
    }
    
    // Encontrar los valores mínimos y máximos para escalar la gráfica
    val minTemp = temperaturePoints.minByOrNull { it.value }?.value ?: 0f
    val maxTemp = temperaturePoints.maxByOrNull { it.value }?.value ?: 100f
    
    // Asegurar que haya un rango mínimo para evitar divisiones por cero
    // Usar el rango de calibración del ESP32 (25°C a 100°C) como referencia
    val tempRange = maxOf(maxTemp - minTemp, 1f)
    val minRangeTemp = 25f  // T_LOW del ESP32
    val maxRangeTemp = 100f // T_HIGH del ESP32
    
    // Ajustar el rango para que incluya los valores de calibración del ESP32
    val adjustedMinTemp = minOf(minRangeTemp, minTemp - (tempRange * 0.1f))
    val adjustedMaxTemp = maxOf(maxRangeTemp, maxTemp + (tempRange * 0.1f))
    val adjustedRange = adjustedMaxTemp - adjustedMinTemp
    
    // Obtener el color y el tamaño de texto fuera del Canvas
    val textColor = android.graphics.Color.GRAY
    val textSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { 10.sp.toPx() }
    val primaryColor = colorScheme.primary
    val surfaceColor = colorScheme.surface
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(
            text = "Gráfica de Temperatura",
            fontSize = 16.sp,
            color = colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(surfaceColor)
                .padding(8.dp)
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val width = size.width
                val height = size.height
                val pointWidth = width / (temperaturePoints.size - 1).coerceAtLeast(1)
                
                // Dibujar líneas de referencia horizontales
                val numLines = 5
                val lineColor = Color.Gray.copy(alpha = 0.3f)
                
                for (i in 0..numLines) {
                    val y = height - (height * i / numLines)
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                    
                    // Dibujar etiquetas de temperatura
                    val tempValue = adjustedMinTemp + (adjustedRange * i / numLines)
                    drawContext.canvas.nativeCanvas.drawText(
                        String.format("%.1f°C", tempValue),
                        8f,
                        y - 4f,
                        android.graphics.Paint().apply {
                            color = textColor
                            textSize = textSizePx
                        }
                    )
                }
                
                // Dibujar la línea de la gráfica
                if (temperaturePoints.size > 1) {
                    val path = Path()
                    
                    temperaturePoints.forEachIndexed { index, point ->
                        val x = index * pointWidth
                        val normalizedY = (point.value - adjustedMinTemp) / adjustedRange
                        val y = height - (normalizedY * height)
                        
                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                        
                        // Dibujar puntos en cada medición
                        drawCircle(
                            color = primaryColor,
                            radius = 4f,
                            center = Offset(x, y)
                        )
                    }
                    
                    // Dibujar la línea que conecta los puntos
                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(width = 2f)
                    )
                }
            }
        }
    }
}