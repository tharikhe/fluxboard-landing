package com.example.service

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.data.api.DeepSeekApiService
import com.example.data.api.DeepSeekRepository
import com.example.data.local.AppDatabase
import com.example.data.local.KeyboardDao
import com.example.data.model.ClipboardHistory
import com.example.data.pref.KeyboardPreferences
import com.example.ui.keyboard.KeyboardScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.*
import java.util.UUID

class AIKeyboardService : InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    // Implementation of standard Compose Lifecycle elements inside Service context
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val viewModelStore: ViewModelStore = store
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var preferences: KeyboardPreferences
    lateinit var database: AppDatabase
    lateinit var databaseDao: KeyboardDao
    lateinit var apiService: DeepSeekApiService
    lateinit var repository: DeepSeekRepository

    private var composeView: ComposeView? = null
    private var clipboard: ClipboardManager? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        // Initialize dependencies
        preferences = KeyboardPreferences(applicationContext)
        database = AppDatabase.getDatabase(applicationContext)
        databaseDao = database.keyboardDao()
        apiService = DeepSeekApiService.create()
        repository = DeepSeekRepository(apiService, preferences)

        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        listenToClipboard()
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val view = ComposeView(this).apply {
            id = View.generateViewId()
        }

        // Attach owners to the window's DecorView so they are visible to the entire view tree
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        // Also set on the ComposeView itself
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)

        view.setContent {
            MyApplicationTheme(keyboardTheme = preferences.selectedTheme) {
                KeyboardScreen(
                    service = this@AIKeyboardService,
                    preferences = preferences,
                    repository = repository,
                    dbDao = databaseDao
                )
            }
        }

        composeView = view
        return view
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        // Set inputs metadata or update keyboard keys as needed
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        serviceScope.cancel()
    }

    // Input Operations Helper Métiers mapping directly to Android InputConnection
    fun triggerKeyClick(primaryCode: String) {
        val ic: InputConnection = currentInputConnection ?: return
        triggerHaptic()

        when (primaryCode) {
            "BACKSPACE" -> {
                // Delete one character before cursor
                ic.deleteSurroundingText(1, 0)
            }
            "SPACE" -> {
                ic.commitText(" ", 1)
            }
            "ENTER" -> {
                val editorInfo = currentInputEditorInfo
                if (editorInfo != null && (editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION) != 0) {
                    ic.performEditorAction(editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION)
                } else {
                    ic.commitText("\n", 1)
                }
            }
            else -> {
                ic.commitText(primaryCode, 1)
            }
        }
    }

    fun triggerHaptic() {
        if (!preferences.isHapticFeedbackEnabled) return
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        preferences.vibrationDurationMs.toLong(),
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(preferences.vibrationDurationMs.toLong())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getSelectedTextOrSentence(): String? {
        val ic = currentInputConnection ?: return null
        
        // Try getting select text
        val selected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ic.getSelectedText(0)?.toString()
        } else {
            null
        }
        
        if (!selected.isNullOrBlank()) {
            return selected
        }

        // Try getting sentence context before cursor
        val before = ic.getTextBeforeCursor(200, 0)?.toString() ?: ""
        if (before.isNotBlank()) {
            val lastSentence = before.split(".", "?", "!").lastOrNull()?.trim()
            if (!lastSentence.isNullOrBlank()) {
                return lastSentence
            }
            return before.trim()
        }

        return null
    }

    fun replaceText(newText: String) {
        val ic = currentInputConnection ?: return
        
        // Check if there's any actual selection
        val selected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ic.getSelectedText(0)?.toString()
        } else {
            null
        }
        
        if (!selected.isNullOrBlank()) {
            // There's an active text selection — commitText replaces it automatically
            ic.commitText(newText, 1)
        } else {
            // No active selection — try to replace the sentence before cursor
            val before = ic.getTextBeforeCursor(200, 0)?.toString() ?: ""
            if (before.isNotBlank()) {
                // Delete the text before cursor that was used as context
                ic.deleteSurroundingText(before.trim().length, 0)
            }
            ic.commitText(newText, 1)
        }
    }

    private fun listenToClipboard() {
        try {
            clipboard?.addPrimaryClipChangedListener {
                try {
                    val clipData = clipboard?.primaryClip ?: return@addPrimaryClipChangedListener
                    if (clipData.itemCount > 0) {
                        val text = clipData.getItemAt(0)?.text?.toString()
                        if (!text.isNullOrBlank() && !preferences.isIncognitoModeEnabled) {
                            serviceScope.launch(Dispatchers.IO) {
                                try {
                                    databaseDao.insertClipboardItem(
                                        ClipboardHistory(
                                            id = UUID.randomUUID().toString(),
                                            text = text,
                                            timestamp = System.currentTimeMillis()
                                        )
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    // Security exception when accessing clipboard background notifications in restricted sandboxes
                    e.printStackTrace()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openSettingsApp() {
        val intent = Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }
}
