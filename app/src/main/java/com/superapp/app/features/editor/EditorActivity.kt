package com.superapp.app.features.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.superapp.app.features.editor.data.EditorSettingsStore
import com.superapp.app.features.editor.data.WorkspaceStore

class EditorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EditorSettingsStore.init(this)
        WorkspaceStore.init(this)
        setContent {
            MaterialTheme {
                EditorRoot()
            }
        }
    }
}
