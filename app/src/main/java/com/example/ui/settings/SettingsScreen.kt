package com.example.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.KeyboardDao
import com.example.data.model.AIHistory
import com.example.data.pref.KeyboardPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: KeyboardPreferences,
    dbDao: KeyboardDao
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Live Keyboard Activation Detection Checkers
    var isEnabled by remember { mutableStateOf(false) }
    var isSelected by remember { mutableStateOf(false) }

    // Read current settings
    var activeTheme by remember { mutableStateOf(preferences.selectedTheme) }
    var hapticEnabled by remember { mutableStateOf(preferences.isHapticFeedbackEnabled) }
    var vibrationDuration by remember { mutableStateOf(preferences.vibrationDurationMs.toFloat()) }
    var soundEnabled by remember { mutableStateOf(preferences.isSoundFeedbackEnabled) }
    var autoCapEnabled by remember { mutableStateOf(preferences.isAutoCapitalizationEnabled) }
    var incognitoEnabled by remember { mutableStateOf(preferences.isIncognitoModeEnabled) }
    var personalApiKey by remember { mutableStateOf(preferences.deepSeekApiKey) }
    var usePersonalKey by remember { mutableStateOf(preferences.usePersonalKey) }
    var apiProvider by remember { mutableStateOf(preferences.apiProvider) }
    var customBaseUrl by remember { mutableStateOf(preferences.customBaseUrl) }
    var customModel by remember { mutableStateOf(preferences.customModel) }
    var isPremium by remember { mutableStateOf(preferences.isPremiumUser) }
    var trialsLeft by remember { mutableStateOf(preferences.trialsLeft) }

    // History and Clipboard
    val aiHistory by dbDao.getAIHistory().collectAsState(initial = emptyList())

    // Update activation state on component reload or resume
    fun reloadActivationState() {
        val packageName = context.packageName
        val serviceName = "$packageName/com.example.service.AIKeyboardService"

        // 1. Check if enabled
        val enabledImes = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_INPUT_METHODS
        ) ?: ""
        isEnabled = enabledImes.contains(packageName)

        // 2. Check if selected
        val defaultIme = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: ""
        isSelected = defaultIme.contains(packageName)
    }

    // Run startup checker updates
    LaunchedEffect(Unit) {
        reloadActivationState()
    }

    // Refresh layout state
    DisposableEffect(Unit) {
        onDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Deep Keyboard Settings",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // STEP 1 & 2: ONBOARDING ASSISTANT CARDS
            item {
                Text(
                    "Service Status",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isEnabled && isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (isEnabled && isSelected) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isEnabled && isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isEnabled && isSelected) "Deep Keyboard is Active!" else "Setup Required!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isEnabled && isSelected) {
                                        "Fully active writing companion. Open any app to start typing with DeepSeek AI."
                                    } else {
                                        "Complete these two clicks to enable writing predictions and smart rephrasers."
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!isEnabled || !isSelected) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (!isEnabled) {
                                    Button(
                                        onClick = {
                                            try {
                                                context.startActivity(Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS))
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("1. Enable Keyboard", fontSize = 12.sp)
                                    }
                                } else if (!isSelected) {
                                    Button(
                                        onClick = {
                                            val im = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                                            im?.showInputMethodPicker()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("2. Switch/Select IME", fontSize = 12.sp)
                                    }
                                }

                                Button(
                                    onClick = { reloadActivationState() },
                                    colors = ButtonDefaults.filledTonalButtonColors(),
                                    modifier = Modifier.width(80.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Check", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // PREMIUM MEMBERSHIP DEMO CARD
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF6200EE),
                                        Color(0xFFBB86FC)
                                    )
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Deep Keyboard Premium",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Badge(containerColor = if (isPremium) Color(0xFF4CAF50) else Color(0xFFFF9800)) {
                                    Text(
                                        text = if (isPremium) "ACTIVE" else "STANDARD TRIAL",
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isPremium) {
                                    "Unlimited writing translations, tone modifications, message expansions, and autocomplete capabilities."
                                } else {
                                    "Unlock unlimited DeepSeek API model requests, zero-latency rewriting filters, and priority performance optimization. ($trialsLeft free trial requests remaining)"
                                },
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            if (!isPremium) {
                                Button(
                                    onClick = {
                                        preferences.isPremiumUser = true
                                        isPremium = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Upgrade to Unlimited", color = Color(0xFF6200EE), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        preferences.isPremiumUser = false
                                        isPremium = false
                                        preferences.freeTrialCount = 0
                                        trialsLeft = preferences.trialsLeft
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f)),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Demote Account (Simulator)", color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // VISUAL THEMES SELECTOR SECTION
            item {
                Text(
                    "Visual Configuration",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Keyboard Active Style Theme", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ThemeSelectionChip(
                                label = "Light",
                                isSelected = activeTheme == KeyboardPreferences.KeyboardTheme.LIGHT,
                                primaryColor = Color(0xFFFFFFFF),
                                accentColor = Color(0xFF1B73E8),
                                onClick = {
                                    preferences.selectedTheme = KeyboardPreferences.KeyboardTheme.LIGHT
                                    activeTheme = KeyboardPreferences.KeyboardTheme.LIGHT
                                }
                            )
                            ThemeSelectionChip(
                                label = "Dark",
                                isSelected = activeTheme == KeyboardPreferences.KeyboardTheme.DARK,
                                primaryColor = Color(0xFF16171A),
                                accentColor = Color(0xFF1B73E8),
                                onClick = {
                                    preferences.selectedTheme = KeyboardPreferences.KeyboardTheme.DARK
                                    activeTheme = KeyboardPreferences.KeyboardTheme.DARK
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ThemeSelectionChip(
                                label = "AMOLED Black",
                                isSelected = activeTheme == KeyboardPreferences.KeyboardTheme.AMOLED,
                                primaryColor = Color(0xFF000000),
                                accentColor = Color(0xFF4285F4),
                                onClick = {
                                    preferences.selectedTheme = KeyboardPreferences.KeyboardTheme.AMOLED
                                    activeTheme = KeyboardPreferences.KeyboardTheme.AMOLED
                                }
                            )
                            ThemeSelectionChip(
                                label = "Cosmic Indigo",
                                isSelected = activeTheme == KeyboardPreferences.KeyboardTheme.COSMIC_INDIGO,
                                primaryColor = Color(0xFF100E1D),
                                accentColor = Color(0xFFFF2A5F),
                                onClick = {
                                    preferences.selectedTheme = KeyboardPreferences.KeyboardTheme.COSMIC_INDIGO
                                    activeTheme = KeyboardPreferences.KeyboardTheme.COSMIC_INDIGO
                                }
                            )
                        }
                    }
                }
            }

            // LLM SERVICE CREDENTIALS & ENDPOINT CONFIGURATION SECTION
            item {
                Text(
                    "AI Model & API Configuration",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Use Custom API Credentials", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Leverage your own custom LLMs, endpoints, and API limits", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = usePersonalKey,
                                onCheckedChange = {
                                    preferences.usePersonalKey = it
                                    usePersonalKey = it
                                }
                            )
                        }

                        AnimatedVisibility(visible = usePersonalKey) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                
                                Text(
                                    "Select API Provider Engine",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    ElevatedFilterChip(
                                        selected = apiProvider == KeyboardPreferences.ApiProvider.NVIDIA_MISTRAL,
                                        onClick = {
                                            preferences.apiProvider = KeyboardPreferences.ApiProvider.NVIDIA_MISTRAL
                                            apiProvider = KeyboardPreferences.ApiProvider.NVIDIA_MISTRAL
                                            preferences.customBaseUrl = "https://integrate.api.nvidia.com/v1/"
                                            customBaseUrl = "https://integrate.api.nvidia.com/v1/"
                                            preferences.customModel = "mistralai/mistral-medium-3.5-128b"
                                            customModel = "mistralai/mistral-medium-3.5-128b"
                                            preferences.deepSeekApiKey = "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"
                                            personalApiKey = "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"
                                        },
                                        label = { Text("Nvidia Mistral", fontSize = 12.sp) }
                                    )
                                    ElevatedFilterChip(
                                        selected = apiProvider == KeyboardPreferences.ApiProvider.DEEPSEEK,
                                        onClick = {
                                            preferences.apiProvider = KeyboardPreferences.ApiProvider.DEEPSEEK
                                            apiProvider = KeyboardPreferences.ApiProvider.DEEPSEEK
                                            preferences.customBaseUrl = "https://api.deepseek.com/"
                                            customBaseUrl = "https://api.deepseek.com/"
                                            preferences.customModel = "deepseek-chat"
                                            customModel = "deepseek-chat"
                                        },
                                        label = { Text("DeepSeek API", fontSize = 12.sp) }
                                    )
                                    ElevatedFilterChip(
                                        selected = apiProvider == KeyboardPreferences.ApiProvider.OPENAI_COMPATIBLE,
                                        onClick = {
                                            preferences.apiProvider = KeyboardPreferences.ApiProvider.OPENAI_COMPATIBLE
                                            apiProvider = KeyboardPreferences.ApiProvider.OPENAI_COMPATIBLE
                                            if (customBaseUrl == "https://api.deepseek.com/" || customBaseUrl == "https://integrate.api.nvidia.com/v1/") {
                                                preferences.customBaseUrl = "https://api.openai.com/v1/"
                                                customBaseUrl = "https://api.openai.com/v1/"
                                            }
                                            if (customModel == "deepseek-chat" || customModel == "mistralai/mistral-medium-3.5-128b") {
                                                preferences.customModel = "gpt-4o-mini"
                                                customModel = "gpt-4o-mini"
                                            }
                                        },
                                        label = { Text("Custom LLM (OpenAI)", fontSize = 12.sp) }
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = customBaseUrl,
                                    onValueChange = {
                                        preferences.customBaseUrl = it
                                        customBaseUrl = it
                                    },
                                    label = { Text("API Base URL (Endpoint)", fontSize = 12.sp) },
                                    placeholder = { Text("e.g. https://api.openai.com/v1/") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Ensure this points to the root URL (including trailing slash /). E.g. locally hosted Ollama: http://10.0.2.2:11434/v1/ or LM Studio.",
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = customModel,
                                    onValueChange = {
                                        preferences.customModel = it
                                        customModel = it
                                    },
                                    label = { Text("Target Model Name ID", fontSize = 12.sp) },
                                    placeholder = { Text("e.g. deepseek-chat, gpt-4o, llama3") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = personalApiKey,
                                    onValueChange = {
                                        preferences.deepSeekApiKey = it
                                        personalApiKey = it
                                    },
                                    label = { Text("API Bearer Key / Token", fontSize = 12.sp) },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "All tokens and configuration endpoints are securely persisted locally on this Android sandbox.",
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // SOUNDS AND HAPTICS
            item {
                Text(
                    "Typing Preferences",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Key Vibration (Haptics)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Switch(
                                checked = hapticEnabled,
                                onCheckedChange = {
                                    preferences.isHapticFeedbackEnabled = it
                                    hapticEnabled = it
                                }
                            )
                        }

                        if (hapticEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Vibrate intensity:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(10.dp))
                                Slider(
                                    value = vibrationDuration,
                                    onValueChange = {
                                        preferences.vibrationDurationMs = it.toInt()
                                        vibrationDuration = it
                                    },
                                    valueRange = 5f..80f,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${vibrationDuration.toInt()}ms", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("Key Sound Feedback", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Clicking audio on typing symbols", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = soundEnabled,
                                onCheckedChange = {
                                    preferences.isSoundFeedbackEnabled = it
                                    soundEnabled = it
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("Incognito Privacy Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Never save clipboard items to local logs database", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = incognitoEnabled,
                                onCheckedChange = {
                                    preferences.isIncognitoModeEnabled = it
                                    incognitoEnabled = it
                                }
                            )
                        }
                    }
                }
            }

            // AI REWRITER HISTORY DUMP
            item {
                Text(
                    "Writing Archives",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (aiHistory.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No rephrasing history yet. Enable keyboard to begin.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                dbDao.clearAIHistory()
                            }
                        }) {
                            Text("Clear all archives", color = Color.Red, fontSize = 12.sp)
                        }
                    }
                }

                items(aiHistory) { record ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(
                                        text = "${record.featureType} ${record.extraInfo}",
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            dbDao.deleteAIHistoryById(record.id)
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Original:\n${record.originalText}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "AI Rephrased:\n${record.modifiedText}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeSelectionChip(
    label: String,
    isSelected: Boolean,
    primaryColor: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(primaryColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accentColor else Color.Gray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .border(0.5.dp, Color.LightGray, RoundedCornerShape(3.dp))
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = if (primaryColor == Color.White) Color.Black else Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
