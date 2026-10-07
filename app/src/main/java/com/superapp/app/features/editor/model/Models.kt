package com.superapp.app.features.editor.model

import android.net.Uri

data class WorkspaceNode(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val children: List<WorkspaceNode>? = null, // null = not yet loaded
    val lastModified: Long = 0L,
    val size: Long = 0L
) {
    val isLoaded: Boolean get() = children != null
}

enum class TokenStyle {
    PLAIN, KEYWORD, TYPE, STRING, NUMBER, COMMENT, OPERATOR, FUNCTION,
    TAG, ATTRIBUTE, PROPERTY, CONSTANT
}

data class Token(val start: Int, val end: Int, val style: TokenStyle)

data class OpenDocument(
    val uri: Uri,
    val name: String,
    val languageId: String,
    val content: String,
    val savedContent: String,
    val readOnly: Boolean = false,
    val truncated: Boolean = false
) {
    val isDirty: Boolean get() = content != savedContent
}
