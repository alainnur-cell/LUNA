package com.gomobile.luna.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gomobile.luna.data.Aging
import com.gomobile.luna.data.Shop
import com.gomobile.luna.ui.achievementColor
import com.gomobile.luna.ui.color
import com.gomobile.luna.ui.components.Badge
import com.gomobile.luna.ui.fmt
import com.gomobile.luna.ui.pctOrDash
import com.gomobile.luna.ui.theme.StatusColors

enum class ShopSort(val label: String) {
    SO("Top SO"),
    ST("Top ST"),
    STOCK("Most stock"),
    AGING("Aging"),
    SO_ACH("Lowest SO %"),
    NAME("Name"),
}

enum class ShopFilter(val label: String) {
    ALL("All"),
    NO_SALE("No SO 3d+"),
    ZERO_STOCK("Zero stock"),
    LOW_STOCK("Low stock"),
}

@Composable
fun ShopsScreen(
    shops: List<Shop>,
    managerFilter: String?,
    initialFilter: ShopFilter,
    onClearManager: () -> Unit,
    onOpenShop: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(if (initialFilter == ShopFilter.ALL) ShopSort.SO else ShopSort.AGING) }
    var filter by rememberSaveable { mutableStateOf(initialFilter) }

    val visible = remember(shops, query, sort, filter, managerFilter) {
        shops.asSequence()
            .filter { managerFilter == null || it.areaManager == managerFilter }
            .filter {
                when (filter) {
                    ShopFilter.ALL -> true
                    ShopFilter.NO_SALE -> it.zeroSo3
                    ShopFilter.ZERO_STOCK -> it.stock <= 0
                    ShopFilter.LOW_STOCK -> it.stock in 1..2
                }
            }
            .filter {
                query.isBlank() || it.name.contains(query, true) || it.code.contains(query, true) ||
                    it.areaManager.contains(query, true) || it.region.contains(query, true)
            }
            .sortedWith(
                when (sort) {
                    ShopSort.SO -> compareByDescending { it.so }
                    ShopSort.ST -> compareByDescending { it.st }
                    ShopSort.STOCK -> compareByDescending { it.stock }
                    ShopSort.AGING -> compareByDescending<Shop> { it.aging.rank }.thenByDescending { it.stock }
                    ShopSort.SO_ACH -> compareBy { it.soAchievement ?: Double.MAX_VALUE }
                    ShopSort.NAME -> compareBy { it.name }
                }
            )
            .toList()
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search shop, code, manager, region") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (managerFilter != null) {
                InputChip(
                    selected = true,
                    onClick = onClearManager,
                    label = { Text(managerFilter) },
                    trailingIcon = { Icon(Icons.Filled.Close, "Show all managers") },
                )
            }
            ShopFilter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sort", style = MaterialTheme.typography.labelMedium)
            ShopSort.entries.forEach { s ->
                FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(s.label) })
            }
        }
        Text(
            "${visible.size} shops",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visible, key = { it.code }) { shop -> ShopRow(shop) { onOpenShop(shop.code) } }
        }
    }
}

@Composable
private fun ShopRow(shop: Shop, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(shop.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${shop.code} · ${shop.kaLevel.ifBlank { "–" }} · ${shop.areaManager}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (shop.aging != Aging.SELLING) Badge(shop.aging.label, shop.aging.color())
            }
            Spacer(Modifier.width(4.dp))
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Figure("Stock", shop.stock.fmt(), if (shop.stock <= 0) StatusColors.bad else null)
                Figure("ST", shop.st.fmt(), null, shop.stAchievement)
                Figure("SO", shop.so.fmt(), null, shop.soAchievement)
                Figure("Avg SO/day", "%.1f".format(shop.avgSo), null)
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color?, ratio: Double? = null) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontWeight = FontWeight.Medium, color = valueColor ?: MaterialTheme.colorScheme.onSurface)
            if (ratio != null) Text(" ${ratio.pctOrDash()}", style = MaterialTheme.typography.labelSmall, color = achievementColor(ratio))
        }
    }
}
