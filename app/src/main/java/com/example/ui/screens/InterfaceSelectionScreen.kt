package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.BorderDark
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberPrimary
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.CaptureInterfaceOption
import com.example.ui.viewmodel.WireRifterViewModel

@Composable
fun InterfaceSelectionScreen(
    viewModel: WireRifterViewModel,
    modifier: Modifier = Modifier
) {
    val interfaces by viewModel.captureInterfaces.collectAsState()
    val selectedCount = interfaces.count { it.selected }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(id = R.mipmap.wirerifter_icon),
                contentDescription = "WireRifter",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RectangleShape)
                    .background(DarkSurfaceElevated)
                    .border(1.dp, BorderDark)
            )
            Text(
                text = "Capture Interfaces",
                color = TextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$selectedCount selected",
                color = CyberPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        InterfaceHeaderRow()

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, BorderDark)
        ) {
            items(interfaces) { item ->
                InterfaceRow(
                    item = item,
                    onClick = { viewModel.toggleCaptureInterface(item.id) }
                )
            }
        }

        Button(
            onClick = viewModel::startCaptureFromSelectedInterfaces,
            colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
            shape = RectangleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(top = 6.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DarkBg, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Start Capture", color = DarkBg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InterfaceHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(DarkSurfaceElevated)
            .border(1.dp, BorderDark)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("Interface Name", Modifier.weight(1.3f))
        HeaderCell("Type", Modifier.weight(1f))
        HeaderCell("IP Address", Modifier.weight(1.4f))
        HeaderCell("Activity", Modifier.weight(1.1f))
        HeaderCell("Status", Modifier.weight(0.8f))
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text = text,
        color = TextGray,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier
    )
}

@Composable
private fun InterfaceRow(
    item: CaptureInterfaceOption,
    onClick: () -> Unit
) {
    val bg = if (item.selected) CyberPrimary.copy(alpha = 0.22f) else DarkSurface
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(bg)
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextCell(item.name, Modifier.weight(1.3f), if (item.selected) TextWhite else CyberPrimary)
            TextCell(item.type, Modifier.weight(1f), TextWhite)
            TextCell(item.ipAddress, Modifier.weight(1.4f), TextWhite)
            ActivitySparkline(item.activity, Modifier.weight(1.1f))
            TextCell(item.status, Modifier.weight(0.8f), if (item.status == "Ready") CyberGreen else TextGray)
        }
        Divider(thickness = 1.dp, color = BorderDark)
    }
}

@Composable
private fun TextCell(
    text: String,
    modifier: Modifier,
    color: Color
) {
    Text(
        text = text,
        color = color,
        fontSize = 9.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
private fun ActivitySparkline(
    values: List<Int>,
    modifier: Modifier
) {
    Canvas(
        modifier = modifier
            .height(22.dp)
            .padding(horizontal = 4.dp)
    ) {
        val maxValue = values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val step = if (values.size > 1) size.width / (values.size - 1) else size.width
        values.forEachIndexed { index, value ->
            val x = index * step
            val y = size.height - ((value.toFloat() / maxValue) * size.height)
            drawLine(
                color = CyberPrimary,
                start = Offset(x, size.height),
                end = Offset(x, y),
                strokeWidth = 2f
            )
        }
    }
}
