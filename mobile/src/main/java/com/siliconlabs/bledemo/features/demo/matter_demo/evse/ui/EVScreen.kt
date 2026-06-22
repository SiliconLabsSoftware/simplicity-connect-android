@file:OptIn(ExperimentalMaterialApi::class)

package com.siliconlabs.bledemo.features.demo.matter_demo.evse.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.features.demo.matter_demo.evse.presentation.EVViewModel
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EVScreen(
    vm: EVViewModel,
    energyEvseModeCluster: chip.devicecontroller.ChipClusters.EnergyEvseModeCluster,
    onSelectMode: (Int) -> Unit
) {
    // Reference cluster to avoid unused parameter warning (future subscription point)
    LaunchedEffect(energyEvseModeCluster) { /* ready for future attribute subscriptions */ }
    val ui by vm.ui.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    // Disable default Scaffold window insets to avoid double spacing below host toolbar
    Scaffold(
        // Transparent so @drawable/modal_bg on matter_container (activity_matter_demo) shows through
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        // Acquire navigation bar bottom inset (0 on gesture nav)
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val footerExtra = 8.dp
        val footerBottomPadding = if (navBarBottom > 0.dp) navBarBottom + footerExtra else footerExtra
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp), // compact top spacing
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 4.dp) // slightly reduced bottom gap
                ) {
                    Text(
                        text = "Electric Vehicle",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily(Font(R.font.stolzl_medium)),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        ),
                        color = colorResource(R.color.silabs_redtheme_checkbox_text_color)
                    )
                    Text(
                        text = "ID: ${ui.vehicleId ?: "--"}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily(Font(R.font.stolzl_medium)),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        ),
                        color = colorResource(R.color.silabs_redtheme_checkbox_text_color)
                    )
                }
                Spacer(Modifier.height(16.dp))
                CarBatteryRing(
                    percent = ui.percent,
                    radius = 130.dp, // increased from 120.dp
                    stroke = 14.dp,
                    remainingSeconds = ui.remainingSeconds,
                    ringColor = colorResource(R.color.silabs_rebranding_2373_hyperlink_text_color),
                    baseColor = MaterialTheme.colorScheme.outlineVariant,
                    showModePicker = ui.showModePicker
                )
                Spacer(Modifier.height(24.dp))

                // Connection card — consistent padding, vertical rhythm, softer elevation
                ElevatedCard(
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    val stateColor = when (ui.chargingStateCode) {
                        3 -> colorResource(R.color.silabs_rebranding_2526_blinky_color_on) // Charging - green
                        4 -> Color(0xFFFFA000) // Discharging - amber
                        6 -> MaterialTheme.colorScheme.error // Fault - red
                        else -> if (ui.connected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val cardHorizontalPadding = 20.dp
                    val rowVerticalPadding = 14.dp
                    val dividerSpacing = 12.dp

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = cardHorizontalPadding,
                                vertical = 16.dp
                            )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "EVS Connection",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily(Font(R.font.stolzl_medium)),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                ),
                                color = colorResource(R.color.silabs_redtheme_checkbox_text_color)
                            )
                            Text(
                                ui.chargingStateLabel
                                    ?: (if (ui.connected) "Connected" else "Disconnected"),
                                color = stateColor,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = dividerSpacing),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = rowVerticalPadding)
                                .clickable { vm.onChargingModeClick() },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Charging mode",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily(Font(R.font.stolzl_medium)), fontSize = 14.sp, fontWeight = FontWeight.Medium
                                ),
                                color = colorResource(R.color.silabs_redtheme_checkbox_text_color)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 12.dp)
                            ) {
                                Text(
                                    ui.currentModeId?.let { ui.modeLabels[it] } ?: "--",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colorResource(R.color.silabs_rebranding_2373_hyperlink_text_color),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    modifier = Modifier.padding(start = 6.dp),
                                    tint = colorResource(R.color.silabs_rebranding_2373_hyperlink_text_color)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = footerBottomPadding),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        "The EV energy data will refresh every sec.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily(Font(R.font.stolzl_regular)),
                            fontWeight = FontWeight.Normal, fontSize = 12.sp
                        ),
                        color = colorResource(R.color.silabs_redtheme_checkbox_text_color)
                    )
                }
            }
            if (ui.showModePicker) {
                ModalBottomSheet(
                    onDismissRequest = { vm.dismissPicker() },
                    sheetState = sheetState,
                    dragHandle = null
                ) {
                    ModePicker(
                        currentId = ui.currentModeId,
                        labels = ui.modeLabels,
                        onSelect = { onSelectMode(it) },
                        onCancel = { vm.dismissPicker() }
                    )
                }
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun CarBatteryRing(
    percent: Int,
    radius: Dp,
    stroke: Dp,
    remainingSeconds: Long,
    ringColor: Color,
    baseColor: Color,
    showModePicker: Boolean
) {
    val progress = (percent.coerceIn(0, 100) / 100f)
    val animated = animateFloatAsState(targetValue = progress, label = "ring")
    val pxRadius = with(LocalDensity.current) { radius.toPx() }
    val pxStroke = with(LocalDensity.current) { stroke.toPx() }
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(radius * 2)) {
            // Base ring
            drawCircle(
                color = baseColor,
                radius = pxRadius,
                style = Stroke(width = pxStroke)
            )
            // Progress arc
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animated.value,
                useCenter = false,
                style = Stroke(width = pxStroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(
                    if (showModePicker) R.drawable.ic_electric_gray_shade else R.drawable.ic_electric_car
                ),
                contentDescription = null,
                modifier = Modifier
                    .size(190.dp)
                    .padding(top = if (showModePicker) 25.dp else 0.dp),
                colorFilter = null
            )
            Text(
                "$percent%",
                fontSize = 20.sp,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .offset(y = (-100).dp)
            )
            /*if (remainingSeconds > 0) {
                val mins = remainingSeconds / 60
                val hrs = mins / 60
                val remMins = mins % 60
                val timeStr = if (hrs > 0) String.format(Locale.getDefault(), "%dh %02dm", hrs, remMins) else String.format(Locale.getDefault(), "%dm", remMins)
                Text(
                    timeStr,
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .offset(y = (-95).dp)
                )
            }*/
        }

    }
}

@Composable
private fun ModePicker(
    currentId: Int?,
    labels: Map<Int, String>,
    onSelect: (Int) -> Unit,
    onCancel: () -> Unit
) {
    Column(Modifier.padding(12.dp)) {
        Text(
            "Select Mode",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily(Font(R.font.stolzl_medium)),
                fontWeight = FontWeight.Medium, fontSize = 14.sp
            ),
            color = colorResource(R.color.silabs_redtheme_checkbox_text_color),
            modifier = Modifier.padding(4.dp)
        )
        Spacer(Modifier.height(8.dp))
        labels.forEach { (modeId, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(modeId) }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = modeId == currentId,
                    onClick = { onSelect(modeId) }
                )
                Text(
                    label,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily(Font(R.font.stolzl_regular)),
                        fontWeight = FontWeight.Normal, fontSize = 12.sp
                    ),
                    color = colorResource(R.color.silabs_redtheme_checkbox_text_color)
                )
            }
        }
        if (labels.isEmpty()) {
            Text(
                "No modes reported by device",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) { Text("Cancel",style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily(Font(R.font.stolzl_regular)),
                fontWeight = FontWeight.Normal, fontSize = 12.sp
            ), color = colorResource(R.color.silabs_redtheme_checkbox_text_color)) }
        }
    }
}
