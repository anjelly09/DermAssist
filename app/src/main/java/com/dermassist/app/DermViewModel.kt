package com.dermassist.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, CONSENT, PHOTO, SYMPTOMS, RESULT, JOURNAL, ABOUT }

class DermViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val photos = PhotoStore(application)
    private val journal = JournalStore(application)
    private val engine = ScreeningEngine(application)
    var screening by mutableStateOf(screeningFromJson(saved.get<String>("screening")?.let { org.json.JSONObject(it) })); private set
    var screen by mutableStateOf(Screen.valueOf(saved["screen"] ?: "HOME")); private set
    var photo by mutableStateOf(saved.get<String>("photo")?.takeIf { File(it).exists() }); private set
    var symptoms by mutableStateOf(Symptoms(saved["location"] ?: "", saved["duration"] ?: "", saved["itching"] ?: "", saved["pain"] ?: "", saved["spread"] ?: "")); private set
    var history by mutableStateOf<List<Assessment>>(emptyList()); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var currentId: String = saved["currentId"] ?: UUID.randomUUID().toString(); private set
    var currentDate: Long = saved["currentDate"] ?: System.currentTimeMillis(); private set
    val current get() = Assessment(currentId, currentDate, symptoms, screening)
    val isSaved get() = history.any { it.id == currentId }
    var pendingCamera: String?
        get() = saved["pendingCamera"]
        set(value) { saved["pendingCamera"] = value }

    init {
        if (photo == null && screen in setOf(Screen.SYMPTOMS, Screen.RESULT)) navigate(Screen.PHOTO)
        photos.clearExcept(setOfNotNull(photo, pendingCamera))
        viewModelScope.launch { history = withContext(Dispatchers.IO) { journal.read() } }
    }
    fun navigate(destination: Screen) { screen = destination; saved["screen"] = destination.name; error = null }
    fun start() {
        photos.delete(photo); photo = null; saved["photo"] = null
        update(Symptoms())
        screening = ScreeningResult(); saved["screening"] = null
        currentId = UUID.randomUUID().toString(); saved["currentId"] = currentId
        currentDate = System.currentTimeMillis(); saved["currentDate"] = currentDate
        navigate(Screen.CONSENT)
    }
    fun finish() {
        photos.delete(photo); photo = null; saved["photo"] = null
        cameraCancelled(); update(Symptoms()); screening = ScreeningResult(); saved["screening"] = null; navigate(Screen.HOME)
    }
    fun back() {
        navigate(when (screen) {
            Screen.CONSENT, Screen.JOURNAL, Screen.ABOUT -> Screen.HOME
            Screen.PHOTO -> Screen.CONSENT
            Screen.SYMPTOMS -> Screen.PHOTO
            Screen.RESULT -> Screen.SYMPTOMS
            else -> Screen.HOME
        })
    }
    fun update(value: Symptoms) {
        symptoms = value
        saved["location"] = value.location; saved["duration"] = value.duration
        saved["itching"] = value.itching; saved["pain"] = value.pain; saved["spread"] = value.spread
    }
    fun showResult() {
        val path = photo ?: return
        if (!symptoms.complete || busy) return
        busy = true
        viewModelScope.launch {
            try {
                screening = withContext(Dispatchers.Default) { engine.assess(path) }
                saved["screening"] = screeningToJson(screening).toString()
                if (isSaved) { currentId = UUID.randomUUID().toString(); saved["currentId"] = currentId }
                currentDate = System.currentTimeMillis(); saved["currentDate"] = currentDate
                navigate(Screen.RESULT)
            } finally { busy = false }
        }
    }
    fun newCapture(): File = photos.newCapture().also { pendingCamera = it.absolutePath }
    fun cameraCancelled() { photos.delete(pendingCamera); pendingCamera = null }
    fun notifyError(message: String) { error = message }
    fun importPhoto(uri: Uri) {
        busy = true; error = null
        viewModelScope.launch {
            try {
                val path = withContext(Dispatchers.IO) { photos.prepare(uri) }
                photos.delete(photo); photo = path; saved["photo"] = path
            } catch (e: Exception) {
                error = e.message ?: "This photo could not be opened. Choose another photo."
            } finally {
                cameraCancelled(); busy = false
            }
        }
    }
    fun save() = changeJournal { entries -> if (entries.any { it.id == currentId }) entries else listOf(current) + entries }
    fun delete(id: String) = changeJournal { it.filterNot { entry -> entry.id == id } }
    fun deleteAll() = changeJournal { emptyList() }
    private fun changeJournal(transform: (List<Assessment>) -> List<Assessment>) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val next = transform(history)
                withContext(Dispatchers.IO) { journal.write(next) }
                history = next
            } catch (e: Exception) { error = e.message ?: "The journal could not be updated. Try again." }
            finally { busy = false }
        }
    }
}
