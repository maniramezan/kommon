package io.github.maniramezan.kommon.debugui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.maniramezan.kmpcomponents.ContentRow
import io.github.maniramezan.kmpcomponents.CountBadge
import io.github.maniramezan.kmpcomponents.EmptyState
import io.github.maniramezan.kmpcomponents.SearchField
import io.github.maniramezan.kmpcomponents.SearchFieldLabels
import io.github.maniramezan.kmpcomponents.SectionHeader
import io.github.maniramezan.kmpcomponents.StatusTone
import io.github.maniramezan.kmpcomponents.TabChip
import io.github.maniramezan.kmpcomponents.TabChipRow
import io.github.maniramezan.kmpcomponents.TonalBadge
import io.github.maniramezan.kmpcomponents.kmpSpacing
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
                actionLabel = labels.clear.takeIf { entries.isNotEmpty() },
                onAction = onClear.takeIf { entries.isNotEmpty() },
                actions = {
                    CountBadge(count = entries.size, contentDescription = "${entries.size} ${labels.entryCount}")
                },
            )
        }
        if (entries.isEmpty()) {
            item(key = "empty") { EmptyState(title = labels.empty) }
            return@LazyColumn
        }
        item(key = "search") {
            SearchField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                labels = SearchFieldLabels(placeholder = labels.searchPlaceholder, clear = labels.clearSearch),
            )
        }
        item(key = "filters") {
            TabChipRow {
                TabChip(label = labels.allActions, selected = action == null, onClick = { action = null })
                actions.forEach { candidate ->
                    TabChip(label = labels.label(candidate), selected = action == candidate, onClick = { action = candidate })
                }
            }
        }
        if (visible.isEmpty()) {
            item(key = "no-matches") { EmptyState(title = labels.noMatches) }
        }
        items(visible, key = { it.id }) { entry ->
            ContentRow(
                title = entry.name,
                secondaryText = entry.formattedTime(zone),
                supportingText = entry.propertySummary(),
                leadingContent = { TonalBadge(text = labels.label(entry.action), tone = StatusTone.INFO) },
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
