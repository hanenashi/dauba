package com.hanenashi.dauba

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File

class MainActivity : ComponentActivity() {
    private val model: EditorModel by viewModels()
    private var incoming by mutableStateOf<Uri?>(null)
    private var editorFullscreen = false

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
                }, ::share, ::setEditorFullscreen)
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }

    private fun setEditorFullscreen(enabled: Boolean) {
        editorFullscreen = enabled
        applySystemBars()
    }

    private fun applySystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (editorFullscreen) hide(WindowInsetsCompat.Type.systemBars())
            else show(WindowInsetsCompat.Type.systemBars())
        }
    }

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
private fun Editor(model: EditorModel, incoming: Uri?, consumed: () -> Unit, share: (File) -> Unit, fullscreen: (Boolean) -> Unit) {
    val project = model.project
    var tool by rememberSaveable { mutableStateOf(Tool.Brush) }
    var colorIndex by rememberSaveable { mutableIntStateOf(0) }
    var sizeIndex by rememberSaveable { mutableIntStateOf(1) }
    var rotation by rememberSaveable(project?.id) { mutableIntStateOf(0) }
    var fitToken by remember { mutableIntStateOf(0) }
    var showNotes by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showTools by rememberSaveable(project?.id) { mutableStateOf(false) }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var gesturing by remember { mutableStateOf(false) }
    var pendingImport by rememberSaveable { mutableStateOf<String?>(null) }
    var packetPath by rememberSaveable { mutableStateOf<String?>(null) }
    var noteOpen by rememberSaveable(project?.id) { mutableStateOf(false) }
    var noteId by rememberSaveable { mutableStateOf<Int?>(null) }
    var noteX by rememberSaveable { mutableFloatStateOf(0f) }
    var noteY by rememberSaveable { mutableFloatStateOf(0f) }
    var noteText by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }

    DisposableEffect(project != null) {
        fullscreen(project != null)
        onDispose { fullscreen(false) }
    }
    BackHandler(enabled = showTools && !showMenu && !showNotes && !showExport && !showHelp && !noteOpen && pendingImport == null) {
        showTools = false
    }

    fun editNote(point: Point, existing: Note?) {
        showTools = false
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

    // The full-window canvas has sibling overlays. Tool visibility never changes its size.
    Box(Modifier.fillMaxSize().background(Ink)) {
        if (project == null) {
            WelcomeScreen(model.busy, { picker.launch(arrayOf("image/*")) }, { showHelp = true })
        } else {
            AndroidView(factory = { context -> AnnotationCanvas(context) },
                modifier = Modifier.fillMaxSize().testTag("annotation-canvas"), update = { canvas ->
                    canvas.tool = tool
                    canvas.brushColor = Palette[colorIndex].toInt()
                    canvas.brushWidth = project.bitmap.width * listOf(.003f, .007f, .015f)[sizeIndex]
                    canvas.onCommit = model::commit
                    canvas.onNote = ::editNote
                    canvas.onGestureStart = { showTools = false; gesturing = true }
                    canvas.onGestureEnd = { gesturing = false }
                    canvas.update(project, rotation, fitToken)
                })
            if (!gesturing) Box(Modifier.align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (showTools) QuickTools(tool, colorIndex, sizeIndex, model.canUndo, model.canRedo, !model.busy,
                    chooseTool = { tool = it }, chooseColor = { colorIndex = it }, chooseSize = { sizeIndex = it },
                    undo = model::undo, redo = model::redo, fit = { fitToken++ },
                    menu = { showTools = false; showMenu = true }, close = { showTools = false })
                else ToolPill(tool, colorIndex, !model.busy) { showTools = true }
            }
        }
        if (model.busy) Box(Modifier.fillMaxSize().background(Ink.copy(alpha = .65f)).clickable {}, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing))
    }

    if (showMenu && project != null) ModalBottomSheet(onDismissRequest = { showMenu = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Screenshot", style = MaterialTheme.typography.headlineSmall)
            Text(project.name, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Text("${project.bitmap.width} × ${project.bitmap.height} · ${model.saveStatus}", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            MenuAction(Icons.Outlined.IosShare, "Export packet", !model.busy) { showMenu = false; showExport = true }
            MenuAction(Icons.AutoMirrored.Outlined.Notes, "Notes (${project.drawing.notes.size})", !model.busy) { showMenu = false; showNotes = true }
            MenuAction(Icons.Outlined.FolderOpen, "Open / replace screenshot", !model.busy) { showMenu = false; picker.launch(arrayOf("image/*")) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFF323737))
            MenuAction(Icons.Outlined.RotateLeft, "Rotate 90° counterclockwise", !model.busy) { rotation = (rotation + 3) % 4; showMenu = false }
            MenuAction(Icons.Outlined.RotateRight, "Rotate 90° clockwise", !model.busy) { rotation = (rotation + 1) % 4; showMenu = false }
            MenuAction(Icons.Outlined.Info, "Help / About Dauba") { showMenu = false; showHelp = true }
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
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Tap the tool pill to open controls. Starting a gesture hides them without moving the screenshot or swallowing your first stroke.\n\nOne finger uses the selected tool. Two fingers move and zoom; adding a second finger cancels a tentative mark. The eraser removes whole strokes.\n\nThe ••• menu holds Export, Notes, Open, and 90° rotation. Fit is in the tool tray. Exports keep the original orientation and resolution.\n\nTap an anchor with the Note tool, or open Notes, to edit its text.\n\nYour current screenshot saves automatically. Export before opening another. Swipe from a screen edge to reveal Android's system bars.")
            Spacer(Modifier.height(16.dp))
            Text("Dauba ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}", color = Muted)
        } },
        confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Got it") } })
}

@Composable
private fun WelcomeScreen(busy: Boolean, open: () -> Unit, help: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("dauba", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                Text("PAINT WHAT YOU MEAN", color = Muted, fontSize = 9.sp, letterSpacing = 1.8.sp, fontFamily = FontFamily.Monospace)
            }
            ChromeAction(Icons.Outlined.FolderOpen, "Open screenshot", !busy, open)
            ChromeAction(Icons.Outlined.Info, "About Dauba", click = help)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(32.dp).verticalScroll(rememberScrollState())) {
                Surface(shape = RoundedCornerShape(22.dp), color = Coral.copy(alpha = .13f)) {
                    Icon(Icons.Outlined.Draw, null, tint = Coral, modifier = Modifier.padding(22.dp).size(52.dp))
                }
                Spacer(Modifier.height(32.dp))
                Text("Less explaining.\nMore pointing.", fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-1).sp)
                Spacer(Modifier.height(16.dp))
                Text("Open a real screenshot. Draw your changes, pin a note, and send the whole picture to your coding agent.", color = Muted, fontSize = 16.sp, lineHeight = 25.sp)
                Spacer(Modifier.height(28.dp))
                Button(onClick = open, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null); Spacer(Modifier.width(10.dp)); Text("Open screenshot")
                }
                Spacer(Modifier.height(14.dp))
                Text("Or share an image to Dauba from any app.", color = Muted, fontSize = 12.sp)
            }
        }
        Text("ON DEVICE  /  NO ACCOUNT  /  JUST MARKUP", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(20.dp))
    }
}
