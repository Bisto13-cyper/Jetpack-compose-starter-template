package com.superapp.app.features.keyboard

data class EmojiCategory(
    val id: String,
    val label: String,
    val emojis: List<String>
)

/**
 * Emoji are inserted as text into the editor via InputConnection.commitText.
 * The keyboard cannot replace how Android renders Unicode emoji — it only
 * inserts the Unicode code points. Custom emoji sets are not possible without
 * a custom font applied to the target editor, which is outside IME control.
 */
object EmojiRepository {

    val categories: List<EmojiCategory> = listOf(
        EmojiCategory(
            "smileys", "Smileys",
            listOf(
                "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃",
                "😉","😊","😇","🥰","😍","🤩","😘","😗","😚","😙",
                "😋","😛","😜","🤪","😝","🤑","🤗","🤭","🤫","🤔"
            )
        ),
        EmojiCategory(
            "gestures", "Gestures",
            listOf(
                "👍","👎","👌","✌️","🤞","🤟","🤘","🤙","👈","👉",
                "👆","👇","☝️","✋","🤚","🖐","🖖","👋","🤝","🙏"
            )
        ),
        EmojiCategory(
            "animals", "Animals",
            listOf(
                "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯",
                "🦁","🐮","🐷","🐸","🐵","🐔","🐧","🐦","🐤","🦆"
            )
        ),
        EmojiCategory(
            "food", "Food",
            listOf(
                "🍏","🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🍈",
                "🍒","🍑","🥭","🍍","🥥","🥝","🍅","🍆","🥑","🥦"
            )
        ),
        EmojiCategory(
            "objects", "Objects",
            listOf(
                "⌚","📱","💻","⌨️","🖥","🖨","🖱","🖲","🕹","🗜",
                "💽","💾","💿","📀","📼","📷","📸","📹","🎥","📽"
            )
        ),
        EmojiCategory(
            "symbols", "Symbols",
            listOf(
                "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔",
                "❣️","💕","💞","💓","💗","💖","💘","💝","💟","☮️"
            )
        )
    )

    fun byId(id: String): EmojiCategory? = categories.firstOrNull { it.id == id }
}
