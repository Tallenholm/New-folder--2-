package com.mapledger.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.mapledger.app.BuildConfig
import com.mapledger.app.domain.LatLon
import com.mapledger.app.ui.theme.Bad
import com.mapledger.app.ui.theme.Good
import com.mapledger.app.ui.theme.Honey
import com.mapledger.app.ui.theme.Info
import kotlin.math.cos

data class MapTarget(val pos: LatLon, val radiusM: Double, val color: Color)

/**
 * Shows GPS evidence. Each inner list is a contiguous verified segment; segments are never joined,
 * so pauses and GPS gaps appear as breaks. Planned routes draw dashed underneath.
 */
@Composable
fun TrackMap(
    segments: List<List<LatLon>>,
    modifier: Modifier = Modifier,
    planned: List<List<LatLon>> = emptyList(),
    targets: List<MapTarget> = emptyList(),
    follow: Boolean = false,
) {
    if (BuildConfig.MAPS_API_KEY.isNotBlank()) GoogleTrackMap(segments, planned, targets, follow, modifier)
    else CanvasTrackMap(segments, planned, targets, modifier)
}

private const val DARK_STYLE = """[{"elementType":"geometry","stylers":[{"color":"#1d2128"}]},{"elementType":"labels.text.fill","stylers":[{"color":"#8a93a3"}]},{"elementType":"labels.text.stroke","stylers":[{"color":"#1d2128"}]},{"featureType":"road","elementType":"geometry","stylers":[{"color":"#2c333e"}]},{"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#3d4553"}]},{"featureType":"water","elementType":"geometry","stylers":[{"color":"#0e1a26"}]},{"featureType":"poi","stylers":[{"visibility":"off"}]}]"""

@Composable
private fun GoogleTrackMap(segments: List<List<LatLon>>, planned: List<List<LatLon>>, targets: List<MapTarget>, follow: Boolean, modifier: Modifier) {
    val cam = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(41.2506, -87.8314), 11f) }
    var loaded by remember { mutableStateOf(false) }
    val all = segments.flatten() + planned.flatten() + targets.map { it.pos }
    val last = segments.lastOrNull { it.isNotEmpty() }?.lastOrNull()
    LaunchedEffect(loaded, if (follow) last else all.size) {
        if (!loaded || all.isEmpty()) return@LaunchedEffect
        runCatching {
            if (follow && last != null) cam.animate(CameraUpdateFactory.newLatLngZoom(LatLng(last.lat, last.lon), 15f))
            else if (all.size == 1) cam.move(CameraUpdateFactory.newLatLngZoom(LatLng(all[0].lat, all[0].lon), 15f))
            else {
                val b = LatLngBounds.builder(); all.forEach { b.include(LatLng(it.lat, it.lon)) }
                cam.move(CameraUpdateFactory.newLatLngBounds(b.build(), 80))
            }
        }
    }
    GoogleMap(
        modifier = modifier, cameraPositionState = cam, onMapLoaded = { loaded = true },
        properties = MapProperties(mapStyleOptions = MapStyleOptions(DARK_STYLE)),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
    ) {
        planned.forEach { p -> if (p.size > 1) Polyline(points = p.map { LatLng(it.lat, it.lon) }, color = Info.copy(alpha = 0.7f), width = 8f) }
        segments.forEach { s -> if (s.size > 1) Polyline(points = s.map { LatLng(it.lat, it.lon) }, color = Honey, width = 12f) }
        targets.forEach { t -> Circle(center = LatLng(t.pos.lat, t.pos.lon), radius = t.radiusM, strokeColor = t.color, fillColor = t.color.copy(alpha = 0.25f), strokeWidth = 3f) }
        segments.firstOrNull { it.isNotEmpty() }?.first()?.let { Circle(center = LatLng(it.lat, it.lon), radius = 12.0, fillColor = Good, strokeColor = Color.White, strokeWidth = 2f) }
        segments.lastOrNull { it.isNotEmpty() }?.last()?.let { Circle(center = LatLng(it.lat, it.lon), radius = 12.0, fillColor = Bad, strokeColor = Color.White, strokeWidth = 2f) }
    }
}

/** Offline renderer: equirectangular projection of the evidence. No tiles, works with no signal. */
@Composable
private fun CanvasTrackMap(segments: List<List<LatLon>>, planned: List<List<LatLon>>, targets: List<MapTarget>, modifier: Modifier) {
    val bg = MaterialTheme.colorScheme.surfaceContainerLowest
    val grid = MaterialTheme.colorScheme.outlineVariant
    Box(modifier.background(bg)) {
        val pts = segments.flatten() + planned.flatten() + targets.map { it.pos }
        if (pts.isEmpty()) {
            Text("Waiting for GPS…", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Canvas(Modifier.fillMaxSize().padding(16.dp)) {
            val minLat = pts.minOf { it.lat }; val maxLat = pts.maxOf { it.lat }
            val minLon = pts.minOf { it.lon }; val maxLon = pts.maxOf { it.lon }
            val k = cos(Math.toRadians((minLat + maxLat) / 2))
            val spanX = ((maxLon - minLon) * k).coerceAtLeast(1e-4); val spanY = (maxLat - minLat).coerceAtLeast(1e-4)
            val scale = minOf(size.width / spanX, size.height / spanY).toFloat()
            val ox = (size.width - spanX.toFloat() * scale) / 2; val oy = (size.height - spanY.toFloat() * scale) / 2
            fun p(l: LatLon) = Offset(ox + ((l.lon - minLon) * k).toFloat() * scale, oy + (maxLat - l.lat).toFloat() * scale)
            for (i in 1..3) { val y = size.height * i / 4; drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f) }
            for (i in 1..3) { val x = size.width * i / 4; drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f) }
            fun path(seg: List<LatLon>) = Path().apply { seg.forEachIndexed { i, l -> val o = p(l); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
            planned.forEach { if (it.size > 1) drawPath(path(it), Info.copy(alpha = 0.7f), style = Stroke(6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))) }
            segments.forEach { if (it.size > 1) drawPath(path(it), Honey, style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
            targets.forEach { t -> val r = (t.radiusM / 111_320.0 * scale).toFloat().coerceAtLeast(6f); drawCircle(t.color.copy(alpha = 0.3f), r, p(t.pos)); drawCircle(t.color, r, p(t.pos), style = Stroke(2f)) }
            segments.firstOrNull { it.isNotEmpty() }?.first()?.let { drawCircle(Good, 9f, p(it)) }
            segments.lastOrNull { it.isNotEmpty() }?.last()?.let { drawCircle(Bad, 9f, p(it)) }
        }
        Text("Offline trace", Modifier.align(Alignment.BottomEnd).padding(6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
