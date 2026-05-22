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
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

    var currentWord by mutableStateOf("")
    val suggestions = mutableStateListOf<String>("I", "The", "Hello", "How", "What", "Good")

    private val nextWordPredictions = mapOf(
        "how" to listOf("are", "do", "about", "to", "is", "can", "much", "many"),
        "thank" to listOf("you", "for", "your", "s"),
        "thanks" to listOf("for", "a", "to", "anyway", "again"),
        "good" to listOf("morning", "afternoon", "evening", "night", "luck", "idea", "job", "to"),
        "are" to listOf("you", "they", "we", "there", "the", "not", "doing"),
        "what" to listOf("are", "is", "about", "do", "you", "if", "time", "did"),
        "i" to listOf("am", "have", "will", "was", "would", "want", "think", "need", "can", "know", "like", "feel", "love"),
        "happy" to listOf("birthday", "new", "anniversary", "to", "hour", "for"),
        "please" to listOf("let", "find", "see", "help", "do", "reply", "send", "call"),
        "would" to listOf("like", "be", "you", "have", "love", "prefer"),
        "let" to listOf("me", "us", "go", "know", "them"),
        "nice" to listOf("to", "meeting", "work", "day", "shot"),
        "look" to listOf("forward", "at", "for", "like", "up", "into"),
        "am" to listOf("writing", "interested", "happy", "sorry", "looking", "doing", "fine", "ready", "going", "sure"),
        "can" to listOf("you", "i", "we", "be", "help", "do", "get", "go", "hear"),
        "could" to listOf("you", "we", "i", "be", "please", "have"),
        "will" to listOf("be", "do", "go", "get", "help", "call", "send", "let", "meet"),
        "did" to listOf("you", "not", "it", "they", "we", "she", "he"),
        "do" to listOf("you", "not", "it", "we", "they", "have", "know", "want"),
        "does" to listOf("it", "not", "he", "she", "that", "this", "anyone"),
        "go" to listOf("to", "home", "out", "on", "through", "with", "there", "back"),
        "going" to listOf("to", "home", "on", "through", "out", "there", "well", "fine"),
        "want" to listOf("to", "you", "it", "more", "a", "some"),
        "like" to listOf("to", "it", "that", "this", "you", "the", "a"),
        "love" to listOf("you", "it", "to", "the", "this"),
        "had" to listOf("a", "been", "to", "no", "some", "enough"),
        "has" to listOf("been", "a", "to", "no", "some"),
        "have" to listOf("a", "been", "to", "no", "some", "any", "the", "received", "heard"),
        "is" to listOf("the", "a", "not", "it", "this", "that", "there", "good", "very", "he", "she", "done"),
        "was" to listOf("the", "a", "not", "it", "this", "that", "there", "good", "very", "he", "she", "done"),
        "hope" to listOf("you", "this", "all", "to"),
        "sorry" to listOf("for", "to", "about"),
        "about" to listOf("the", "this", "that", "it", "you", "to", "our", "your", "my"),
        "this" to listOf("is", "was", "email", "message", "one", "week", "year", "time"),
        "that" to listOf("is", "was", "would", "you", "it", "we", "they", "he", "she"),
        "at" to listOf("the", "home", "work", "school", "least", "once", "first"),
        "for" to listOf("the", "your", "our", "you", "me", "this", "that", "help"),
        "in" to listOf("the", "our", "your", "my", "this", "that", "touch", "front", "order"),
        "on" to listOf("the", "my", "your", "our", "this", "that", "time", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"),
        "with" to listOf("you", "me", "us", "him", "her", "them", "the", "our", "your", "my"),
        "by" to listOf("the", "our", "your", "my", "this", "that", "doing"),
        "from" to listOf("the", "our", "your", "my", "this", "that", "you", "me", "us"),
        "to" to listOf("the", "be", "do", "go", "get", "see", "meet", "hear", "help", "know", "my", "your", "our", "you", "me", "us")
    )

    private val commonWords = listOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "I",
        "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
        "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
        "when", "make", "can", "like", "time", "no", "just", "him", "know", "take",
        "people", "into", "year", "your", "good", "some", "could", "them", "see", "other",
        "than", "then", "now", "look", "only", "come", "its", "over", "think", "also",
        "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
        "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
        "are", "was", "were", "is", "been", "has", "had", "will", "would", "should",
        "could", "about", "above", "across", "action", "activity", "actually", "add", "address", "administration",
        "admit", "adult", "affect", "after", "again", "against", "age", "agency", "agent", "ago",
        "agree", "agreement", "ahead", "air", "all", "allow", "almost", "alone", "along", "already",
        "also", "although", "always", "American", "among", "amount", "analysis", "and", "animal", "another",
        "answer", "any", "anyone", "anything", "appear", "apply", "approach", "area", "argue", "arm",
        "around", "arrive", "art", "article", "artist", "as", "ask", "assume", "at", "attack",
        "attention", "attorney", "audience", "author", "authority", "available", "avoid", "away", "baby", "back",
        "bad", "bag", "ball", "bank", "bar", "base", "beautiful", "because", "become", "bed",
        "before", "begin", "behavior", "behind", "believe", "benefit", "best", "better", "between", "beyond",
        "big", "bill", "billion", "bit", "black", "blood", "blue", "board", "body", "book",
        "born", "both", "box", "boy", "break", "bring", "brother", "budget", "build", "building",
        "business", "but", "buy", "by", "call", "camera", "campaign", "can", "cancer", "candidate",
        "capital", "car", "card", "care", "career", "carry", "case", "catch", "cause", "cell",
        "center", "central", "century", "certain", "certainly", "chair", "challenge", "chance", "change", "character",
        "charge", "check", "child", "choice", "choose", "church", "citizen", "city", "civil", "claim",
        "class", "clear", "clearly", "close", "coach", "cold", "collection", "college", "color", "come",
        "commercial", "common", "community", "company", "compare", "computer", "concern", "condition", "conference", "Congress",
        "consider", "consumer", "contain", "continue", "control", "cost", "could", "country", "couple", "course",
        "court", "cover", "create", "crime", "cultural", "culture", "cup", "current", "customer", "cut",
        "dark", "data", "daughter", "day", "dead", "deal", "death", "debate", "decade", "decide",
        "decision", "deep", "defense", "degree", "Democrat", "democratic", "describe", "design", "despite", "detail",
        "determine", "develop", "development", "device", "difference", "different", "difficult", "dinner", "direction", "director",
        "discover", "discuss", "discussion", "disease", "do", "doctor", "dog", "door", "down", "draw",
        "dream", "drive", "drop", "drug", "during", "each", "early", "east", "easy", "eat",
        "economic", "economy", "edge", "education", "effect", "effort", "eight", "either", "election", "else",
        "employee", "end", "energy", "enjoy", "enough", "enter", "entire", "environment", "environmental", "especially",
        "establish", "even", "evening", "event", "ever", "every", "everybody", "everyone", "everything", "evidence",
        "exactly", "example", "executive", "exist", "expect", "experience", "expert", "explain", "eye", "face",
        "fact", "factor", "fail", "fall", "family", "far", "fast", "father", "fear", "federal",
        "feel", "feeling", "few", "field", "fight", "figure", "fill", "film", "final", "finally",
        "financial", "find", "fine", "finger", "finish", "fire", "firm", "first", "fish", "five",
        "floor", "fly", "focus", "follow", "food", "foot", "for", "force", "foreign", "forget",
        "form", "former", "forward", "four", "free", "friend", "from", "front", "full", "fund",
        "future", "game", "garden", "gas", "general", "generation", "gentleman", "get", "girl", "give",
        "glass", "go", "goal", "good", "government", "great", "green", "ground", "group", "grow",
        "growth", "guess", "gun", "guy", "hair", "half", "hand", "hang", "happen", "happy",
        "hard", "have", "he", "head", "health", "hear", "heart", "heat", "heavy", "help",
        "her", "here", "herself", "him", "himself", "his", "history", "hit", "hold", "hope",
        "hospital", "hot", "hotel", "hour", "house", "how", "however", "huge", "human", "hundred",
        "husband", "I", "idea", "identify", "if", "ignore", "image", "imagine", "impact", "important",
        "improve", "in", "include", "including", "increase", "indeed", "indicate", "individual", "industry", "information",
        "inside", "instead", "institution", "interest", "interesting", "international", "interview", "into", "investment", "involve",
        "issue", "it", "item", "its", "itself", "job", "join", "just", "keep", "key",
        "kid", "kill", "kind", "kitchen", "know", "knowledge", "land", "language", "large", "last",
        "late", "later", "laugh", "law", "lawyer", "lay", "lead", "leader", "learn", "least",
        "leave", "left", "leg", "legal", "less", "let", "letter", "level", "lie", "life",
        "light", "like", "likely", "line", "list", "listen", "little", "live", "local", "long",
        "look", "lose", "loss", "lot", "love", "low", "machine", "magazine", "main", "maintain",
        "major", "majority", "make", "male", "man", "manage", "management", "manager", "many", "market",
        "marriage", "material", "matter", "may", "maybe", "me", "mean", "measure", "media", "medical",
        "meet", "meeting", "member", "memory", "mention", "message", "method", "middle", "might", "military",
        "million", "mind", "minute", "miss", "mission", "model", "modern", "moment", "money", "month",
        "more", "morning", "most", "mother", "mouth", "move", "movement", "movie", "Mr", "Mrs",
        "much", "music", "must", "my", "myself", "name", "nation", "national", "natural", "nature",
        "near", "nearly", "necessary", "need", "network", "never", "new", "news", "next", "nice",
        "night", "nine", "no", "nobody", "nor", "north", "not", "note", "nothing", "notice",
        "now", "number", "occur", "of", "off", "offer", "office", "officer", "official", "often",
        "oh", "oil", "ok", "old", "on", "once", "one", "only", "onto", "open",
        "operation", "opinion", "opportunity", "option", "or", "order", "organization", "other", "others", "our",
        "out", "outside", "over", "own", "owner", "page", "pain", "paint", "paper", "parent",
        "part", "participant", "particular", "particularly", "partner", "party", "pass", "past", "patient", "pattern",
        "pay", "peace", "people", "per", "perform", "performance", "perhaps", "period", "person", "personal",
        "phone", "physical", "physician", "picture", "piece", "place", "plan", "plant", "play", "player",
        "PM", "point", "police", "policy", "political", "politics", "poor", "popular", "population", "position",
        "positive", "possible", "power", "practice", "prepare", "present", "president", "pressure", "pretty", "prevent",
        "price", "private", "probably", "problem", "process", "produce", "product", "production", "professional", "professor",
        "program", "project", "property", "protect", "prove", "provide", "public", "pull", "purpose", "push",
        "put", "quality", "question", "quickly", "quite", "race", "radio", "raise", "range", "rate",
        "rather", "reach", "read", "ready", "real", "reality", "realize", "really", "reason", "receive",
        "recent", "recently", "recognize", "record", "red", "reduce", "refer", "regard", "region", "relate",
        "relationship", "religious", "remain", "remember", "remove", "report", "represent", "Republican", "require", "research",
        "resource", "respond", "response", "responsibility", "rest", "result", "return", "reveal", "rich", "rid",
        "ride", "right", "rise", "risk", "road", "rock", "role", "room", "rule", "run",
        "safe", "same", "save", "say", "scene", "school", "science", "scientist", "score", "sea",
        "season", "seat", "second", "secret", "section", "sector", "security", "see", "seed", "seek",
        "seem", "segment", "seize", "select", "send", "sentence", "separate", "series", "serious", "serve",
        "service", "set", "seven", "several", "sex", "shake", "share", "she", "shoot", "short",
        "shot", "should", "shoulder", "show", "side", "sign", "significant", "silence", "similar", "simple",
        "simply", "since", "sing", "single", "sister", "sit", "site", "situation", "six", "size",
        "skill", "skin", "small", "smile", "so", "social", "society", "soldier", "some", "someone",
        "something", "sometimes", "son", "song", "soon", "sort", "sound", "source", "south", "southern",
        "space", "speak", "special", "specialist", "specific", "speech", "spend", "sport", "spring", "staff",
        "stage", "stand", "standard", "star", "start", "state", "statement", "station", "stay", "step",
        "still", "stock", "stop", "store", "story", "strategy", "street", "strength", "structure", "student",
        "study", "stuff", "style", "subject", "success", "successful", "such", "suddenly", "suffer", "suggest",
        "summer", "support", "sure", "surface", "system", "table", "take", "talk", "tall", "task",
        "tax", "teach", "teacher", "team", "technology", "television", "tell", "ten", "tend", "term",
        "test", "than", "thank", "that", "the", "their", "them", "themselves", "then", "theory",
        "there", "these", "they", "thing", "think", "third", "this", "those", "though", "thought",
        "thousand", "threat", "three", "through", "throw", "thus", "time", "to", "today", "together",
        "tonight", "too", "tool", "top", "total", "tough", "toward", "town", "trade", "traditional",
        "training", "travel", "treat", "treatment", "tree", "trial", "trip", "trouble", "true", "truth",
        "try", "turn", "TV", "two", "type", "under", "understand", "unit", "until", "up",
        "upon", "us", "use", "usually", "value", "various", "very", "victim", "view", "violence",
        "visit", "voice", "vote", "wait", "walk", "wall", "want", "war", "watch", "water",
        "way", "we", "weapon", "wear", "week", "weight", "well", "west", "western", "what",
        "whatever", "when", "where", "whether", "which", "while", "white", "who", "whole", "whom",
        "whose", "why", "wide", "wife", "will", "win", "wind", "window", "wish", "with",
        "within", "without", "woman", "wonder", "word", "work", "worker", "world", "worry", "would",
        "write", "writer", "wrong", "yard", "yeah", "year", "yes", "yet", "you", "young",
        "your", "yourself"
    )

    fun updateSuggestions() {
        suggestions.clear()
        
        val ic = currentInputConnection
        val beforeText = ic?.getTextBeforeCursor(60, 0)?.toString() ?: ""
        
        // Find the word immediately before the one being typed (if any)
        val words = beforeText.trim().split(Regex("\\s+"))
        
        // The last word before the cursor is the context. 
        // If currentWord is NOT empty, the context is the word *before* currentWord.
        // If currentWord IS empty, the context is the very last word of words.
        val contextWord = if (currentWord.isNotEmpty()) {
            if (words.size >= 2) words[words.size - 2].lowercase().replace(Regex("[^a-zA-Z]"), "") else ""
        } else {
            if (words.isNotEmpty()) words.last().lowercase().replace(Regex("[^a-zA-Z]"), "") else ""
        }
        
        val candidates = mutableListOf<String>()
        
        val contractionAutocorrect = mapOf(
            "dont" to "don't",
            "cant" to "can't",
            "im" to "I'm",
            "its" to "it's",
            "youre" to "you're",
            "theyre" to "they're",
            "wont" to "won't",
            "wouldnt" to "wouldn't",
            "shouldnt" to "shouldn't",
            "couldnt" to "couldn't",
            "isnt" to "isn't",
            "arent" to "aren't",
            "wasnt" to "wasn't",
            "werent" to "weren't",
            "hes" to "he's",
            "shes" to "she's",
            "thats" to "that's",
            "theres" to "there's",
            "whats" to "what's",
            "lets" to "let's",
            "didnt" to "didn't",
            "havent" to "haven't",
            "hasnt" to "hasn't",
            "hadnt" to "hadn't",
            "couldve" to "could've",
            "wouldve" to "would've",
            "shouldve" to "should've",
            "doesnt" to "doesn't"
        )

        if (currentWord.isEmpty()) {
            // Case 1: Cursor is at a word boundary
            // Predict next words based on context
            val predicted = mutableListOf<String>()
            if (contextWord.isNotEmpty() && nextWordPredictions.containsKey(contextWord)) {
                predicted.addAll(nextWordPredictions[contextWord] ?: emptyList())
            }
            // Fallbacks for predictions
            val generalFallbacks = listOf("I", "the", "Hello", "How", "Thanks", "What", "Good", "please", "sorry", "would")
            for (word in generalFallbacks) {
                if (predicted.size >= 3) break
                if (!predicted.contains(word)) {
                    predicted.add(word)
                }
            }
            
            // Gboard suggestion layout when not typing:
            // Left: ","
            // Middle: main predicted next-word
            // Right: "."
            candidates.add(",")
            if (predicted.isNotEmpty()) {
                candidates.add(predicted[0])
            } else {
                candidates.add("I")
            }
            candidates.add(".")
        } else {
            // Case 2: User is actively typing a word
            val prefix = currentWord.lowercase()
            
            val contraction = contractionAutocorrect[prefix]
            if (contraction != null) {
                // If the word matches a contraction, force it as autocorrect target (middle slot)
                candidates.add(currentWord) // Left
                candidates.add(contraction) // Middle
                // Right: fill with general match
                val generalMatches = commonWords.filter { it.startsWith(prefix, ignoreCase = true) }
                val rightCandidate = generalMatches.firstOrNull { it.lowercase() != prefix }
                if (rightCandidate != null) {
                    candidates.add(rightCandidate)
                }
            } else {
                // Prioritize context-aware bigram predictions matching the prefix
                val contextMatches = if (contextWord.isNotEmpty() && nextWordPredictions.containsKey(contextWord)) {
                    nextWordPredictions[contextWord] ?: emptyList()
                } else {
                    emptyList()
                }
                val matchingContextMatches = contextMatches.filter { it.startsWith(prefix, ignoreCase = true) }
                val tempCandidates = mutableListOf<String>()
                tempCandidates.addAll(matchingContextMatches)
                
                // Fill remaining candidate slots with general words starting with prefix
                val generalMatches = commonWords.filter { it.startsWith(prefix, ignoreCase = true) }
                for (match in generalMatches) {
                    if (tempCandidates.size >= 5) break
                    val normalizedMatch = match.replaceFirstChar { char ->
                        if (currentWord.firstOrNull()?.isUpperCase() == true) char.uppercaseChar() else char.lowercaseChar()
                    }
                    if (!tempCandidates.contains(normalizedMatch)) {
                        tempCandidates.add(normalizedMatch)
                    }
                }
                
                // Re-structure candidates:
                // Left (index 0): raw typed word
                // Middle (index 1): best candidate if available
                // Right (index 2): second best candidate
                val list = mutableListOf<String>()
                list.add(currentWord)
                
                val bestCandidate = tempCandidates.firstOrNull { it.lowercase() != prefix }
                if (bestCandidate != null) {
                    list.add(bestCandidate)
                }
                
                val secondCandidate = tempCandidates.firstOrNull { it.lowercase() != prefix && it != bestCandidate }
                if (secondCandidate != null) {
                    list.add(secondCandidate)
                }
                
                candidates.clear()
                candidates.addAll(list)
            }
        }
        
        // Take up to 3 final suggestions and format case matching the typing intent
        val finalSuggestions = candidates
            .distinctBy { it.lowercase() }
            .take(3)
            .map { word ->
                if (word.firstOrNull()?.isUpperCase() == true || word == "," || word == ".") {
                    word
                } else if (currentWord.firstOrNull()?.isUpperCase() == true) {
                    word.replaceFirstChar { it.uppercaseChar() }
                } else {
                    word.lowercase()
                }
            }
            
        suggestions.addAll(finalSuggestions)
    }

    fun selectSuggestion(word: String) {
        val ic: InputConnection = currentInputConnection ?: return
        triggerHaptic()
        
        if (currentWord.isNotEmpty()) {
            ic.deleteSurroundingText(currentWord.length, 0)
        }
        
        // If it's punctuation, clean up preceding space
        if (word in listOf(".", ",", "?", "!", "\"", ";", ":")) {
            val before = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
            if (before == " ") {
                ic.deleteSurroundingText(1, 0)
            }
            ic.commitText("$word ", 1)
        } else {
            ic.commitText("$word ", 1)
        }
        currentWord = ""
        updateSuggestions()
    }

    // Input Operations Helper Métiers mapping directly to Android InputConnection
    fun triggerKeyClick(primaryCode: String) {
        val ic: InputConnection = currentInputConnection ?: return
        triggerHaptic()

        when (primaryCode) {
            "BACKSPACE" -> {
                // Delete one character before cursor
                ic.deleteSurroundingText(1, 0)
                if (currentWord.isNotEmpty()) {
                    currentWord = currentWord.dropLast(1)
                }
                updateSuggestions()
            }
            "SPACE" -> {
                val bestSuggestion = if (suggestions.size >= 2) suggestions[1] else suggestions.firstOrNull()
                if (currentWord.isNotEmpty() && bestSuggestion != null && bestSuggestion.lowercase() != currentWord.lowercase()) {
                    ic.deleteSurroundingText(currentWord.length, 0)
                    ic.commitText("$bestSuggestion ", 1)
                } else {
                    ic.commitText(" ", 1)
                }
                currentWord = ""
                updateSuggestions()
            }
            "ENTER" -> {
                val editorInfo = currentInputEditorInfo
                if (editorInfo != null && (editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION) != 0) {
                    ic.performEditorAction(editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION)
                } else {
                    ic.commitText("\n", 1)
                }
                currentWord = ""
                updateSuggestions()
            }
            else -> {
                if (primaryCode.length == 1) {
                    val isLetter = primaryCode[0].isLetter()
                    ic.commitText(primaryCode, 1)
                    if (isLetter) {
                        currentWord += primaryCode
                    } else {
                        currentWord = ""
                    }
                } else {
                    ic.commitText(primaryCode, 1)
                    currentWord = ""
                }
                updateSuggestions()
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

    fun showKeyboardSwitcher() {
        triggerHaptic()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showInputMethodPicker()
    }
}
