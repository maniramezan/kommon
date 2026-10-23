package io.github.maniramezan.kommon.debugui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

@Composable
internal fun DebugScreenHeader(
    title: String,
    description: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
    Text(description)
    if (actionLabel != null) {
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
internal fun DebugScreenSearch(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearLabel: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(placeholder) },
        singleLine = true,
        trailingIcon = {
            if (value.isNotEmpty()) {
                TextButton(onClick = { onValueChange("") }) { Text(clearLabel) }
            }
        },
    )
}
