package com.superapp.app.features.dashboard.ui

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.superapp.app.core.ui.rememberIsVisible
import com.superapp.app.features.dashboard.ACTION_GRANT_LOCATION
import com.superapp.app.features.dashboard.DashboardCategory
import com.superapp.app.features.dashboard.DashboardRepository
import com.superapp.app.features.dashboard.DashboardStyleStore
import com.superapp.app.features.dashboard.InfoSection
import com.superapp.app.features.dashboard.collectors.SensorCollector
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Central Device Dashboard. Category chips on top; data refreshes only while the screen is visible
 * and only for the selected category (see DashboardRepository.refreshIntervalMs).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val repo = remember { DashboardRepository(context) }
    val styleStore = remember { DashboardStyleStore(context) }
    var style by remember { mutableStateOf(styleStore.load()) }
    var showStyle by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(0) }
    val category = DashboardCategory.values()[selected]
    val visible by rememberIsVisible()

    var sections by remember { mutableStateOf<List<InfoSection>>(emptyList()) }
    var sensors by remember { mutableStateOf<List<Pair<InfoSection, Sensor>>>(emptyList()) }
    var liveKey by remember { mutableStateOf<String?>(null) }

    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        locationGranted = it
    }

    // Polling loop: restarts on category/visibility change and ends when the screen is hidden.
    LaunchedEffect(category, visible, locationGranted) {
        if (!visible) return@LaunchedEffect
        liveKey = null
        if (category == DashboardCategory.SENSORS) {
            sensors = repo.sensors()
            return@LaunchedEffect
        }
        while (isActive) {
            sections = repo.load(category, locationGranted)
            val interval = repo.refreshIntervalMs(category)
            if (interval <= 0L) break
            delay(interval)
        }
    }

    val bg = style.backgroundColor?.let { Color(it) } ?: MaterialTheme.colorScheme.background
    Column(Modifier.fillMaxSize().background(bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) TextButton(onClick = onBack) { Text("< Back") }
            Text("Device Dashboard", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(8.dp))
            OutlinedButton(onClick = { showStyle = true }) { Text("Style") }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(DashboardCategory.values().toList()) { c ->
                FilterChip(selected = c == category, onClick = { selected = c.ordinal }, label = { Text(c.title) })
            }
        }

        val onAction: (String) -> Unit = { id ->
            if (id == ACTION_GRANT_LOCATION) locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(style.spacingDp.dp)
        ) {
            if (category == DashboardCategory.SENSORS) {
                item { Text("${sensors.size} sensors reported by this device", color = MaterialTheme.colorScheme.onBackground) }
                items(sensors, key = { SensorCollector.key(it.second) }) { (section, sensor) ->
                    val key = SensorCollector.key(sensor)
                    InfoCard(section, style) {
                        TextButton(onClick = { liveKey = if (liveKey == key) null else key }) {
                            Text(if (liveKey == key) "Stop live values" else "Show live values")
                        }
                        LiveSensorValues(sensor, active = liveKey == key && visible)
                    }
                }
            } else {
                items(sections, key = { it.title }) { section -> InfoCard(section, style, onAction = onAction) }
            }
        }
    }

    if (showStyle) {
        StyleDialog(style, { showStyle = false }) {
            style = it
            styleStore.save(it)
            showStyle = false
        }
    }
}
