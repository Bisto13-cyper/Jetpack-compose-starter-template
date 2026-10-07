package com.superapp.app.features.password

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.superapp.app.features.password.data.VaultBackup
import com.superapp.app.features.password.data.VaultClipboard
import com.superapp.app.features.password.data.VaultEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MASK = "••••••••••••"
private val SecureDialog = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)

/**
 * Entry point of the Password Vault feature. Host it from your navigation (see integration notes).
 * It never touches the network; it locks when leaving the screen/app and after the inactivity timeout.
 */
@Composable
fun PasswordVaultScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val session = remember { VaultSession(context) }

    VaultGuards(session)
    BackHandler(onBack = onClose)

    // Inactivity auto-lock: a single coroutine that sleeps until the deadline (no polling loop).
    LaunchedEffect(session.state, session.autoLockMs) {
        while (session.state == VaultSession.State.UNLOCKED) {
            val remaining = session.autoLockMs - (SystemClock.elapsedRealtime() - session.lastTouch)
            if (remaining <= 0) {
                session.lock()
                break
            }
            delay(remaining)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(session) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        session.touch()
                    }
                }
            }
    ) {
        when (session.state) {
            VaultSession.State.NEEDS_SETUP -> SetupContent(session)
            VaultSession.State.LOCKED -> LockedContent(session, onClose)
            VaultSession.State.UNLOCKED -> UnlockedContent(session, onClose)
        }
    }
}

/** FLAG_SECURE while visible; lock when the app goes to the background or the screen is left. */
@Composable
private fun VaultGuards(session: VaultSession) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val owner = activity as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !session.externalUiOpen) session.lock()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose {
            owner?.lifecycle?.removeObserver(observer)
            session.lock()
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

// =============================================================== setup / lock

@Composable
private fun SetupContent(session: VaultSession) {
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Create vault password", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "This password protects all your entries. It is never stored and cannot be recovered. " +
                "Use at least ${VaultSession.MIN_PASSWORD_LENGTH} characters.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        SecretField("Password", pw) { pw = it; session.touch() }
        Spacer(Modifier.height(8.dp))
        SecretField("Confirm password", confirm) { confirm = it; session.touch() }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !busy,
            onClick = {
                when {
                    pw.length < VaultSession.MIN_PASSWORD_LENGTH ->
                        error = "Use at least ${VaultSession.MIN_PASSWORD_LENGTH} characters."
                    pw != confirm -> error = "Passwords do not match."
                    else -> scope.launch {
                        busy = true
                        val chars = pw.toCharArray()
                        pw = ""; confirm = ""
                        val ok = session.setUp(chars)
                        chars.fill('\u0000')
                        busy = false
                        if (!ok) error = "Could not create the vault."
                    }
                }
            },
        ) { Text(if (busy) "Please wait…" else "Create vault") }
    }
}

@Composable
private fun LockedContent(session: VaultSession, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val biometricReady = session.biometricEnrolled && VaultBiometric.isAvailable(context)

    fun startBiometric() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val cipher = session.biometricUnlockCipher() ?: return
        VaultBiometric.authenticate(
            context = context,
            cipher = cipher,
            title = "Unlock Password Vault",
            onSuccess = { c ->
                scope.launch {
                    if (!session.finishBiometricUnlock(c)) error = "Biometric unlock failed. Use your password."
                }
            },
            onFailure = { },
        )
    }

    LaunchedEffect(biometricReady) { if (biometricReady) startBiometric() }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Password Vault is locked", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        SecretField("Vault password", pw) { pw = it; session.touch() }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !busy && pw.isNotEmpty(),
            onClick = {
                scope.launch {
                    busy = true
                    val chars = pw.toCharArray()
                    pw = ""
                    val r = session.unlockWithPassword(chars)
                    chars.fill('\u0000')
                    busy = false
                    error = when (r) {
                        UnlockOutcome.Success -> null
                        UnlockOutcome.WrongPassword -> "Wrong password."
                        is UnlockOutcome.LockedOut ->
                            "Too many attempts. Try again in ${(r.remainingMs + 999) / 1000} s."
                        UnlockOutcome.Failure -> "Could not unlock the vault."
                    }
                }
            },
        ) { Text(if (busy) "Checking…" else "Unlock") }
        if (biometricReady) {
            TextButton(onClick = { startBiometric() }) { Text("Use biometrics / device lock") }
        }
        TextButton(onClick = onClose) { Text("Close") }
    }
}

// ================================================================== unlocked

@Composable
private fun UnlockedContent(session: VaultSession, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<VaultEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showSecurity by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        session.externalUiOpen = false
        val bytes = pendingExport
        pendingExport = null
        if (uri != null && bytes != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes); true } ?: false
                    } catch (e: Exception) {
                        false
                    }
                }
                message = if (ok) "Encrypted backup saved." else "Could not save the backup file."
            }
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        session.externalUiOpen = false
        if (uri != null) restoreUri = uri
    }

    val filtered = remember(session.entries.toList(), query) {
        val q = query.trim()
        if (q.isEmpty()) session.entries.toList()
        else session.entries.filter { it.name.contains(q, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PASSWORDS", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Row {
                TextButton(onClick = { session.lock() }) { Text("Lock") }
                TextButton(onClick = { menuOpen = true }) { Text("⋮") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Export encrypted backup") }, onClick = {
                        menuOpen = false; showExport = true
                    })
                    DropdownMenuItem(text = { Text("Restore from backup") }, onClick = {
                        menuOpen = false
                        session.externalUiOpen = true
                        openLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    })
                    DropdownMenuItem(text = { Text("Security settings") }, onClick = {
                        menuOpen = false; showSecurity = true
                    })
                    DropdownMenuItem(text = { Text("Close vault") }, onClick = {
                        menuOpen = false; onClose()
                    })
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; session.touch() },
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Add Password") }
        Spacer(Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Text(
                if (session.entries.isEmpty()) "No passwords yet." else "No matches.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(8.dp),
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { entry ->
                val revealed = entry.id in session.revealedIds
                Card(Modifier.fillMaxWidth().clickable { editing = entry }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (revealed) entry.value else MASK,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        IconButton(onClick = { session.toggleReveal(entry.id) }) {
                            Text(if (revealed) "🙈" else "👁")
                        }
                        IconButton(onClick = {
                            VaultClipboard.copy(context, entry.value, sensitive = true)
                            Toast.makeText(context, "Password copied (clears in 30 s)", Toast.LENGTH_SHORT).show()
                        }) { Text("📋") }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ dialogs

    if (adding) {
        EntryDialog(
            entry = null,
            onDismiss = { adding = false },
            onSave = { n, v -> scope.launch { if (session.upsert(null, n, v)) adding = false else message = "Could not save." } },
            onDelete = null,
            session = session,
        )
    }
    editing?.let { e ->
        EntryDialog(
            entry = e,
            onDismiss = { editing = null },
            onSave = { n, v -> scope.launch { if (session.upsert(e.id, n, v)) editing = null else message = "Could not save." } },
            onDelete = { scope.launch { session.delete(e.id); editing = null } },
            session = session,
        )
    }
    if (showExport) {
        BackupPasswordDialog(
            title = "Export Password Vault",
            description = "Choose a backup password. It is not stored; without it the backup cannot be opened. " +
                "Use at least ${VaultSession.MIN_BACKUP_PASSWORD_LENGTH} characters.",
            confirmLabel = "Create backup",
            requireConfirm = true,
            session = session,
            onDismiss = { showExport = false },
            onSubmit = { chars, done ->
                scope.launch {
                    val bytes = session.createBackup(chars)
                    chars.fill('\u0000')
                    done()
                    showExport = false
                    if (bytes == null) {
                        message = "Could not create the backup."
                    } else {
                        pendingExport = bytes
                        session.externalUiOpen = true
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                        createLauncher.launch("password-vault-backup-$stamp.zip")
                    }
                }
            },
        )
    }
    restoreUri?.let { uri ->
        BackupPasswordDialog(
            title = "Restore Password Vault",
            description = "Enter the backup password for the selected file.",
            confirmLabel = "Restore",
            requireConfirm = false,
            session = session,
            onDismiss = { restoreUri = null },
            onSubmit = { chars, done ->
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openInputStream(uri)?.use {
                                VaultBackup.readLimited(it, VaultBackup.MAX_ZIP_BYTES)
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                    val result = if (bytes == null) ImportResult(0, 0, "Could not read the selected file.")
                    else session.importBackup(bytes, chars)
                    chars.fill('\u0000')
                    done()
                    restoreUri = null
                    message = result.error
                        ?: "Imported ${result.added} entries (${result.skipped} duplicates skipped)."
                }
            },
        )
    }
    if (showSecurity) {
        SecurityDialog(session = session, onDismiss = { showSecurity = false })
    }
    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            properties = SecureDialog,
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
            text = { Text(it) },
        )
    }
}

@Composable
private fun EntryDialog(
    entry: VaultEntry?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)?,
    session: VaultSession,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(entry?.name ?: "") }
    var value by remember { mutableStateOf(entry?.value ?: "") }
    var show by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val valid = name.isNotBlank() && value.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = SecureDialog,
        title = { Text(if (entry == null) "Add Password" else "Edit Password") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= VaultSession.MAX_NAME) name = it; session.touch() },
                    label = { Text("Name (e.g. Facebook)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { if (it.length <= VaultSession.MAX_VALUE) value = it; session.touch() },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false),
                    trailingIcon = { IconButton(onClick = { show = !show }) { Text(if (show) "🙈" else "👁") } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    TextButton(onClick = { VaultClipboard.copy(context, name, sensitive = false) }) { Text("Copy name") }
                    TextButton(onClick = {
                        VaultClipboard.copy(context, value, sensitive = true)
                        Toast.makeText(context, "Password copied (clears in 30 s)", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy password") }
                }
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(name.trim(), value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            properties = SecureDialog,
            title = { Text("Delete entry?") },
            text = { Text("This cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BackupPasswordDialog(
    title: String,
    description: String,
    confirmLabel: String,
    requireConfirm: Boolean,
    session: VaultSession,
    onDismiss: () -> Unit,
    onSubmit: (CharArray, done: () -> Unit) -> Unit,
) {
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = SecureDialog,
        title = { Text(title) },
        text = {
            Column {
                Text(description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                SecretField("Backup password", pw) { pw = it; session.touch() }
                if (requireConfirm) {
                    Spacer(Modifier.height(8.dp))
                    SecretField("Confirm backup password", confirm) { confirm = it; session.touch() }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) Text("Working…")
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && pw.isNotEmpty(), onClick = {
                when {
                    requireConfirm && pw.length < VaultSession.MIN_BACKUP_PASSWORD_LENGTH ->
                        error = "Use at least ${VaultSession.MIN_BACKUP_PASSWORD_LENGTH} characters."
                    requireConfirm && pw != confirm -> error = "Passwords do not match."
                    else -> {
                        busy = true
                        val chars = pw.toCharArray()
                        pw = ""; confirm = ""
                        onSubmit(chars) { busy = false }
                    }
                }
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SecurityDialog(session: VaultSession, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sub by remember { mutableStateOf(0) } // 0 main, 1 change password, 2 reset confirm
    var info by remember { mutableStateOf<String?>(null) }
    val biometricAvailable = VaultBiometric.isAvailable(context)

    fun setBiometric(enable: Boolean) {
        if (!enable) { session.disableBiometric(); return }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val cipher = session.biometricEnrollCipher()
        if (cipher == null) { info = "Could not prepare biometric unlock."; return }
        VaultBiometric.authenticate(
            context = context,
            cipher = cipher,
            title = "Enable biometric unlock",
            onSuccess = { c ->
                info = if (session.finishBiometricEnroll(c)) null else "Could not enable biometric unlock."
            },
            onFailure = { info = "Biometric setup was cancelled." },
        )
    }

    when (sub) {
        1 -> ChangePasswordDialog(
            session = session,
            onDismiss = { sub = 0 },
            onDone = { ok -> sub = 0; info = if (ok) "Password changed." else "Wrong current password or error." },
        )
        2 -> AlertDialog(
            onDismissRequest = { sub = 0 },
            properties = SecureDialog,
            title = { Text("Reset vault?") },
            text = { Text("This permanently deletes ALL entries and keys on this device. Export a backup first if you need the data.") },
            confirmButton = {
                TextButton(onClick = { session.resetVault(); onDismiss() }) {
                    Text("Delete everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { sub = 0 }) { Text("Cancel") } },
        )
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            properties = SecureDialog,
            title = { Text("Security settings") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Biometric / device-lock unlock")
                            if (!biometricAvailable) {
                                Text(
                                    "Needs Android 11+ and a fingerprint/strong face or screen lock set up.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Switch(
                            checked = session.biometricEnrolled,
                            enabled = biometricAvailable || session.biometricEnrolled,
                            onCheckedChange = { setBiometric(it) },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Auto-lock after inactivity")
                    Row {
                        listOf(30_000L to "30s", 60_000L to "1m", 300_000L to "5m", 900_000L to "15m").forEach { (ms, label) ->
                            TextButton(onClick = { session.setAutoLock(ms) }) {
                                Text(if (session.autoLockMs == ms) "[$label]" else label)
                            }
                        }
                    }
                    Text(
                        "The vault also locks whenever you leave the app or this screen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { sub = 1 }) { Text("Change vault password") }
                    TextButton(onClick = { sub = 2 }) {
                        Text("Reset vault…", color = MaterialTheme.colorScheme.error)
                    }
                    info?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        )
    }
}

@Composable
private fun ChangePasswordDialog(session: VaultSession, onDismiss: () -> Unit, onDone: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = SecureDialog,
        title = { Text("Change vault password") },
        text = {
            Column {
                SecretField("Current password", old) { old = it; session.touch() }
                Spacer(Modifier.height(8.dp))
                SecretField("New password", new) { new = it; session.touch() }
                Spacer(Modifier.height(8.dp))
                SecretField("Confirm new password", confirm) { confirm = it; session.touch() }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && old.isNotEmpty(), onClick = {
                when {
                    new.length < VaultSession.MIN_PASSWORD_LENGTH ->
                        error = "Use at least ${VaultSession.MIN_PASSWORD_LENGTH} characters."
                    new != confirm -> error = "Passwords do not match."
                    else -> scope.launch {
                        busy = true
                        val o = old.toCharArray(); val n = new.toCharArray()
                        old = ""; new = ""; confirm = ""
                        val ok = session.changePassword(o, n)
                        o.fill('\u0000'); n.fill('\u0000')
                        busy = false
                        onDone(ok)
                    }
                }
            }) { Text(if (busy) "Working…" else "Change") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}

// ================================================================== helpers

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
