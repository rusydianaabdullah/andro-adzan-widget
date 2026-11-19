package com.rusydiana.adzantime.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rusydiana.adzantime.R
import com.rusydiana.adzantime.data.Settings
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The Kaaba, which every bearing on this screen points at. */
private const val KAABA_LAT = 21.4224779
private const val KAABA_LON = 39.7593740
private const val EARTH_RADIUS_KM = 6371.0

/**
 * The compass. The dial rotates with the phone so the needle keeps pointing at the Kaaba;
 * without a rotation-vector sensor the screen degrades to the plain bearing from north.
 */
@Composable
fun QiblaScreen(settings: Settings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bearing = remember { qiblaBearing(settings.latitude, settings.longitude) }
    val distance = remember { distanceToKaabaKm(settings.latitude, settings.longitude) }
    val azimuth = rememberAzimuth()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.qibla_heading),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(
                R.string.qibla_bearing_format,
                String.format(Locale.getDefault(), "%.1f", bearing),
            ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(
                R.string.qibla_distance_format,
                String.format(Locale.getDefault(), "%,.0f", distance),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!settings.hasLocation) {
            Text(
                stringResource(R.string.qibla_needs_location),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (azimuth == null) {
            Text(stringResource(R.string.qibla_no_sensor), style = MaterialTheme.typography.bodySmall)
            return@Column
        }

        // Positive means the Kaaba is clockwise from where the phone is pointing.
        val relative = normalizeSigned(bearing - azimuth)
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            CompassDial(
                dialRotation = -azimuth,
                needleRotation = relative.toFloat(),
                aligned = abs(relative) <= 5.0,
                accent = MaterialTheme.colorScheme.primary,
                onSurface = MaterialTheme.colorScheme.onSurface,
                textMeasurer = rememberTextMeasurer(),
                cardinals = listOf(
                    stringResource(R.string.compass_north),
                    stringResource(R.string.compass_east),
                    stringResource(R.string.compass_south),
                    stringResource(R.string.compass_west),
                ),
            )
        }
        Text(
            when {
                abs(relative) <= 5.0 -> stringResource(R.string.qibla_aligned)
                relative > 0 -> stringResource(R.string.qibla_turn_right)
                else -> stringResource(R.string.qibla_turn_left)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (abs(relative) <= 5.0) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
        Text(stringResource(R.string.qibla_calibrate), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CompassDial(
    dialRotation: Float,
    needleRotation: Float,
    aligned: Boolean,
    accent: Color,
    onSurface: Color,
    textMeasurer: TextMeasurer,
    /** N, E, S, W, in that order — the order the drawing walks the dial. */
    cardinals: List<String>,
) {
    Canvas(Modifier.fillMaxSize()) {
        val radius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(color = onSurface.copy(alpha = 0.15f), radius = radius, style = Stroke(3f))
        drawCircle(color = onSurface.copy(alpha = 0.08f), radius = radius * 0.66f, style = Stroke(2f))

        // Tick marks turn with the phone, so north stays put in the real world.
        rotate(degrees = dialRotation, pivot = center) {
            for (degree in 0 until 360 step 15) {
                val major = degree % 90 == 0
                rotate(degrees = degree.toFloat(), pivot = center) {
                    drawLine(
                        color = if (degree == 0) accent else onSurface.copy(alpha = if (major) 0.6f else 0.25f),
                        start = Offset(center.x, center.y - radius),
                        end = Offset(center.x, center.y - radius + if (major) radius * 0.14f else radius * 0.07f),
                        strokeWidth = if (major) 6f else 3f,
                    )
                }
            }
        }

        // The cardinal letters travel round the dial with it but stay upright, which reads
        // better on a screen than the rotating glyphs of a physical compass.
        cardinals.forEachIndexed { index, letter ->
            val isNorth = index == 0
            val radians = Math.toRadians((index * 90 + dialRotation).toDouble())
            val ring = radius * 0.82f
            val layout = textMeasurer.measure(
                letter,
                TextStyle(
                    color = if (isNorth) accent else onSurface.copy(alpha = 0.55f),
                    fontSize = if (isNorth) 17.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    center.x + (ring * sin(radians)).toFloat() - layout.size.width / 2f,
                    center.y - (ring * cos(radians)).toFloat() - layout.size.height / 2f,
                ),
            )
        }

        // The needle: a filled arrowhead pointing at the Kaaba.
        rotate(degrees = needleRotation, pivot = center) {
            drawQiblaNeedle(
                center = center,
                radius = radius,
                color = if (aligned) accent else onSurface.copy(alpha = 0.75f),
            )
        }
        drawCircle(color = accent, radius = radius * 0.045f, center = center)
    }
}

private fun DrawScope.drawQiblaNeedle(center: Offset, radius: Float, color: Color) {
    val tip = center.y - radius * 0.82f
    val head = radius * 0.22f
    drawLine(
        color = color,
        start = Offset(center.x, center.y + radius * 0.45f),
        end = Offset(center.x, tip + head),
        strokeWidth = 10f,
    )
    drawPath(
        path = Path().apply {
            moveTo(center.x, tip)
            lineTo(center.x - head * 0.55f, tip + head)
            lineTo(center.x + head * 0.55f, tip + head)
            close()
        },
        color = color,
    )
}

/**
 * The phone's heading in degrees clockwise from magnetic north, or null when the device has
 * no rotation-vector sensor. Smoothed on the unit circle so it does not jump across 360°.
 */
@Composable
private fun rememberAzimuth(): Float? {
    val context = LocalContext.current
    val manager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    val sensor = remember(manager) {
        manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: manager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    }
    var supported by remember { mutableStateOf(sensor != null) }
    var azimuth by remember { mutableFloatStateOf(0f) }

    DisposableEffect(manager, sensor) {
        if (manager == null || sensor == null) {
            supported = false
            return@DisposableEffect onDispose { }
        }
        val matrix = FloatArray(9)
        val orientation = FloatArray(3)
        var smoothedSin = 0.0
        var smoothedCos = 0.0
        var seeded = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, orientation)
                val radians = orientation[0].toDouble()
                if (!seeded) {
                    smoothedSin = sin(radians)
                    smoothedCos = cos(radians)
                    seeded = true
                } else {
                    smoothedSin += (sin(radians) - smoothedSin) * SMOOTHING
                    smoothedCos += (cos(radians) - smoothedCos) * SMOOTHING
                }
                azimuth = normalize(Math.toDegrees(atan2(smoothedSin, smoothedCos))).toFloat()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }

    return if (supported) azimuth else null
}

private const val SMOOTHING = 0.15

/** Great-circle initial bearing from the user to the Kaaba, in degrees clockwise from north. */
internal fun qiblaBearing(latitude: Double, longitude: Double): Double {
    val lat = Math.toRadians(latitude)
    val kaabaLat = Math.toRadians(KAABA_LAT)
    val deltaLon = Math.toRadians(KAABA_LON - longitude)
    val y = sin(deltaLon) * cos(kaabaLat)
    val x = cos(lat) * sin(kaabaLat) - sin(lat) * cos(kaabaLat) * cos(deltaLon)
    return normalize(Math.toDegrees(atan2(y, x)))
}

internal fun distanceToKaabaKm(latitude: Double, longitude: Double): Double {
    val lat = Math.toRadians(latitude)
    val kaabaLat = Math.toRadians(KAABA_LAT)
    val deltaLon = Math.toRadians(KAABA_LON - longitude)
    val cosCentral = sin(lat) * sin(kaabaLat) + cos(lat) * cos(kaabaLat) * cos(deltaLon)
    return EARTH_RADIUS_KM * kotlin.math.acos(cosCentral.coerceIn(-1.0, 1.0))
}

/** Into [0, 360). */
private fun normalize(degrees: Double): Double = (degrees % 360 + 360) % 360

/** Into (-180, 180], so the sign says which way to turn. */
private fun normalizeSigned(degrees: Double): Double {
    val wrapped = normalize(degrees)
    return if (wrapped > 180) wrapped - 360 else wrapped
}
