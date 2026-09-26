package com.hanenashi.dauba

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditorModel(application: Application) : AndroidViewModel(application) {
    private val store = ProjectStore(application)
    private val operations = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    var project by mutableStateOf<Project?>(null)
        private set
    var busy by mutableStateOf(true)
        private set
    var message by mutableStateOf<String?>(null)
    var saveStatus by mutableStateOf("Saved on this device")
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    private var history = History()
    private var revision = 0

    init {
        viewModelScope.launch {
            for (operation in operations) {
                try { operation() }
                catch (e: Exception) { message = e.message ?: "Something went wrong. Please try again."; busy = false }
            }
        }
        enqueue {
            project = withContext(Dispatchers.IO) { store.load() }
            history = History(project?.drawing ?: Drawing())
            busy = false
        }
    }

    private fun enqueue(block: suspend () -> Unit) { operations.trySend(block) }

    fun import(uri: Uri) {
        if (busy) return
        busy = true
        enqueue {
            project = withContext(Dispatchers.IO) { store.import(uri) }
            history = History()
            canUndo = false; canRedo = false
            saveStatus = "Saved on this device"
            busy = false
        }
    }

    fun commit(drawing: Drawing) {
        if (busy || project == null) return
        history.commit(drawing)
        changed()
    }
    fun undo() { if (!busy) { history.undo(); changed() } }
    fun redo() { if (!busy) { history.redo(); changed() } }

    private fun changed() {
        val snapshot = project?.copy(drawing = history.current) ?: return
        project = snapshot
        canUndo = history.canUndo; canRedo = history.canRedo
        saveStatus = "Saving…"
        val version = ++revision
        enqueue {
            try {
                withContext(Dispatchers.IO) { store.save(snapshot) }
                if (version == revision) saveStatus = "Saved on this device"
            } catch (e: Exception) {
                saveStatus = "Save failed — export your work"
                throw e
            }
        }
    }

    fun note(point: Point, existing: Note?, text: String) {
        val drawing = project?.drawing ?: return
        if (existing == null) {
            commit(drawing.copy(notes = drawing.notes + Note(drawing.nextNoteId, point, text.trim()), nextNoteId = drawing.nextNoteId + 1))
        } else commit(drawing.copy(notes = drawing.notes.map { if (it.id == existing.id) it.copy(text = text.trim()) else it }))
    }

    fun delete(note: Note) {
        val drawing = project?.drawing ?: return
        commit(drawing.copy(notes = drawing.notes.filterNot { it.id == note.id }))
    }

    fun export(onReady: (File) -> Unit) {
        val snapshot = project ?: return
        if (busy) return
        busy = true
        enqueue {
            val file = withContext(Dispatchers.IO) { store.export(snapshot) }
            busy = false
            onReady(file)
        }
    }

    fun savePacket(file: File, uri: Uri) {
        busy = true
        enqueue {
            withContext(Dispatchers.IO) {
                val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?: error("Could not open that destination.")
                output.use { out -> file.inputStream().use { it.copyTo(out) } }
            }
            busy = false
            message = "Packet saved. Give the ZIP to your coding agent."
        }
    }
}
