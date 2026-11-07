package com.gomobile.luna.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gomobile.luna.data.ReportMeta
import com.gomobile.luna.data.Shop
import com.gomobile.luna.data.Totals
import com.gomobile.luna.data.UserProfile
import com.gomobile.luna.ui.achievementColor
import com.gomobile.luna.ui.components.DailyBars
import com.gomobile.luna.ui.components.SectionTitle
import com.gomobile.luna.ui.components.StatCard
import com.gomobile.luna.ui.components.TargetCard
import com.gomobile.luna.ui.fmt
import com.gomobile.luna.ui.fmtDateTime
import com.gomobile.luna.ui.pct
import com.gomobile.luna.ui.pctOrDash
import com.gomobile.luna.ui.theme.StatusColors

@Composable
fun DashboardScreen(
    profile: UserProfile,
    shops: List<Shop>,
    loaded: Boolean,
    meta: ReportMeta?,
    onOpenManager: (String) -> Unit,
    onOpenAging: () -> Unit,
) {
    val totals = remember(shops) { Totals.of(shops) }
    // Admins and regional managers see a row per Area Manager under them.
    val byManager = remember(shops) {
        shops.groupBy { it.areaManager }
            .map { (name, list) -> name to Totals.of(list) }
            .sortedByDescending { it.second.so }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text("Hello ${profile.name.ifBlank { profile.email }}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                val period = meta?.periodLabel?.takeIf { it.isNotBlank() }?.let { "$it · " }.orEmpty()
                Text(
                    "${period}Updated ${meta?.asOf.fmtDateTime()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (loaded && shops.isEmpty()) {
            item {
                Text(
                    if (profile.isAdmin) "No shops yet. Run tools/import_excel.py to upload the latest Excel."
                    else "No shops are assigned to \"${profile.name}\" in the latest report. If that's wrong, tell the admin.",
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            return@LazyColumn
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TargetCard("Sell-through (ST)", totals.st.fmt(), totals.stTarget.fmt(), totals.stAchievement, Modifier.weight(1f))
                TargetCard("Sell-out (SO)", totals.so.fmt(), totals.soTarget.fmt(), totals.soAchievement, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Stock", totals.stock.fmt(), Modifier.weight(1f), caption = "${totals.shops.fmt()} shops")
                StatCard("Convert rate", totals.convertRate.pct(), Modifier.weight(1f), caption = "SO ÷ ST")
            }
        }
        item { SectionTitle("Stock alerts & aging") }
        item {
            Card(
                Modifier.fillMaxWidth().clickable(onClick = onOpenAging),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AlertRow("Zero stock", totals.zeroStock, totals.shops)
                    AlertRow("Low stock (1–2 pcs)", totals.lowStock, totals.shops)
                    HorizontalDivider()
                    AlertRow("No sell-out 3+ days", totals.zeroSo3, totals.shops)
                    AlertRow("No sell-out 6+ days", totals.zeroSo6, totals.shops)
                    AlertRow("No sell-out 10+ days", totals.zeroSo10, totals.shops)
                    Text("Tap to see these shops", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { SectionTitle("Daily sell-out") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                DailyBars(totals.dailySo, Modifier.padding(14.dp))
            }
        }
        if (byManager.size > 1) {
            item { SectionTitle("By Area Manager") }
            items(byManager.size) { i ->
                val (name, t) = byManager[i]
                ManagerRow(name, t) { onOpenManager(name) }
            }
        }
    }
}

@Composable
private fun AlertRow(label: String, count: Int, of: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        val ratio = if (of > 0) count.toDouble() / of else 0.0
        Text(
            "${count.fmt()}  (${ratio.pct()})",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (count == 0) StatusColors.good else if (ratio < 0.2) StatusColors.warn else StatusColors.bad,
        )
    }
}

@Composable
private fun ManagerRow(name: String, t: Totals, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name.ifBlank { "Unassigned" }, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${t.shops} shops", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Metric("ST", t.st.fmt(), t.stAchievement)
                Metric("SO", t.so.fmt(), t.soAchievement)
                Metric("Stock", t.stock.fmt(), null)
                Metric("No SO 6d+", t.zeroSo6.fmt(), null)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, ratio: Double?) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (ratio != null) {
                Text(" ${ratio.pctOrDash()}", style = MaterialTheme.typography.labelSmall, color = achievementColor(ratio))
            }
        }
    }
}
