package app.pzip.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.pzip.ui.theme.P

@Composable
fun pzipFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = P.c.ink, unfocusedBorderColor = P.c.border, cursorColor = P.c.ink,
    focusedLabelColor = P.c.text, unfocusedLabelColor = P.c.textMuted,
    focusedContainerColor = P.c.surface, unfocusedContainerColor = P.c.surface,
)

@Composable
fun PDialog(title: String, onDismiss: () -> Unit, confirm: String, onConfirm: () -> Unit, dismiss: String = "Cancel", danger: Boolean = false, confirmEnabled: Boolean = true, content: @Composable () -> Unit = {}) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.c.surface,
        shape = RoundedCornerShape(26.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = P.c.text) },
        text = content,
        confirmButton = {
            Box(Modifier.height(50.dp), contentAlignment = Alignment.Center) {
                if (danger) SoftButton(confirm, onConfirm, danger = true)
                else InkButton(confirm, onConfirm, enabled = confirmEnabled)
            }
        },
        dismissButton = {
            Box(Modifier.height(50.dp), contentAlignment = Alignment.Center) {
                TextLink(dismiss, onDismiss, P.c.text)
            }
        },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, danger: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    PDialog(title, onDismiss, confirm, { onConfirm(); onDismiss() }, danger = danger) {
        Text(text, color = P.c.textMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun InputDialog(
    title: String,
    initial: String = "",
    label: String = "Name",
    confirm: String = "OK",
    selectBase: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    validate: (String) -> String? = { null },
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val end = if (selectBase) initial.lastIndexOf('.').takeIf { it > 0 } ?: initial.length else initial.length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, end))) }
    val error = validate(value.text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    PDialog(title, onDismiss, confirm, { if (error == null && value.text.isNotBlank()) onConfirm(value.text.trim()) }, confirmEnabled = error == null && value.text.isNotBlank()) {
        Column {
            OutlinedTextField(
                value = value, onValueChange = { value = it }, label = { Text(label) }, singleLine = true,
                isError = error != null, colors = pzipFieldColors(), shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (error != null) Text(error, color = P.c.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun PasswordField(value: String, onChange: (String) -> Unit, label: String = "Password", modifier: Modifier = Modifier, error: Boolean = false, onDone: (() -> Unit)? = null) {
    var visible by remember { mutableStateOf(false) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, isError = error,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); onDone?.invoke() }),
        trailingIcon = { IconBtn(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show password", { visible = !visible }) },
        colors = pzipFieldColors(), shape = RoundedCornerShape(14.dp), modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun PasswordDialog(title: String = "Enter password", message: String? = null, wrong: Boolean = false, onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var edited by remember { mutableStateOf(false) }
    val showWrong = wrong && !edited
    val submit = { if (pw.isNotEmpty()) onConfirm(pw.toCharArray()) }
    PDialog(title, onDismiss, "Unlock", submit, confirmEnabled = pw.isNotEmpty()) {
        Column {
            if (message != null) Text(message, color = P.c.textMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp))
            PasswordField(pw, { pw = it; edited = true }, error = showWrong, onDone = submit)
            if (showWrong) Text("Wrong password, try again", color = P.c.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(onDismiss: () -> Unit, title: String? = null, skipPartial: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartial)
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = P.c.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(P.c.border)) },
    ) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 12.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, color = P.c.text, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
            content()
        }
    }
}

@Composable
fun SheetItem(icon: ImageVector, text: String, sub: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    SettingRow(text, sub, icon, onClick = onClick) {}
    if (danger) Unit
}

@Composable
fun ActionGrid(items: List<Triple<ImageVector, String, () -> Unit>>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (icon, label, action) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickableNoRipple(action).padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(P.c.card), contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = P.c.text, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(label, color = P.c.text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

@Composable
fun Gap(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun HGap(w: Int) = Spacer(Modifier.width(w.dp))
