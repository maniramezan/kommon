package io.github.maniramezan.kommon.debugui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.maniramezan.kommon.remoteconfig.ConfigKey
import io.github.maniramezan.kommon.remoteconfig.ConfigValue
import io.github.maniramezan.kommon.remoteconfig.ConfigValueType
import io.github.maniramezan.kommon.remoteconfig.RemoteConfigClient
import io.github.maniramezan.kommon.remoteconfig.ResolvedConfigEntry
import io.github.maniramezan.kommon.remoteconfig.ValueSource
import io.github.maniramezan.kommon.remoteconfig.debug.LocalOverrideStore

/**
 * A debug screen listing every remote-config key with its effective value and where it came from,
 * with an editor per key for setting or resetting a local override.
 *
 * Stateless: [entries] are typically `RemoteConfigClient.allValues()`, and the callbacks write to
 * the override store. Booleans use a switch, keys with `allowedValues` a menu, and other types a
 * text field that only applies values [ConfigKey.accepts]. The search query and in-progress edits
 * are the only internal state.
 */
@Composable
public fun RemoteConfigDebugScreen(
    entries: List<ResolvedConfigEntry>,
    onOverride: (ConfigKey, ConfigValue) -> Unit,
    onResetOverride: (ConfigKey) -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier,
    labels: RemoteConfigDebugLabels = RemoteConfigDebugLabels(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    var confirmResetAll by rememberSaveable { mutableStateOf(false) }
    val visible = remember(entries, query) { entries.matching(query) }
    val hasOverrides = entries.any { it.source == ValueSource.OVERRIDE }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") {
            DebugScreenHeader(labels.title, labels.description, labels.resetAll.takeIf { hasOverrides }) {
                confirmResetAll = true
            }
        }
        item(key = "search") {
            DebugScreenSearch(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = labels.searchPlaceholder,
                clearLabel = labels.clearSearch,
            )
        }
        if (visible.isEmpty()) {
            item(key = "empty") { Text(labels.noMatches) }
        }
        items(visible, key = { it.key.id }) { entry ->
            ConfigEntryCard(entry, onOverride, onResetOverride, labels)
        }
    }

    if (confirmResetAll) {
        AlertDialog(
            title = { Text(labels.resetAllTitle) },
            text = { Text(labels.resetAllMessage) },
            onDismissRequest = { confirmResetAll = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmResetAll = false
                    onResetAll()
                }) { Text(labels.resetAll) }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetAll = false }) { Text(labels.cancel) }
            },
        )
    }
}

/**
 * [RemoteConfigDebugScreen] bound to a [client] and the [overrideStore] its resolver reads, so
 * each edit is written and the list re-resolved immediately. [client] must resolve through
 * [overrideStore] (see `ConfigResolver`), or overrides will not show up here.
 */
@Composable
public fun RemoteConfigDebugScreen(
    client: RemoteConfigClient,
    overrideStore: LocalOverrideStore,
    modifier: Modifier = Modifier,
    labels: RemoteConfigDebugLabels = RemoteConfigDebugLabels(),
) {
    var entries by remember(client, overrideStore) { mutableStateOf(client.allValues()) }
    RemoteConfigDebugScreen(
        entries = entries,
        onOverride = { key, value ->
            overrideStore.setOverride(key, value)
            entries = client.allValues()
        },
        onResetOverride = { key ->
            overrideStore.removeOverride(key)
            entries = client.allValues()
        },
        onResetAll = {
            overrideStore.removeAllOverrides()
            entries = client.allValues()
        },
        modifier = modifier,
        labels = labels,
    )
}

@Composable
private fun ConfigEntryCard(
    entry: ResolvedConfigEntry,
    onOverride: (ConfigKey, ConfigValue) -> Unit,
    onResetOverride: (ConfigKey) -> Unit,
    labels: RemoteConfigDebugLabels,
) {
    val key = entry.key
    val allowedValues = key.allowedValues
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(key.id)
            if (key.description.isNotBlank()) Text(key.description)
            Text(labels.sourceLabel(entry.source))
            when {
                key.valueType == ConfigValueType.BOOL -> {
                    Text(labels.overrideValue)
                    Switch(
                        checked = entry.value.boolValue,
                        onCheckedChange = { onOverride(key, ConfigValue.Bool(it)) },
                        modifier = Modifier.semantics { contentDescription = "${key.id}: ${labels.overrideValue}" },
                    )
                }
                allowedValues != null -> {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text("${labels.overrideValue}: ${entry.value.stringValue}")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            allowedValues.forEach { value ->
                                DropdownMenuItem(text = { Text(value) }, onClick = {
                                    expanded = false
                                    onOverride(key, ConfigValue.StringVal(value))
                                })
                            }
                        }
                    }
                }
                else -> ConfigValueEditor(entry, onOverride, labels)
            }
            if (entry.source == ValueSource.OVERRIDE) {
                TextButton(onClick = { onResetOverride(key) }) { Text(labels.reset) }
            }
        }
    }
}

@Composable
private fun ConfigValueEditor(
    entry: ResolvedConfigEntry,
    onOverride: (ConfigKey, ConfigValue) -> Unit,
    labels: RemoteConfigDebugLabels,
) {
    val key = entry.key
    var draft by rememberSaveable(key.id, entry.value.stringValue) { mutableStateOf(entry.value.stringValue) }
    val parsed = remember(key, draft) { key.parseOverride(draft) }
    val canApply = parsed != null && parsed != entry.value
    val apply = { parsed?.let { onOverride(key, it) } }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text("${key.id}: ${labels.overrideValue}") },
        modifier = Modifier.fillMaxWidth(),
        isError = parsed == null,
        supportingText = if (parsed == null) ({ Text(labels.invalidValue) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = key.valueType.keyboardType(), imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (canApply) apply() }),
        trailingIcon = {
            TextButton(onClick = { apply() }, enabled = canApply) { Text(labels.apply) }
        },
    )
}

private fun ConfigValueType.keyboardType(): KeyboardType =
    when (this) {
        ConfigValueType.INT -> KeyboardType.Number
        ConfigValueType.DOUBLE -> KeyboardType.Decimal
        ConfigValueType.BOOL, ConfigValueType.STRING -> KeyboardType.Text
    }
