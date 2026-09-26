package com.hanenashi.dauba

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import java.io.File

private val Coral = Color(0xFFFF927B)
private val Ink = Color(0xFF171A1B)
private val Paper = Color(0xFFF5F0E8)
private val Muted = Color(0xFFABB1AD)
private val Palette = listOf(0xFFFF5C5B, 0xFFFFC84A, 0xFF66D9A8, 0xFF57B8FF, 0xFFF7F7F2, 0xFF171A1B)
private val ColorNames = listOf("Red", "Yellow", "Green", "Blue", "White", "Black")

class MainActivity : ComponentActivity() {
    private val model: EditorModel by viewModels()
    private var incoming by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        receive(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Coral, onPrimary = Ink,
                background = Ink, surface = Ink, onSurface = Paper, onBackground = Paper,
                secondary = Coral, secondaryContainer = Color(0xFF42332F), onSecondaryContainer = Coral,
                surfaceContainer = Color(0xFF242829), surfaceContainerHigh = Color(0xFF2B3031),
                surfaceVariant = Color(0xFF2B3031), onSurfaceVariant = Muted)) {
                Editor(model, incoming, {
                    incoming = null
                    setIntent(Intent(this, MainActivity::class.java))
                }, ::share)
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }

    private fun receive(intent: Intent) {
        incoming = when (intent.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
    }

    private fun share(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Dauba packet", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Dauba packet"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Editor(model: EditorModel, incoming: Uri?, consumed: () -> Unit, share: (File) -> Unit) {
    val project = model.project
    var tool by rememberSaveable { mutableStateOf(Tool.Brush) }
    var colorIndex by rememberSaveable { mutableIntStateOf(0) }
    var sizeIndex by rememberSaveable { mutableIntStateOf(1) }
    var rotation by rememberSaveable(project?.id) { mutableIntStateOf(0) }
    var fitToken by remember { mutableIntStateOf(0) }
    var showNotes by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var pendingImport by rememberSaveable { mutableStateOf<String?>(null) }
    var packetPath by rememberSaveable { mutableStateOf<String?>(null) }
    var noteOpen by rememberSaveable(project?.id) { mutableStateOf(false) }
    var noteId by rememberSaveable { mutableStateOf<Int?>(null) }
    var noteX by rememberSaveable { mutableFloatStateOf(0f) }
    var noteY by rememberSaveable { mutableFloatStateOf(0f) }
    var noteText by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }

    fun editNote(point: Point, existing: Note?) {
        noteId = existing?.id; noteX = point.x; noteY = point.y
        noteText = existing?.text ?: ""; noteOpen = true
    }
    fun requestImport(uri: Uri) {
        if (project != null) pendingImport = uri.toString() else model.import(uri)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::requestImport) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val file = packetPath?.let(::File)
        if (uri != null && file != null) model.savePacket(file, uri)
        packetPath = null
    }
    LaunchedEffect(incoming, model.busy) {
        if (incoming != null && !model.busy) { requestImport(incoming); consumed() }
    }
    LaunchedEffect(model.message) {
        model.message?.let { message ->
            snackbar.showSnackbar(message, duration = SnackbarDuration.Long)
            model.message = null
        }
    }

    Scaffold(containerColor = Ink, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("dauba", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                    Text("PAINT WHAT YOU MEAN", color = Muted, fontSize = 9.sp, letterSpacing = 1.8.sp, fontFamily = FontFamily.Monospace)
                }
                Action(Icons.Outlined.FolderOpen, "Open screenshot", !model.busy) { picker.launch(arrayOf("image/*")) }
                if (project != null) {
                    FilledTonalButton(onClick = { showExport = true }, enabled = !model.busy, contentPadding = PaddingValues(horizontal = 16.dp)) {
                        Text("Export", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp)); Icon(Icons.Outlined.IosShare, null, Modifier.size(18.dp))
                    }
                } else Action(Icons.Outlined.Info, "About Dauba") { showHelp = true }
            }

            if (project == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(Modifier.padding(32.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.Start) {
                        Surface(shape = RoundedCornerShape(22.dp), color = Coral.copy(alpha = .13f)) {
                            Icon(Icons.Outlined.Draw, null, tint = Coral, modifier = Modifier.padding(22.dp).size(52.dp))
                        }
                        Spacer(Modifier.height(32.dp))
                        Text("Less explaining.\nMore pointing.", fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-1).sp)
                        Spacer(Modifier.height(16.dp))
                        Text("Open a real screenshot. Draw your changes, pin a note, and send the whole picture to your coding agent.", color = Muted, fontSize = 16.sp, lineHeight = 25.sp)
                        Spacer(Modifier.height(28.dp))
                        Button(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !model.busy,
                            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Outlined.AddPhotoAlternate, null); Spacer(Modifier.width(10.dp)); Text("Open screenshot")
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Or share an image to Dauba from any app.", color = Muted, fontSize = 12.sp)
                    }
                    if (model.busy) CircularProgressIndicator()
                }
                Text("ON DEVICE  /  NO ACCOUNT  /  JUST MARKUP", color = Muted,
                    fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(20.dp))
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f))
                    Text("${project.bitmap.width} × ${project.bitmap.height}", fontSize = 10.sp, color = Muted, fontFamily = FontFamily.Monospace)
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp))) {
                    AndroidView(factory = { context -> AnnotationCanvas(context) }, modifier = Modifier.fillMaxSize(), update = { canvas ->
                        canvas.tool = tool
                        canvas.brushColor = Palette[colorIndex].toInt()
                        canvas.brushWidth = project.bitmap.width * listOf(.003f, .007f, .015f)[sizeIndex]
                        canvas.onCommit = model::commit
                        canvas.onNote = ::editNote
                        canvas.update(project, rotation, fitToken)
                    })
                    if (model.busy) Box(Modifier.fillMaxSize().background(Ink.copy(alpha = .7f)).clickable {}, contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Action(Icons.AutoMirrored.Outlined.Undo, "Undo", model.canUndo && !model.busy, model::undo)
                    Action(Icons.AutoMirrored.Outlined.Redo, "Redo", model.canRedo && !model.busy, model::redo)
                    Action(Icons.Outlined.RotateLeft, "Rotate 90 degrees counterclockwise", !model.busy) { rotation = (rotation + 3) % 4 }
                    Action(Icons.Outlined.RotateRight, "Rotate 90 degrees clockwise", !model.busy) { rotation = (rotation + 1) % 4 }
                    Action(Icons.Outlined.FitScreen, "Fit screenshot", !model.busy) { fitToken++ }
                    TextButton(onClick = { showNotes = true }, enabled = !model.busy) {
                        Icon(Icons.AutoMirrored.Outlined.Notes, null, Modifier.size(19.dp))
                        Spacer(Modifier.width(5.dp)); Text("${project.drawing.notes.size} notes", fontSize = 12.sp)
                    }
                }
                HorizontalDivider(color = Color(0xFF323737))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp).height(58.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (tool == Tool.Brush) {
                        Palette.forEachIndexed { index, color ->
                            Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = !model.busy) { colorIndex = index }
                                .semantics { contentDescription = "${ColorNames[index]} brush${if (colorIndex == index) ", selected" else ""}" }
                                .padding(5.dp).border(if (colorIndex == index) 2.dp else 1.dp,
                                    if (colorIndex == index) Paper else Muted.copy(alpha = .4f), CircleShape).padding(4.dp).background(Color(color), CircleShape))
                        }
                        Spacer(Modifier.width(8.dp))
                        listOf(3.dp, 6.dp, 10.dp).forEachIndexed { index, size ->
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(if (sizeIndex == index) Color(0xFF3B4141) else Color.Transparent)
                                .clickable(enabled = !model.busy) { sizeIndex = index }.semantics { contentDescription = "${listOf("Fine", "Medium", "Bold")[index]} brush size" },
                                contentAlignment = Alignment.Center) { Box(Modifier.size(size).background(Paper, CircleShape)) }
                        }
                    } else Text(when (tool) {
                        Tool.Eraser -> "Touch a stroke to erase it. Undo brings it back."
                        Tool.Note -> "Tap to pin a note. Tap an anchor to edit."
                        else -> "Drag to move. Pinch to zoom with any tool."
                    }, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tool.entries.forEach { item ->
                        val selected = tool == item
                        val icon = when (item) {
                            Tool.Brush -> Icons.Outlined.Draw
                            Tool.Eraser -> Icons.Outlined.AutoFixNormal
                            Tool.Note -> Icons.Outlined.AddComment
                            Tool.Hand -> Icons.Outlined.PanTool
                        }
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (selected) Coral else Color(0xFF272C2D))
                            .clickable(enabled = !model.busy) { tool = item }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, item.name, tint = if (selected) Ink else Paper, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(3.dp)); Text(item.name, fontSize = 11.sp, color = if (selected) Ink else Paper, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(model.saveStatus, color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showHelp = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.HelpOutline, "Help", Modifier.size(18.dp), tint = Muted)
                    }
                }
            }
        }
    }

    if (pendingImport != null) AlertDialog(onDismissRequest = { pendingImport = null },
        title = { Text("Start a new screenshot?") },
        text = { Text("Dauba keeps one working screenshot. Export the current one first if you want to keep its markup and notes.") },
        confirmButton = { TextButton(onClick = { pendingImport?.let { model.import(Uri.parse(it)) }; pendingImport = null }) { Text("Open new screenshot") } },
        dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Keep editing") } })

    if (noteOpen && project != null) {
        val existing = project.drawing.notes.find { it.id == noteId }
        AlertDialog(onDismissRequest = { noteOpen = false },
            title = { Text("Note ${existing?.label ?: anchorLabel(project.drawing.nextNoteId)}") },
            text = { Column {
                OutlinedTextField(value = noteText, onValueChange = { if (it.length <= 10_000) noteText = it },
                    placeholder = { Text("What should change here?") }, minLines = 3, maxLines = 8,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth())
                if (existing != null) TextButton(onClick = { model.delete(existing); noteOpen = false }) { Text("Delete note") }
            } },
            confirmButton = { TextButton(enabled = noteText.isNotBlank(), onClick = {
                model.note(Point(noteX, noteY), existing, noteText); noteOpen = false
            }) { Text("Save note") } },
            dismissButton = { TextButton(onClick = { noteOpen = false }) { Text("Cancel") } })
    }

    if (showNotes && project != null) ModalBottomSheet(onDismissRequest = { showNotes = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text("Anchored notes", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Letters connect your words to the screenshot.", color = Muted)
            Spacer(Modifier.height(16.dp))
            if (project.drawing.notes.isEmpty()) {
                Text("Choose Note, then tap the screenshot to place your first anchor.", modifier = Modifier.padding(vertical = 24.dp))
            }
            project.drawing.notes.forEach { note ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                    showNotes = false; editNote(note.point, note)
                }.padding(vertical = 16.dp, horizontal = 8.dp), verticalAlignment = Alignment.Top) {
                    Surface(color = Coral, shape = CircleShape) { Text(note.label, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) }
                    Text(note.text, modifier = Modifier.weight(1f).padding(start = 14.dp), fontSize = 15.sp)
                    Icon(Icons.Outlined.Edit, "Edit note ${note.label}", modifier = Modifier.size(18.dp), tint = Muted)
                }
                HorizontalDivider(color = Color(0xFF323737))
            }
        }
    }

    if (showExport) AlertDialog(onDismissRequest = { showExport = false },
        title = { Text("Ready for your agent") },
        text = { Column {
            Text("One ZIP. The original screenshot, full-resolution markup, readable Markdown notes, and anchor coordinates.")
            Spacer(Modifier.height(12.dp))
            Text("Export always uses the original image orientation, even when your canvas is rotated.", color = Muted)
        } },
        confirmButton = { TextButton(onClick = { showExport = false; model.export(share) }) { Text("Share ZIP") } },
        dismissButton = { TextButton(onClick = { showExport = false; model.export { file -> packetPath = file.path; saver.launch(file.name) } }) { Text("Save ZIP") } })

    if (showHelp) AlertDialog(onDismissRequest = { showHelp = false }, title = { Text("Paint what you mean.") },
        text = { Text("One finger draws or uses the selected tool. Two fingers move and zoom. Adding a second finger cancels a tentative mark.\n\nThe eraser removes whole strokes. Notes can be edited by tapping their anchors with the Note tool, or from the notes list. Undo covers both.\n\nRotation buttons turn the canvas by 90°. Fit restores the current view. Exports keep the original orientation and resolution.\n\nYour current screenshot and edits save automatically on this device. Export before opening another screenshot.\n\nDauba 0.1.0 · First test build") },
        confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Got it") } })
}

@Composable
private fun Action(icon: ImageVector, label: String, enabled: Boolean = true, click: () -> Unit) {
    IconButton(onClick = click, enabled = enabled) { Icon(icon, label, Modifier.size(22.dp)) }
}
