package com.hanenashi.dauba

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Coral = Color(0xFFFF927B)
internal val Ink = Color(0xFF171A1B)
internal val Paper = Color(0xFFF5F0E8)
internal val Muted = Color(0xFFABB1AD)
internal val Palette = listOf(0xFFFF5C5B, 0xFFFFC84A, 0xFF66D9A8, 0xFF57B8FF, 0xFFF7F7F2, 0xFF171A1B)
private val ColorNames = listOf("Red", "Yellow", "Green", "Blue", "White", "Black")

@Composable
internal fun ToolPill(tool: Tool, colorIndex: Int, enabled: Boolean, open: () -> Unit) {
    Surface(onClick = open, enabled = enabled, shape = CircleShape, color = Ink.copy(alpha = .94f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Paper.copy(alpha = .18f)), shadowElevation = 6.dp,
        modifier = Modifier.semantics { contentDescription = "Open tools" }) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (tool == Tool.Brush) Box(Modifier.size(10.dp).border(1.dp, Muted, CircleShape).background(Color(Palette[colorIndex]), CircleShape))
            else Icon(toolIcon(tool), null, Modifier.size(18.dp), tint = Coral)
            Text(tool.name, color = Paper, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Outlined.ExpandLess, null, Modifier.size(18.dp), tint = Muted)
        }
    }
}

/** Non-modal: the first touch outside the tray belongs to the canvas, not a scrim. */
@Composable
internal fun QuickTools(
    tool: Tool, colorIndex: Int, sizeIndex: Int, canUndo: Boolean, canRedo: Boolean, enabled: Boolean,
    chooseTool: (Tool) -> Unit, chooseColor: (Int) -> Unit, chooseSize: (Int) -> Unit,
    undo: () -> Unit, redo: () -> Unit, fit: () -> Unit, menu: () -> Unit, close: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(24.dp), color = Ink,
        border = androidx.compose.foundation.BorderStroke(1.dp, Paper.copy(alpha = .14f)), shadowElevation = 12.dp,
        modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ChromeAction(Icons.AutoMirrored.Outlined.Undo, "Undo", canUndo && enabled, undo)
                ChromeAction(Icons.AutoMirrored.Outlined.Redo, "Redo", canRedo && enabled, redo)
                ChromeAction(Icons.Outlined.FitScreen, "Fit screenshot", enabled, fit)
                ChromeAction(Icons.Outlined.MoreHoriz, "More actions", enabled, menu)
                ChromeAction(Icons.Outlined.ExpandMore, "Hide tools", enabled, close)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Tool.entries.forEach { item ->
                    val selected = item == tool
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Coral else Color(0xFF272C2D))
                        .selectable(selected, enabled = enabled, role = Role.Tab, onClick = { chooseTool(item) })
                        .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(toolIcon(item), null, Modifier.size(20.dp), tint = if (selected) Ink else Paper)
                        Text(item.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Ink else Paper)
                    }
                }
            }
            if (tool == Tool.Brush) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Palette.forEachIndexed { index, color ->
                        Box(Modifier.size(48.dp).clip(CircleShape)
                            .selectable(colorIndex == index, enabled = enabled, role = Role.RadioButton, onClick = { chooseColor(index) })
                            .semantics { contentDescription = "${ColorNames[index]} brush" }.padding(7.dp)
                            .border(if (colorIndex == index) 2.dp else 1.dp, if (colorIndex == index) Paper else Muted.copy(alpha = .4f), CircleShape)
                            .padding(5.dp).background(Color(color), CircleShape))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Fine", "Medium", "Bold").forEachIndexed { index, name ->
                        Row(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (sizeIndex == index) Color(0xFF343B3B) else Color.Transparent)
                            .selectable(sizeIndex == index, enabled = enabled, role = Role.RadioButton, onClick = { chooseSize(index) })
                            .semantics { contentDescription = "$name brush size" },
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(listOf(3.dp, 6.dp, 10.dp)[index]).background(Paper, CircleShape))
                            Spacer(Modifier.width(8.dp)); Text(name, fontSize = 11.sp, color = Paper)
                        }
                    }
                }
            } else Text(when (tool) {
                Tool.Eraser -> "Touch a stroke to erase it."
                Tool.Note -> "Tap the image to place or edit a note."
                else -> "Drag to move. Two fingers zoom."
            }, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp))
        }
    }
}

internal fun toolIcon(tool: Tool): ImageVector = when (tool) {
    Tool.Brush -> Icons.Outlined.Draw
    Tool.Eraser -> Icons.Outlined.AutoFixNormal
    Tool.Note -> Icons.Outlined.AddComment
    Tool.Hand -> Icons.Outlined.PanTool
}

@Composable
internal fun ChromeAction(icon: ImageVector, label: String, enabled: Boolean = true, click: () -> Unit) {
    IconButton(onClick = click, enabled = enabled, modifier = Modifier.size(48.dp)) { Icon(icon, label, Modifier.size(22.dp)) }
}

@Composable
internal fun MenuAction(icon: ImageVector, title: String, enabled: Boolean = true, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, role = Role.Button, onClick = click)
        .padding(horizontal = 12.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = Coral)
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}
