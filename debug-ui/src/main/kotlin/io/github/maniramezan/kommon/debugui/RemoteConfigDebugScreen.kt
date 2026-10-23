package io.github.maniramezan.kommon.debugui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.maniramezan.kmpcomponents.ConfirmDialog
import io.github.maniramezan.kmpcomponents.EmptyState
import io.github.maniramezan.kmpcomponents.FormTextField
import io.github.maniramezan.kmpcomponents.MenuPicker
import io.github.maniramezan.kmpcomponents.SearchField
import io.github.maniramezan.kmpcomponents.SearchFieldLabels
import io.github.maniramezan.kmpcomponents.SectionCard
import io.github.maniramezan.kmpcomponents.SectionHeader
import io.github.maniramezan.kmpcomponents.StatusTone
import io.github.maniramezan.kmpcomponents.ThemeSwitch
import io.github.maniramezan.kmpcomponents.TonalBadge
import io.github.maniramezan.kmpcomponents.kmpSpacing
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
    val spacing = MaterialTheme.kmpSpacing

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        item(key = "header") {
            SectionHeader(
                title = labels.title,
                subtitle = labels.description,
                actionLabel = labels.resetAll.takeIf { hasOverrides },
                onAction = if (hasOverrides) ({ confirmResetAll = true }) else null,
            )
        }
        item(key = "search") {
            SearchField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                labels = SearchFieldLabels(placeholder = labels.searchPlaceholder, clear = labels.clearSearch),
            )
        }
        if (visible.isEmpty()) {
            item(key = "empty") { EmptyState(title = labels.noMatches) }
        }
        items(visible, key = { it.key.id }) { entry ->
            ConfigEntryCard(entry, onOverride, onResetOverride, labels)
        }
    }

    if (confirmResetAll) {
        ConfirmDialog(
            title = labels.resetAllTitle,
            message = labels.resetAllMessage,
            onConfirm = {
                confirmResetAll = false
                onResetAll()
            },
            onDismiss = { confirmResetAll = false },
            confirmLabel = labels.resetAll,
            destructive = true,
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
    var entries by remember(client) { mutableStateOf(client.allValues()) }
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
    SectionCard(
        title = key.id,
        modifier = Modifier.fillMaxWidth(),
        description = key.description.takeIf { it.isNotBlank() },
        action = { SourceBadge(entry.source, labels) },
    ) {
        when {
            key.valueType == ConfigValueType.BOOL ->
                ThemeSwitch(
                    checked = entry.value.boolValue,
                    onCheckedChange = { onOverride(key, ConfigValue.Bool(it)) },
                    label = labels.overrideValue,
                )
            allowedValues != null ->
                MenuPicker(
                    items = allowedValues,
                    selected = entry.value.stringValue,
                    onSelect = { onOverride(key, ConfigValue.StringVal(it)) },
                    label = labels.overrideValue,
                    itemLabel = { it },
                )
            else -> ConfigValueEditor(entry, onOverride, labels)
        }
        if (entry.source == ValueSource.OVERRIDE) {
            TextButton(onClick = { onResetOverride(key) }) { Text(labels.reset) }
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
    FormTextField(
        value = draft,
        onValueChange = { draft = it },
        label = labels.overrideValue,
        modifier = Modifier.fillMaxWidth(),
        errorText = labels.invalidValue.takeIf { parsed == null },
        keyboardOptions = KeyboardOptions(keyboardType = key.valueType.keyboardType(), imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (canApply) apply() }),
        trailingContent = {
            TextButton(onClick = { apply() }, enabled = canApply) { Text(labels.apply) }
        },
    )
}

@Composable
private fun SourceBadge(
    source: ValueSource,
    labels: RemoteConfigDebugLabels,
) {
    when (source) {
        ValueSource.OVERRIDE -> TonalBadge(text = labels.sourceOverride, tone = StatusTone.WARNING)
        ValueSource.REMOTE -> TonalBadge(text = labels.sourceRemote, tone = StatusTone.INFO)
        ValueSource.DEFAULT -> TonalBadge(text = labels.sourceDefault, color = MaterialTheme.colorScheme.outline)
    }
}

private fun ConfigValueType.keyboardType(): KeyboardType =
    when (this) {
        ConfigValueType.INT -> KeyboardType.Number
        ConfigValueType.DOUBLE -> KeyboardType.Decimal
        ConfigValueType.BOOL, ConfigValueType.STRING -> KeyboardType.Text
    }
