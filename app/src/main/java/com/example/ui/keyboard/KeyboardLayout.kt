package com.example.ui.keyboard

object KeyboardLayout {
    val qwertyKeys = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("SHIFT", "z", "x", "c", "v", "b", "n", "m", "BACKSPACE"),
        listOf("?123", "EMOJI", ",", "SPACE", ".", "ENTER")
    )

    val symbolKeys = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/"),
        listOf("ALT_SYM", "*", "\"", "'", ":", ";", "!", "?", "BACKSPACE"),
        listOf("ABC", "EMOJI", ",", "SPACE", ".", "ENTER")
    )

    val altSymbolKeys = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆"),
        listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\"),
        listOf("?123", "<", ">", "[", "]", "©", "®", "™", "BACKSPACE"),
        listOf("ABC", "EMOJI", ",", "SPACE", ".", "ENTER")
    )

    val popularEmojis = listOf(
        "😀", "😂", "🤣", "😍", "🥰", "😊", "🙏", "👍", "🔥", "😭",
        "😘", "🥺", "🎉", "👏", "❤️", "🤔", "💡", "🚀", "💀", "👀",
        "✨", "💯", "🎂", "💻", "💼", "📍", "😎", "🤗", "😅", "🥳",
        "😢", "💪", "🙌", "🤝", "💕", "🌟", "📱", "🎶", "☕", "🍕"
    )
}
