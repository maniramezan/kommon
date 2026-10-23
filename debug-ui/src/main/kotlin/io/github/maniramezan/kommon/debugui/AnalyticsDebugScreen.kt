package io.github.maniramezan.kommon.debugui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.maniramezan.kommon.analytics.debug.AnalyticsDebugAction
import io.github.maniramezan.kommon.analytics.debug.AnalyticsDebugEntry
import io.github.maniramezan.kommon.analytics.debug.AnalyticsDebugStore
import java.time.ZoneId

/**
 * A debug log of captured analytics calls (see `AnalyticsCaptureClient`), newest first, filterable
 * by action and by a text query over names and properties.
 *
 * Stateless apart from the filter: [entries] are typically `AnalyticsDebugStore.entries`, and
 * [onClear] clears the store. Times are shown in [zone].
 */
@Composable
public fun AnalyticsDebugScreen(
    entries: List<AnalyticsDebugEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    labels: AnalyticsDebugLabels = AnalyticsDebugLabels(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    var action by rememberSaveable { mutableStateOf<AnalyticsDebugAction?>(null) }
    val actions = remember(entries) { entries.actions() }
    val visible = remember(entries, action, query) { entries.newestFirst(action, query) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") {
            DebugScreenHeader(labels.title, labels.description, labels.clear.takeIf { entries.isNotEmpty() }, onClear)
            Text("${entries.size} ${labels.entryCount}")
        }
        if (entries.isEmpty()) {
            item(key = "empty") { Text(labels.empty) }
            return@LazyColumn
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
        item(key = "filters") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = action == null, onClick = { action = null }, label = { Text(labels.allActions) })
                actions.forEach { candidate ->
                    FilterChip(selected = action == candidate, onClick = { action = candidate }, label = { Text(labels.label(candidate)) })
                }
            }
        }
        if (visible.isEmpty()) {
            item(key = "no-matches") { Text(labels.noMatches) }
        }
        items(visible, key = { it.id }) { entry ->
            ListItem(
                headlineContent = { Text(entry.name) },
                overlineContent = { Text("${labels.label(entry.action)} · ${entry.formattedTime(zone)}") },
                supportingContent = entry.propertySummary()?.let { summary -> { Text(summary) } },
            )
        }
    }
}

/** [AnalyticsDebugScreen] observing [store], whose clear action empties the store. */
@Composable
public fun AnalyticsDebugScreen(
    store: AnalyticsDebugStore,
    modifier: Modifier = Modifier,
    labels: AnalyticsDebugLabels = AnalyticsDebugLabels(),
) {
    val entries by store.entries.collectAsState()
    AnalyticsDebugScreen(entries = entries, onClear = store::clear, modifier = modifier, labels = labels)
}
