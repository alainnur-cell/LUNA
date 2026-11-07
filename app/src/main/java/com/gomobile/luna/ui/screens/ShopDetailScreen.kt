package com.gomobile.luna.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gomobile.luna.data.Shop
import com.gomobile.luna.ui.color
import com.gomobile.luna.ui.components.Badge
import com.gomobile.luna.ui.components.DailyBars
import com.gomobile.luna.ui.components.SectionTitle
import com.gomobile.luna.ui.components.StatCard
import com.gomobile.luna.ui.components.TargetCard
import com.gomobile.luna.ui.fmt
import com.gomobile.luna.ui.pct
import com.gomobile.luna.ui.theme.StatusColors

@Composable
fun ShopDetailScreen(shop: Shop?) {
    if (shop == null) {
        Text("This shop is not in the latest report.", Modifier.padding(24.dp))
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column {
            Text(shop.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("${shop.code} · ${shop.region}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge(shop.aging.label, shop.aging.color())
                if (shop.kaLevel.isNotBlank()) Badge("KA ${shop.kaLevel}", MaterialTheme.colorScheme.primary)
                if (shop.stock <= 0) Badge("Zero stock", StatusColors.bad)
                else if (shop.stock <= 2) Badge("Low stock", StatusColors.warn)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TargetCard("Sell-through (ST)", shop.st.fmt(), shop.stTarget.fmt(), shop.stAchievement, Modifier.weight(1f))
            TargetCard("Sell-out (SO)", shop.so.fmt(), shop.soTarget.fmt(), shop.soAchievement, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Stock", shop.stock.fmt(), Modifier.weight(1f), caption = "PSI pcs")
            StatCard(
                "Stock cover",
                shop.stockCoverDays?.let { "${it.toInt()} days" } ?: "No sales",
                Modifier.weight(1f),
                caption = "At %.1f SO/day".format(shop.avgSo),
            )
        }

        SectionTitle("Daily sell-out")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            DailyBars(shop.dailySo, Modifier.padding(14.dp))
        }

        SectionTitle("Details")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Line("Convert rate (SO ÷ ST)", shop.convertRate.pct())
                Line("Group A  ST / SO", "${shop.groupASt.fmt()} / ${shop.groupASo.fmt()}")
                Line("Group B  ST / SO", "${shop.groupBSt.fmt()} / ${shop.groupBSo.fmt()}")
                HorizontalDivider()
                Line("No SO for 3 / 6 / 10 days", listOf(shop.zeroSo3, shop.zeroSo6, shop.zeroSo10).joinToString(" / ") { if (it) "Yes" else "No" })
                HorizontalDivider()
                Line("Area Manager", shop.areaManager)
                Line("Regional Manager", shop.regionalManager)
                Line("Promoter", shop.promoter.ifBlank { "–" })
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}
