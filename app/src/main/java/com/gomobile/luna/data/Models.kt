package com.gomobile.luna.data

import java.util.Date

/** A signed-in team member, stored at users/{uid}. */
data class UserProfile(
    val uid: String,
    val email: String,
    /** Exactly as it appears in the Excel "Area Manager" / "Regional Manager" columns. */
    val name: String,
    val role: Role,
) {
    val isAdmin: Boolean get() = role == Role.ADMIN
}

enum class Role {
    ADMIN, MANAGER;

    companion object {
        fun from(value: String?): Role =
            if (value.equals("admin", ignoreCase = true)) ADMIN else MANAGER
    }
}

/** One shop's figures from the latest import, stored at shops/{shopCode}. */
data class Shop(
    val code: String,
    val name: String,
    val areaManager: String,
    val regionalManager: String,
    val region: String,
    val kaLevel: String,
    val promoter: String,
    /** PSI stock on hand (pcs). */
    val stock: Long,
    /** Sell-through this month (pcs). */
    val st: Long,
    /** Sell-out this month (pcs). */
    val so: Long,
    val stTarget: Long,
    val soTarget: Long,
    val avgSo: Double,
    val groupASt: Long,
    val groupBSt: Long,
    val groupASo: Long,
    val groupBSo: Long,
    /** Shop had no sell-out for the last 3 / 6 / 10 days. */
    val zeroSo3: Boolean,
    val zeroSo6: Boolean,
    val zeroSo10: Boolean,
    /** Sell-out per day of the month, day 1 first. */
    val dailySo: List<Long>,
) {
    /** SO / ST, as in the "CONVERT RATE" column. */
    val convertRate: Double get() = if (st > 0) so.toDouble() / st else 0.0
    val stAchievement: Double? get() = if (stTarget > 0) st.toDouble() / stTarget else null
    val soAchievement: Double? get() = if (soTarget > 0) so.toDouble() / soTarget else null

    /** Days the current stock lasts at the average daily sell-out. Null when nothing sells. */
    val stockCoverDays: Double? get() = if (avgSo > 0) stock / avgSo else null

    val aging: Aging
        get() = when {
            zeroSo10 -> Aging.NO_SALE_10
            zeroSo6 -> Aging.NO_SALE_6
            zeroSo3 -> Aging.NO_SALE_3
            else -> Aging.SELLING
        }
}

enum class Aging(val label: String, val rank: Int) {
    SELLING("Selling", 0),
    NO_SALE_3("No SO 3+ days", 1),
    NO_SALE_6("No SO 6+ days", 2),
    NO_SALE_10("No SO 10+ days", 3),
}

/** Details of the latest import, stored at meta/report. */
data class ReportMeta(
    val asOf: Date?,
    val periodLabel: String,
    val sourceFile: String,
)

/** A message from the admin to the whole team, stored in broadcasts/. */
data class Broadcast(
    val id: String,
    val title: String,
    val body: String,
    val author: String,
    val createdAt: Date?,
)

/** Sums over a set of shops. */
data class Totals(
    val shops: Int,
    val stock: Long,
    val st: Long,
    val so: Long,
    val stTarget: Long,
    val soTarget: Long,
    val zeroStock: Int,
    val lowStock: Int,
    val zeroSo3: Int,
    val zeroSo6: Int,
    val zeroSo10: Int,
    val dailySo: List<Long>,
) {
    val stAchievement: Double? get() = if (stTarget > 0) st.toDouble() / stTarget else null
    val soAchievement: Double? get() = if (soTarget > 0) so.toDouble() / soTarget else null
    val convertRate: Double get() = if (st > 0) so.toDouble() / st else 0.0

    companion object {
        fun of(shops: List<Shop>): Totals {
            val days = shops.maxOfOrNull { it.dailySo.size } ?: 0
            val daily = List(days) { d -> shops.sumOf { it.dailySo.getOrElse(d) { 0L } } }
            return Totals(
                shops = shops.size,
                stock = shops.sumOf { it.stock },
                st = shops.sumOf { it.st },
                so = shops.sumOf { it.so },
                stTarget = shops.sumOf { it.stTarget },
                soTarget = shops.sumOf { it.soTarget },
                zeroStock = shops.count { it.stock <= 0 },
                lowStock = shops.count { it.stock in 1..2 },
                zeroSo3 = shops.count { it.zeroSo3 },
                zeroSo6 = shops.count { it.zeroSo6 },
                zeroSo10 = shops.count { it.zeroSo10 },
                dailySo = daily,
            )
        }
    }
}
