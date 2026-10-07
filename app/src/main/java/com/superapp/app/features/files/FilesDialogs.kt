package com.superapp.app.features.files

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.superapp.app.features.files.archive.ArchiveFormat
import com.superapp.app.features.files.model.ConflictPolicy
import com.superapp.app.features.files.model.FileDetails
import com.superapp.app.features.files.model.FileItem
import com.superapp.app.features.files.model.formatDate
import com.superapp.app.features.files.model.formatSize
import com.superapp.app.features.files.model.userMessage
import com.superapp.app.features.files.ssh.KeyKind
import com.superapp.app.features.files.ssh.SshAuth
import com.superapp.app.features.files.ssh.SshConnectionManager
import com.superapp.app.features.files.ssh.SshProfile
import com.superapp.app.features.files.store.FileIconStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SecureDialog = DialogProperties(securePolicy = SecureFlagPolicy.Inherit)

@Composable
internal fun FilesDialogs(c: FilesController, d: FilesDialog?, onDismiss: () -> Unit, onOpen: (FilesDialog) -> Unit) {
    when (d) {
        null -> {}
        FilesDialog.NewFolder -> TextDialog("New folder", "Folder name", "", onDismiss) { c.createFolder(it); onDismiss() }
        FilesDialog.NewFile -> TextDialog("New file", "File name (e.g. notes.txt)", "", onDismiss) { c.createFile(it); onDismiss() }
        is FilesDialog.Rename -> TextDialog("Rename", "New name", d.item.name, onDismiss) { c.rename(d.item, it); onDismiss() }
        FilesDialog.ConfirmDelete -> {
            val n = c.selection.size
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Delete $n item(s)?") },
                text = { Text("Folders are deleted with everything inside. This cannot be undone.") },
                confirmButton = { TextButton(onClick = { c.deleteSelected(); onDismiss() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            )
        }
        FilesDialog.Conflict -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Name conflicts") },
                text = { Text("Some items already exist in this folder. What should happen?") },
                confirmButton = {
                    Column {
                        TextButton(onClick = { c.paste(ConflictPolicy.KEEP_BOTH); onDismiss() }) { Text("Keep both (rename new)") }
                        TextButton(onClick = { c.paste(ConflictPolicy.SKIP); onDismiss() }) { Text("Skip existing") }
                        TextButton(onClick = { c.paste(ConflictPolicy.OVERWRITE); onDismiss() }) { Text("Overwrite", color = MaterialTheme.colorScheme.error) }
                    }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            )
        }
        FilesDialog.Compress -> {
            var name by remember { mutableStateOf(c.selectedItems().singleOrNull()?.name ?: "archive") }
            var fmt by remember { mutableStateOf(ArchiveFormat.ZIP) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Compress ${c.selection.size} item(s)") },
                text = {
                    Column {
                        OutlinedTextField(name, { name = it }, label = { Text("Archive name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        ArchiveFormat.values().forEach { f ->
                            Row(Modifier.clickable { fmt = f }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = fmt == f, onClick = { fmt = f }); Text(f.label)
                            }
                        }
                        Text("ZIP is universal. TAR.GZ handles very large archives better. Symbolic links are skipped.", fontSize = 12.sp)
                    }
                },
                confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { c.compress(fmt, name.trim()); onDismiss() }) { Text("Compress") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            )
        }
        is FilesDialog.Details -> DetailsDialog(c, d.item, onDismiss)
        FilesDialog.Display -> DisplayDialog(c, onDismiss)
        FilesDialog.Icons -> IconsDialog(c, onDismiss)
        FilesDialog.SshList -> {
            val list = c.profiles.list()
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("SSH / Termux connections") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (list.isEmpty()) Text("No connections yet.")
                        list.forEach { p ->
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(p.name, fontWeight = FontWeight.Bold)
                                Text(p.display, fontSize = 12.sp)
                                Row {
                                    TextButton(onClick = { onDismiss(); c.connect(p) }) { Text("Connect") }
                                    TextButton(onClick = { onOpen(FilesDialog.SshEdit(p)) }) { Text("Edit") }
                                    TextButton(onClick = {
                                        c.sources().firstOrNull { it.id == "ssh:${p.id}" }?.let { c.removeSource(it) }; onDismiss()
                                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { onOpen(FilesDialog.SshEdit(null)) }) { Text("+ New") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
            )
        }
        is FilesDialog.SshEdit -> SshEditDialog(c, d.profile, onDismiss)
    }
}

@Composable
private fun TextDialog(title: String, label: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onOk(text.trim()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DetailsDialog(c: FilesController, item: FileItem, onDismiss: () -> Unit) {
    var d by remember { mutableStateOf<FileDetails?>(null) }
    androidx.compose.runtime.LaunchedEffect(item.id) { d = try { c.details(item) } catch (_: Exception) { null } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            val x = d
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (x == null) Text("Loading…") else {
                    Text("Path: ${x.path}", fontSize = 13.sp)
                    Text("Type: ${if (x.isDirectory) "Folder" else x.extension.ifEmpty { "File" }}${x.mime?.let { " ($it)" } ?: ""}", fontSize = 13.sp)
                    if (!x.isDirectory) Text("Size: ${formatSize(x.size)} (${x.size} bytes)", fontSize = 13.sp)
                    x.itemCount?.let { Text("Items: $it", fontSize = 13.sp) }
                    x.totalSize?.let { Text("Total size: ${formatSize(it)}${if (x.countIsPartial) "+ (partial)" else ""}", fontSize = 13.sp) }
                    Text("Modified: ${formatDate(x.modified)}", fontSize = 13.sp)
                    Text("Created: ${if (x.created > 0) formatDate(x.created) else "not available"}", fontSize = 13.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun DisplayDialog(c: FilesController, onDismiss: () -> Unit) {
    val p = c.prefs
    val palette = listOf(0, 0xFFFFB300.toInt(), 0xFF42A5F5.toInt(), 0xFF66BB6A.toInt(), 0xFFEF5350.toInt(), 0xFFAB47BC.toInt(), 0xFF8D6E63.toInt())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Display options") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                @Composable fun check(t: String, v: Boolean, set: (Boolean) -> Unit) =
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(v, set); Text(t) }
                check("Show file size", p.showSize.value) { p.showSize.set(it) }
                check("Show date", p.showDate.value) { p.showDate.set(it) }
                check("Show file type", p.showType.value) { p.showType.set(it) }
                check("Show hidden files", p.showHidden.value) { p.showHidden.set(it); c.resort(); c.reload() }
                Text("Corner radius: ${p.cornerDp.value} dp")
                Slider(p.cornerDp.value.toFloat(), { p.cornerDp.set(it.toInt()) }, valueRange = 0f..28f)
                Text("Spacing: ${p.spacingDp.value} dp")
                Slider(p.spacingDp.value.toFloat(), { p.spacingDp.set(it.toInt()) }, valueRange = 0f..16f)
                Text("Folder colour (0 = theme)")
                Row {
                    palette.forEach { col ->
                        Spacer(Modifier.size(4.dp))
                        Text(
                            if (col == 0) "∅" else "", textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.size(32.dp)
                                .background(if (col == 0) MaterialTheme.colorScheme.surfaceVariant else Color(col), RoundedCornerShape(8.dp))
                                .clickable { p.folderColor.set(col) },
                        )
                    }
                }
                Text("Window colours follow the app theme (Settings → Appearance).", fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun IconsDialog(c: FilesController, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val k = FileIconStore.normalizeKey(key)
        if (uri != null && k != null) scope.launch {
            info = try { withContext(Dispatchers.IO) { c.icons.setImage(k, uri) }; "Icon saved for .$k" } catch (e: Exception) { e.userMessage() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File type icons") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Type an extension (any, e.g. py, md, kt, zip) or \"folder\", then pick an image or an emoji.", fontSize = 12.sp)
                OutlinedTextField(key, { key = it }, label = { Text("Extension or folder") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(emoji, { emoji = it }, label = { Text("Emoji") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(enabled = FileIconStore.normalizeKey(key) != null && emoji.isNotBlank(), onClick = {
                        c.icons.setEmoji(FileIconStore.normalizeKey(key)!!, emoji); info = "Icon saved."
                    }) { Text("Set") }
                }
                TextButton(enabled = FileIconStore.normalizeKey(key) != null, onClick = { picker.launch("image/*") }) { Text("Choose image…") }
                info?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(8.dp))
                Text("Your icons", fontWeight = FontWeight.Bold)
                if (c.icons.mappings.isEmpty()) Text("None yet (defaults are used).", fontSize = 12.sp)
                c.icons.mappings.keys.sorted().forEach { k ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val e = c.icons.emoji(k)
                        Text(if (e != null) "$e  .$k" else "🖼  .$k", modifier = Modifier.weight(1f))
                        TextButton(onClick = { key = k }) { Text("Edit") }
                        TextButton(onClick = { c.icons.remove(k) }) { Text("Reset") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SshEditDialog(c: FilesController, existing: SshProfile?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var port by remember { mutableStateOf(existing?.port?.toString() ?: "") }
    var user by remember { mutableStateOf(existing?.username ?: "") }
    var auth by remember { mutableStateOf(existing?.auth ?: SshAuth.PASSWORD) }
    var password by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var keyText by remember { mutableStateOf("") }
    var publicLine by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    val keyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val t = withContext(Dispatchers.IO) {
                try { context.contentResolver.openInputStream(uri)?.use { it.readBytes().take(65_536).toByteArray().toString(Charsets.UTF_8) } } catch (_: Exception) { null }
            }
            if (t != null && t.contains("PRIVATE KEY")) { keyText = t; info = "Private key loaded." } else info = "That file is not a PEM/OpenSSH private key."
        }
    }

    fun build(): SshProfile? {
        val p = port.toIntOrNull()
        if (host.isBlank() || user.isBlank() || p == null || p !in 1..65535) { info = "Host, user and a valid port (1–65535) are required."; return null }
        return SshProfile(
            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
            name = name.ifBlank { "$user@$host" }, host = host.trim(), port = p, username = user.trim(), auth = auth,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(if (existing == null) "New SSH connection" else "Edit connection") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(host, { host = it }, label = { Text("Host / IP") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port (Termux sshd usually 8022)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(user, { user = it }, label = { Text("Username (Termux: run whoami)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row {
                    Row(Modifier.clickable { auth = SshAuth.PASSWORD }, verticalAlignment = Alignment.CenterVertically) { RadioButton(auth == SshAuth.PASSWORD, { auth = SshAuth.PASSWORD }); Text("Password") }
                    Row(Modifier.clickable { auth = SshAuth.KEY }, verticalAlignment = Alignment.CenterVertically) { RadioButton(auth == SshAuth.KEY, { auth = SshAuth.KEY }); Text("SSH key") }
                }
                if (auth == SshAuth.PASSWORD) {
                    OutlinedTextField(password, { password = it }, label = { Text(if (existing?.hasPassword == true) "Password (blank = keep saved)" else "Password") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false), modifier = Modifier.fillMaxWidth())
                } else {
                    Text(if (keyText.isNotEmpty()) "New private key ready (${keyText.length} chars)" else if (existing?.hasKey == true) "A private key is saved." else "No private key yet.", fontSize = 12.sp)
                    Row {
                        TextButton(onClick = { keyPicker.launch(arrayOf("*/*")) }) { Text("Import key file") }
                        TextButton(onClick = {
                            scope.launch { val (priv, pub) = withContext(Dispatchers.Default) { SshConnectionManager.generateKey(KeyKind.ECDSA_256, "superapp-files") }
                                keyText = priv; publicLine = pub; info = "Key generated. Add the public key to Termux (see below)." }
                        }) { Text("Generate ECDSA") }
                        TextButton(onClick = {
                            scope.launch { val (priv, pub) = withContext(Dispatchers.Default) { SshConnectionManager.generateKey(KeyKind.RSA_3072, "superapp-files") }
                                keyText = priv; publicLine = pub; info = "Key generated. Add the public key to Termux (see below)." }
                        }) { Text("Generate RSA") }
                    }
                    OutlinedTextField(passphrase, { passphrase = it }, label = { Text("Key passphrase (if the key has one)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    publicLine?.let { pub ->
                        Text("Public key (append to ~/.ssh/authorized_keys in Termux):", fontSize = 12.sp)
                        Text(pub, fontSize = 10.sp)
                        TextButton(onClick = {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ssh public key", pub))
                            info = "Public key copied."
                        }) { Text("Copy public key") }
                    }
                }
                info?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(4.dp))
                Row {
                    TextButton(onClick = {
                        val p = build() ?: return@TextButton
                        c.saveProfile(p, password.takeIf { it.isNotEmpty() }, keyText.takeIf { it.isNotEmpty() }, passphrase.takeIf { it.isNotEmpty() })
                        c.testProfile(p)
                    }) { Text("Save & test") }
                    if (existing != null) TextButton(onClick = { c.ssh.forgetHost(existing); info = "Saved host key forgotten; you will be asked to verify it again." }) { Text("Forget host key") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = build() ?: return@TextButton
                c.saveProfile(p, password.takeIf { it.isNotEmpty() }, keyText.takeIf { it.isNotEmpty() }, passphrase.takeIf { it.isNotEmpty() })
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

