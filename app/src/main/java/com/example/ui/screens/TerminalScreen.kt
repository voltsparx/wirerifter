package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.TerminalLine
import com.example.ui.viewmodel.TerminalLineKind
import com.example.ui.viewmodel.WireRifterViewModel

@Composable
fun TerminalScreen(
    viewModel: WireRifterViewModel,
    modifier: Modifier = Modifier
) {
    val lines by viewModel.terminalLines.collectAsState()
    val input by viewModel.terminalInput.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Terminal", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                Text("Local network commands and packet workflow helpers", fontSize = 11.sp, color = TextGray, fontFamily = FontFamily.Monospace)
            }
            Icon(Icons.Default.Code, contentDescription = null, tint = CyberPrimary, modifier = Modifier.size(28.dp))
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurface)
                .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                .padding(12.dp)
                .testTag("terminal_output"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lines) { line ->
                TerminalLineRow(line)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = viewModel::onTerminalInputChange,
                singleLine = true,
                placeholder = {
                    Text("help", color = TextGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("terminal_input"),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = TextWhite,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberPrimary,
                    unfocusedBorderColor = BorderDark,
                    cursorColor = CyberPrimary,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = viewModel::submitTerminalCommand,
                colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(54.dp)
                    .testTag("terminal_send")
            ) {
                Icon(Icons.Default.Send, contentDescription = "Run command", tint = DarkBg, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun TerminalLineRow(line: TerminalLine) {
    val color = when (line.kind) {
        TerminalLineKind.COMMAND -> CyberPrimary
        TerminalLineKind.SUCCESS -> CyberGreen
        TerminalLineKind.WARNING -> CyberAmber
        TerminalLineKind.ERROR -> CyberRed
        TerminalLineKind.INFO -> TextGray
        TerminalLineKind.OUTPUT -> TextWhite
    }
    Text(
        text = line.text,
        color = color,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontFamily = FontFamily.Monospace
    )
}
