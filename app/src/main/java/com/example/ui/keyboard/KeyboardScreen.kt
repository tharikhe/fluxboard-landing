package com.example.ui.keyboard

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.DeepSeekRepository
import com.example.data.local.KeyboardDao
import com.example.data.model.AIHistory
import com.example.data.model.ClipboardHistory
import com.example.data.pref.KeyboardPreferences
import com.example.service.AIKeyboardService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class KeyboardMode {
    QWERTY, SYMBOLS, ALT_SYMBOLS, EMOJI
}

enum class KeyboardToolbarMode {
    NORMAL, REWRITE, AI_DRAFT, CLIPBOARD, HISTORY
}

@Composable
fun KeyboardScreen(
    service: AIKeyboardService,
    preferences: KeyboardPreferences,
    repository: DeepSeekRepository,
    dbDao: KeyboardDao
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Keyboard Visual & Input States
    var currentMode by remember { mutableStateOf(KeyboardMode.QWERTY) }
    var isShiftActive by remember { mutableStateOf(false) }
    var isCapsLock by remember { mutableStateOf(false) }
    var toolbarMode by remember { mutableStateOf(KeyboardToolbarMode.NORMAL) }

    // Color Theme configuration based on preferences
    val theme = preferences.selectedTheme
    val colors = remember(theme) { getThemeColors(theme) }

    // AI Processing States
    var selectedTextForAI by remember { mutableStateOf("") }
    var aiInstruction by remember { mutableStateOf("") }
    var isAiLoading by remember { mutableStateOf(false) }
    var aiResultText by remember { mutableStateOf("") }
    var apiErrorMessage by remember { mutableStateOf("") }

    // Database Streams
    val clipboardHistory by dbDao.getClipboardHistory().collectAsState(initial = emptyList())
    val aiHistoryList by dbDao.getAIHistory().collectAsState(initial = emptyList())

    // Fetch surrounding text if keyboard expanded
    LaunchedEffect(toolbarMode) {
        if (toolbarMode == KeyboardToolbarMode.REWRITE || toolbarMode == KeyboardToolbarMode.AI_DRAFT) {
            val text = service.getSelectedTextOrSentence()
            selectedTextForAI = text ?: ""
            aiResultText = ""
            apiErrorMessage = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(bottom = 4.dp)
    ) {
        // 1. DYNAMIC TOOLBAR AREA (AI writing capabilities & clipboard)
        if (currentMode != KeyboardMode.EMOJI) {
            AIKeyboardToolbar(
                toolbarMode = toolbarMode,
                onChangeMode = { toolbarMode = it },
                selectedText = selectedTextForAI,
                onSelectedTextChange = { selectedTextForAI = it },
                aiResult = aiResultText,
                isLoading = isAiLoading,
                colors = colors,
                errorMessage = apiErrorMessage,
                clipboardItems = clipboardHistory,
                aiHistoryList = aiHistoryList,
                onOpenSettings = { service.openSettingsApp() },
                onExecuteAction = { type, contextStr ->
                    coroutineScope.launch {
                        isAiLoading = true
                        apiErrorMessage = ""
                        val result = repository.executeAiAction(type, selectedTextForAI, contextStr)
                        isAiLoading = false
                        result.fold(
                            onSuccess = { rewrittenText ->
                                aiResultText = rewrittenText
                                // Log history to DB
                                withContext(Dispatchers.IO) {
                                    dbDao.insertAIHistory(
                                        AIHistory(
                                            originalText = selectedTextForAI,
                                            modifiedText = rewrittenText,
                                            featureType = type,
                                            extraInfo = contextStr
                                        )
                                    )
                                }
                            },
                            onFailure = { error ->
                                apiErrorMessage = error.message ?: "An unknown AI error occurred."
                            }
                        )
                    }
                },
                onInsertText = { textToInsert ->
                    service.triggerHaptic()
                    service.replaceText(textToInsert)
                    toolbarMode = KeyboardToolbarMode.NORMAL
                },
                onDeleteClip = { clipId ->
                    coroutineScope.launch(Dispatchers.IO) {
                        dbDao.deleteClipboardItemById(clipId)
                    }
                },
                onClearHistory = {
                    coroutineScope.launch(Dispatchers.IO) {
                        dbDao.clearAIHistory()
                    }
                }
            )

            HorizontalDivider(color = colors.dividerColor, thickness = 0.3.dp)
        }

        // 2. SUGGESTION STRIP (NORMAL MODE ONLY)
        AnimatedVisibility(
            visible = toolbarMode == KeyboardToolbarMode.NORMAL && currentMode != KeyboardMode.EMOJI,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            SuggestionStrip(
                suggestions = service.suggestions,
                colors = colors,
                isTyping = service.currentWord.isNotEmpty(),
                onSuggestionClick = { word ->
                    service.selectSuggestion(word)
                }
            )
        }

        // 3. MAIN INTERACTIVE KEYBOARD SYSTEM
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent
        ) {
            when (currentMode) {
                KeyboardMode.QWERTY -> {
                    KeyboardKeysGrid(
                        keys = KeyboardLayout.qwertyKeys,
                        isShift = isShiftActive,
                        isCaps = isCapsLock,
                        colors = colors,
                        onKeyClick = { key ->
                            if (key == "EMOJI") {
                                service.triggerHaptic()
                                currentMode = KeyboardMode.EMOJI
                            } else {
                                handleKeyAction(
                                    key = key,
                                    service = service,
                                    onShift = {
                                        if (isShiftActive) {
                                            if (isCapsLock) {
                                                isCapsLock = false
                                                isShiftActive = false
                                            } else {
                                                isCapsLock = true
                                            }
                                        } else {
                                            isShiftActive = true
                                        }
                                    },
                                    onSymToggle = { currentMode = KeyboardMode.SYMBOLS }
                                )
                                // Auto reset shift if dynamic
                                if (isShiftActive && !isCapsLock && key != "SHIFT") {
                                    isShiftActive = false
                                }
                            }
                        },
                        onKeyLongClick = { key ->
                            if (key == "SPACE") {
                                service.showKeyboardSwitcher()
                            } else {
                                val number = getLongPressChar(key)
                                if (number != null) {
                                    service.triggerKeyClick(number)
                                }
                            }
                        }
                    )
                }
                KeyboardMode.SYMBOLS -> {
                    KeyboardKeysGrid(
                        keys = KeyboardLayout.symbolKeys,
                        isShift = false,
                        isCaps = false,
                        colors = colors,
                        onKeyClick = { key ->
                            if (key == "EMOJI") {
                                service.triggerHaptic()
                                currentMode = KeyboardMode.EMOJI
                            } else {
                                handleKeyAction(
                                    key = key,
                                    service = service,
                                    onShift = {},
                                    onSymToggle = {
                                        if (key == "ALT_SYM") {
                                            currentMode = KeyboardMode.ALT_SYMBOLS
                                        } else {
                                            currentMode = KeyboardMode.QWERTY
                                        }
                                    }
                                )
                            }
                        },
                        onKeyLongClick = { key ->
                            if (key == "SPACE") {
                                service.showKeyboardSwitcher()
                            }
                        }
                    )
                }
                KeyboardMode.ALT_SYMBOLS -> {
                    KeyboardKeysGrid(
                        keys = KeyboardLayout.altSymbolKeys,
                        isShift = false,
                        isCaps = false,
                        colors = colors,
                        onKeyClick = { key ->
                            if (key == "EMOJI") {
                                service.triggerHaptic()
                                currentMode = KeyboardMode.EMOJI
                            } else {
                                handleKeyAction(
                                    key = key,
                                    service = service,
                                    onShift = {},
                                    onSymToggle = {
                                        if (key == "?123") {
                                            currentMode = KeyboardMode.SYMBOLS
                                        } else {
                                            currentMode = KeyboardMode.QWERTY
                                        }
                                    }
                                )
                            }
                        },
                        onKeyLongClick = { key ->
                            if (key == "SPACE") {
                                service.showKeyboardSwitcher()
                            }
                        }
                    )
                }
                KeyboardMode.EMOJI -> {
                    EmojiGrid(
                        colors = colors,
                        onEmojiClick = { emoji ->
                            service.triggerKeyClick(emoji)
                        },
                        onBackClick = { currentMode = KeyboardMode.QWERTY }
                    )
                }
            }
        }
    }
}

// Handler mapping layout symbols to input callbacks
private fun handleKeyAction(
    key: String,
    service: AIKeyboardService,
    onShift: () -> Unit,
    onSymToggle: () -> Unit
) {
    when (key) {
        "SHIFT" -> onShift()
        "BACKSPACE" -> service.triggerKeyClick("BACKSPACE")
        "SPACE" -> service.triggerKeyClick("SPACE")
        "ENTER" -> service.triggerKeyClick("ENTER")
        "?123", "ABC", "ALT_SYM" -> onSymToggle()
        "EMOJI" -> {
            service.triggerKeyClick("") // vibration feedback
            onSymToggle() // toggle
        }
        else -> service.triggerKeyClick(key)
    }
}


// --- THEME COLOR MAPPINGS ---
data class KeyboardThemeColors(
    val background: Color,
    val keyBg: Color,
    val specialKeyBg: Color,
    val accentBg: Color,
    val accentGlow: Color,
    val keyTextColor: Color,
    val accentTextColor: Color,
    val dividerColor: Color,
    val activePillBg: Color,
    val subtleTextColor: Color,
    val toolbarIconTint: Color
)

private fun getThemeColors(theme: KeyboardPreferences.KeyboardTheme): KeyboardThemeColors {
    return when (theme) {
        KeyboardPreferences.KeyboardTheme.LIGHT -> KeyboardThemeColors(
            background = Color(0xFFD9DBDE),
            keyBg = Color(0xFFF7F8FA),
            specialKeyBg = Color(0xFFB8BCC2),
            accentBg = Color(0xFF4285F4),
            accentGlow = Color(0x334285F4),
            keyTextColor = Color(0xFF202124),
            accentTextColor = Color(0xFFFFFFFF),
            dividerColor = Color(0xFFC4C7CC),
            activePillBg = Color(0xFFD3E3FD),
            subtleTextColor = Color(0xFF5F6368),
            toolbarIconTint = Color(0xFF444746)
        )
        KeyboardPreferences.KeyboardTheme.DARK -> KeyboardThemeColors(
            background = Color(0xFF2B2F33),
            keyBg = Color(0xFF494D52),
            specialKeyBg = Color(0xFF3A3E42),
            accentBg = Color(0xFF8AB4F8),
            accentGlow = Color(0x338AB4F8),
            keyTextColor = Color(0xFFE8EAED),
            accentTextColor = Color(0xFF202124),
            dividerColor = Color(0xFF3C4043),
            activePillBg = Color(0xFF394457),
            subtleTextColor = Color(0xFF9AA0A6),
            toolbarIconTint = Color(0xFFC4C7C5)
        )
        KeyboardPreferences.KeyboardTheme.AMOLED -> KeyboardThemeColors(
            background = Color(0xFF000000),
            keyBg = Color(0xFF1A1A1A),
            specialKeyBg = Color(0xFF0F0F0F),
            accentBg = Color(0xFF8AB4F8),
            accentGlow = Color(0x338AB4F8),
            keyTextColor = Color(0xFFFFFFFF),
            accentTextColor = Color(0xFF000000),
            dividerColor = Color(0xFF1A1A1A),
            activePillBg = Color(0xFF1C2D4D),
            subtleTextColor = Color(0xFF8E8E8E),
            toolbarIconTint = Color(0xFFAAAAAA)
        )
        KeyboardPreferences.KeyboardTheme.COSMIC_INDIGO -> KeyboardThemeColors(
            background = Color(0xFF0F0D1B),
            keyBg = Color(0xFF1C1835),
            specialKeyBg = Color(0xFF14102A),
            accentBg = Color(0xFFBB86FC),
            accentGlow = Color(0x33BB86FC),
            keyTextColor = Color(0xFFE2E2F2),
            accentTextColor = Color(0xFF0F0D1B),
            dividerColor = Color(0xFF252040),
            activePillBg = Color(0xFF2D1F4E),
            subtleTextColor = Color(0xFF8A85A8),
            toolbarIconTint = Color(0xFFA09CC0)
        )
    }
}


// --- AI TOOLBAR (Gboard-Inspired) ---
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AIKeyboardToolbar(
    toolbarMode: KeyboardToolbarMode,
    onChangeMode: (KeyboardToolbarMode) -> Unit,
    selectedText: String,
    onSelectedTextChange: (String) -> Unit,
    aiResult: String,
    isLoading: Boolean,
    colors: KeyboardThemeColors,
    errorMessage: String,
    clipboardItems: List<ClipboardHistory>,
    aiHistoryList: List<AIHistory>,
    onOpenSettings: () -> Unit,
    onExecuteAction: (String, String) -> Unit,
    onInsertText: (String) -> Unit,
    onDeleteClip: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // Compact toolbar row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(colors.background)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // AI toggle icon
            IconButton(
                onClick = {
                    val next = if (toolbarMode == KeyboardToolbarMode.NORMAL) KeyboardToolbarMode.REWRITE else KeyboardToolbarMode.NORMAL
                    onChangeMode(next)
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "AI Assistant",
                    tint = if (toolbarMode != KeyboardToolbarMode.NORMAL) colors.accentBg else colors.toolbarIconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Tab pills
            LazyRow(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item { ToolbarTab("Suggest", toolbarMode == KeyboardToolbarMode.NORMAL, colors) { onChangeMode(KeyboardToolbarMode.NORMAL) } }
                item { ToolbarTab("Rewrite", toolbarMode == KeyboardToolbarMode.REWRITE, colors) { onChangeMode(KeyboardToolbarMode.REWRITE) } }
                item { ToolbarTab("Compose", toolbarMode == KeyboardToolbarMode.AI_DRAFT, colors) { onChangeMode(KeyboardToolbarMode.AI_DRAFT) } }
                item { ToolbarTab("Clipboard", toolbarMode == KeyboardToolbarMode.CLIPBOARD, colors) { onChangeMode(KeyboardToolbarMode.CLIPBOARD) } }
                item { ToolbarTab("History", toolbarMode == KeyboardToolbarMode.HISTORY, colors) { onChangeMode(KeyboardToolbarMode.HISTORY) } }
            }

            // Settings
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = colors.toolbarIconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Expanded panels
        AnimatedContent(
            targetState = toolbarMode,
            transitionSpec = {
                fadeIn(tween(150)) togetherWith fadeOut(tween(100))
            }
        ) { mode ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 0.dp, max = 160.dp)
            ) {
                when (mode) {
                    KeyboardToolbarMode.NORMAL -> {
                        // Empty in normal mode - just shows suggestion strip below
                    }

                    KeyboardToolbarMode.REWRITE -> {
                        RewritePanel(
                            selectedText = selectedText,
                            onTextChange = onSelectedTextChange,
                            aiResult = aiResult,
                            isLoading = isLoading,
                            colors = colors,
                            errorMessage = errorMessage,
                            onExecute = { tone -> onExecuteAction("REWRITE", tone) },
                            onGrammarFix = { onExecuteAction("GRAMMAR", "") },
                            onSummarise = { onExecuteAction("SUMMARIZE", "") },
                            onInsert = onInsertText
                        )
                    }

                    KeyboardToolbarMode.AI_DRAFT -> {
                        SmartComposerPanel(
                            promptText = selectedText,
                            onPromptChange = onSelectedTextChange,
                            aiResult = aiResult,
                            isLoading = isLoading,
                            colors = colors,
                            errorMessage = errorMessage,
                            onExecute = { kind -> onExecuteAction("GENERATE", kind) },
                            onInsert = onInsertText
                        )
                    }

                    KeyboardToolbarMode.CLIPBOARD -> {
                        ClipboardPanel(
                            items = clipboardItems,
                            colors = colors,
                            onItemClick = { text -> onInsertText(text) },
                            onDelete = onDeleteClip
                        )
                    }

                    KeyboardToolbarMode.HISTORY -> {
                        HistoryPanel(
                            history = aiHistoryList,
                            colors = colors,
                            onItemClick = { text -> onInsertText(text) },
                            onClearAll = onClearHistory
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ToolbarTab(
    label: String,
    isActive: Boolean,
    colors: KeyboardThemeColors,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isActive) colors.activePillBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            color = if (isActive) colors.accentBg else colors.subtleTextColor,
            fontFamily = FontFamily.SansSerif
        )
    }
}


// --- REWRITE PANEL DETAILS ---
@Composable
fun RewritePanel(
    selectedText: String,
    onTextChange: (String) -> Unit,
    aiResult: String,
    isLoading: Boolean,
    colors: KeyboardThemeColors,
    errorMessage: String,
    onExecute: (String) -> Unit,
    onGrammarFix: () -> Unit,
    onSummarise: () -> Unit,
    onInsert: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        if (selectedText.isBlank() && !isLoading && aiResult.isBlank()) {
            Text(
                text = "⚠️ No selected text found. Type or select something in the app above first, then click a tone option.",
                color = colors.keyTextColor.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.padding(8.dp)
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.accentBg, strokeWidth = 2.dp)
            }
        } else if (errorMessage.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Request Failed:\n$errorMessage",
                    color = Color.Red,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else if (aiResult.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, colors.dividerColor, RoundedCornerShape(8.dp))
                    .background(colors.keyBg.copy(alpha = 0.2f))
                    .padding(8.dp)
            ) {
                Text(
                    text = aiResult,
                    color = colors.keyTextColor,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { onInsert(aiResult) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentBg),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Apply", fontSize = 11.sp, color = colors.accentTextColor)
                    }
                }
            }
        } else {
            // Options Row
            Text(
                text = "Target Sentiment rephrase:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = colors.keyTextColor.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Button(
                        onClick = onGrammarFix,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.keyTextColor),
                        border = BorderStroke(0.5.dp, colors.accentBg),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(10.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Fix Grammar", fontSize = 10.sp)
                        }
                    }
                }
                item { ToneButton("Professional 👔", colors) { onExecute("Professional") } }
                item { ToneButton("Casual 💬", colors) { onExecute("Casual") } }
                item { ToneButton("Corporate 🏢", colors) { onExecute("Corporate") } }
                item { ToneButton("Creative 🎨", colors) { onExecute("Creative") } }
                item { ToneButton("Summarise 🧾", colors) { onSummarise() } }
            }
        }
    }
}

@Composable
fun ToneButton(label: String, colors: KeyboardThemeColors, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.keyBg)
            .border(0.5.dp, colors.dividerColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text = label, fontSize = 11.sp, color = colors.keyTextColor, fontFamily = FontFamily.SansSerif)
    }
}


// --- SMART COMPOSER PANEL ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartComposerPanel(
    promptText: String,
    onPromptChange: (String) -> Unit,
    aiResult: String,
    isLoading: Boolean,
    colors: KeyboardThemeColors,
    errorMessage: String,
    onExecute: (String) -> Unit,
    onInsert: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accentBg, strokeWidth = 2.dp)
            }
        } else if (errorMessage.isNotBlank()) {
            Text(text = "Error: $errorMessage", color = Color.Red, fontSize = 11.sp, modifier = Modifier.padding(6.dp))
        } else if (aiResult.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, colors.dividerColor, RoundedCornerShape(8.dp))
                    .background(colors.keyBg.copy(alpha = 0.2f))
                    .padding(8.dp)
            ) {
                Text(text = aiResult, color = colors.keyTextColor, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = { onInsert(aiResult) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentBg),
                        modifier = Modifier.height(26.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text("Apply", fontSize = 10.sp, color = colors.accentTextColor)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = promptText,
                    onValueChange = onPromptChange,
                    placeholder = { Text("What should DeepSeek write?", fontSize = 11.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .height(45.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.keyBg,
                        unfocusedContainerColor = colors.keyBg,
                        focusedTextColor = colors.keyTextColor,
                        unfocusedTextColor = colors.keyTextColor,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = TextStyle(fontSize = 11.sp),
                    shape = RoundedCornerShape(8.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item { ToneButton("Email Reply ✉️", colors) { onExecute("Email Reply") } }
                item { ToneButton("LinkedIn Post 👔", colors) { onExecute("LinkedIn Post") } }
                item { ToneButton("Tweet/Caption 🐦", colors) { onExecute("Tweet Caption") } }
                item { ToneButton("Quick Reply 👍", colors) { onExecute("Quick Text Response") } }
            }
        }
    }
}


// --- SUGGESTION STRIP (Gboard Style) ---
@Composable
fun SuggestionStrip(
    suggestions: List<String>,
    colors: KeyboardThemeColors,
    isTyping: Boolean,
    onSuggestionClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(colors.background),
        verticalAlignment = Alignment.CenterVertically
    ) {
        suggestions.forEachIndexed { index, keyword ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSuggestionClick(keyword) },
                contentAlignment = Alignment.Center
            ) {
                val isAutocorrectTarget = isTyping && index == 1 && suggestions.size >= 2
                Text(
                    text = keyword.trim(),
                    fontSize = 14.sp,
                    fontWeight = if (isAutocorrectTarget) FontWeight.Bold else FontWeight.Normal,
                    color = if (isAutocorrectTarget) colors.accentBg else colors.keyTextColor,
                    maxLines = 1,
                    fontFamily = FontFamily.SansSerif
                )
            }
            if (index < suggestions.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(colors.dividerColor)
                )
            }
        }
    }
}


// --- CLIPBOARD PANEL ---
@Composable
fun ClipboardPanel(
    items: List<ClipboardHistory>,
    colors: KeyboardThemeColors,
    onItemClick: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Clipboard is empty. Copy text in any application to store snippets.", fontSize = 11.sp, color = colors.keyTextColor.copy(alpha = 0.5f))
        }
    } else {
        LazyRow(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(items) { item ->
                Column(
                    modifier = Modifier
                        .width(130.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.keyBg)
                        .border(1.dp, colors.dividerColor, RoundedCornerShape(8.dp))
                        .clickable { onItemClick(item.text) }
                        .padding(6.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = item.text, fontSize = 11.sp, color = colors.keyTextColor, maxLines = 2, modifier = Modifier.weight(1f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = { onDelete(item.id) },
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }
}


// --- HISTORY PANEL ---
@Composable
fun HistoryPanel(
    history: List<AIHistory>,
    colors: KeyboardThemeColors,
    onItemClick: (String) -> Unit,
    onClearAll: () -> Unit
) {
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "No rewrite logs available yet.", fontSize = 11.sp, color = colors.keyTextColor.copy(alpha = 0.5f))
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("AI Rewrites", fontSize = 10.sp, color = colors.keyTextColor.copy(alpha = 0.6f))
                Text("Clear All", fontSize = 10.sp, color = Color.Red, modifier = Modifier.clickable { onClearAll() })
            }
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(history) { record ->
                    Column(
                        modifier = Modifier
                            .width(140.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.keyBg)
                            .border(0.5.dp, colors.dividerColor, RoundedCornerShape(8.dp))
                            .clickable { onItemClick(record.modifiedText) }
                            .padding(6.dp)
                    ) {
                        Text(text = "${record.featureType}: ${record.extraInfo}", fontSize = 8.sp, color = colors.accentBg, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = record.modifiedText, fontSize = 11.sp, color = colors.keyTextColor, maxLines = 2)
                    }
                }
            }
        }
    }
}


// --- KEYBOARD KEYS ENGINE (Gboard Style) ---
@Composable
fun KeyboardKeysGrid(
    keys: List<List<String>>,
    isShift: Boolean,
    isCaps: Boolean,
    colors: KeyboardThemeColors,
    onKeyClick: (String) -> Unit,
    onKeyLongClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 2.dp)
    ) {
        keys.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { originalKey ->
                    val weight = when (originalKey) {
                        "SPACE" -> 5f
                        "SHIFT", "BACKSPACE" -> 1.4f
                        "?123", "ABC", "ALT_SYM" -> 1.3f
                        "ENTER" -> 1.4f
                        "EMOJI" -> 1.1f
                        else -> 1f
                    }

                    val displayText = remember(originalKey, isShift, isCaps) {
                        if (originalKey.length == 1) {
                            if (isShift || isCaps) originalKey.uppercase() else originalKey.lowercase()
                        } else {
                            originalKey
                        }
                    }

                    val isEnterKey = originalKey == "ENTER"
                    val isSpecialKey = originalKey in listOf("SHIFT", "BACKSPACE", "?123", "ABC", "ALT_SYM", "EMOJI")

                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .height(52.dp)
                    ) {
                        KeyboardKeyButton(
                            label = displayText,
                            originalKey = originalKey,
                            isSpecial = isSpecialKey,
                            isEnter = isEnterKey,
                            isActive = (originalKey == "SHIFT" && (isShift || isCaps)),
                            isCapsLock = (originalKey == "SHIFT" && isCaps),
                            colors = colors,
                            onClick = { onKeyClick(originalKey) },
                            onLongClick = { onKeyLongClick(originalKey) }
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeyboardKeyButton(
    label: String,
    originalKey: String,
    isSpecial: Boolean,
    isEnter: Boolean,
    isActive: Boolean,
    isCapsLock: Boolean,
    colors: KeyboardThemeColors,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressedState by interactionSource.collectIsPressedAsState()

    if (originalKey == "BACKSPACE") {
        LaunchedEffect(isPressedState) {
            if (isPressedState) {
                onClick()
                delay(400)
                while (true) {
                    onClick()
                    delay(60)
                }
            }
        }
    }

    val shape = RoundedCornerShape(8.dp)
    val background = when {
        isEnter -> if (isPressedState) colors.accentBg.copy(alpha = 0.85f) else colors.accentBg
        isActive -> colors.accentBg
        isSpecial -> if (isPressedState) colors.specialKeyBg.copy(alpha = 0.85f) else colors.specialKeyBg
        else -> if (isPressedState) colors.keyBg.copy(alpha = 0.85f) else colors.keyBg
    }

    val textColor = when {
        isEnter -> colors.accentTextColor
        isActive -> colors.accentTextColor
        else -> colors.keyTextColor
    }

    val longPressHint = remember(originalKey) { getLongPressChar(originalKey) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .shadow(0.5.dp, shape)
            .clip(shape)
            .background(background)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (originalKey != "BACKSPACE") {
                        onClick()
                    }
                },
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (longPressHint != null) {
            Text(
                text = longPressHint,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = colors.keyTextColor.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 5.dp)
            )
        }

        when (originalKey) {
            "BACKSPACE" -> Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Backspace",
                tint = textColor,
                modifier = Modifier.size(22.dp)
            )
            "SHIFT" -> Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = if (isCapsLock) "Caps Lock" else "Shift",
                tint = textColor,
                modifier = Modifier.size(24.dp)
            )
            "ENTER" -> Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Enter",
                tint = textColor,
                modifier = Modifier.size(20.dp)
            )
            "EMOJI" -> Icon(
                imageVector = Icons.Default.Face,
                contentDescription = "Emojis",
                tint = textColor,
                modifier = Modifier.size(22.dp)
            )
            "SPACE" -> Text(
                text = "English",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = colors.subtleTextColor,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif
            )
            else -> Text(
                text = label,
                fontSize = when {
                    label.length == 1 -> 22.sp
                    label.length <= 3 -> 14.sp
                    else -> 11.sp
                },
                fontWeight = if (label.length == 1) FontWeight.Normal else FontWeight.Medium,
                color = textColor,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

private fun getLongPressChar(key: String): String? {
    return when (key.lowercase()) {
        "q" -> "1"
        "w" -> "2"
        "e" -> "3"
        "r" -> "4"
        "t" -> "5"
        "y" -> "6"
        "u" -> "7"
        "i" -> "8"
        "o" -> "9"
        "p" -> "0"
        else -> null
    }
}


@Composable
fun EmojiGrid(
    colors: KeyboardThemeColors,
    onEmojiClick: (String) -> Unit,
    onBackClick: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Smileys") }
    val emojis = KeyboardLayout.emojiCategories[selectedCategory] ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(colors.background)
    ) {
        // Category bar & Back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(colors.background)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ABC back button
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.specialKeyBg)
                    .clickable(onClick = onBackClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ABC",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.keyTextColor,
                    fontFamily = FontFamily.SansSerif
                )
            }

            // Categories Row
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KeyboardLayout.emojiCategories.keys.forEach { category ->
                    val isSelected = selectedCategory == category
                    val icon = when (category) {
                        "Smileys" -> "😀"
                        "Gestures" -> "👍"
                        "Hearts" -> "❤️"
                        "Food" -> "🍔"
                        "Travel" -> "🚗"
                        "Objects" -> "💻"
                        else -> "😀"
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colors.accentBg.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { selectedCategory = category }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = icon, fontSize = 16.sp)
                                Text(
                                    text = category,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) colors.accentBg else colors.keyTextColor
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = colors.dividerColor, thickness = 0.5.dp)

        // Smooth scrolling grid
        val gridState = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            state = gridState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(4.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(emojis) { emoji ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEmojiClick(emoji) },
                      contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emoji,
                        fontSize = 24.sp
                    )
                }
            }
        }
    }
}
