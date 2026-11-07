package com.gomobile.luna.data

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await

/**
 * All reads and writes against Firebase.
 *
 * Firestore layout (written by tools/import_excel.py):
 *   users/{uid}        name, email, role ("admin" | "manager")
 *   shops/{shopCode}   one document per shop with its figures
 *   meta/report        asOf, periodLabel, sourceFile
 *   broadcasts/{id}    title, body, author, createdAt
 */
class Repository {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    /** False when the app was built without app/google-services.json. */
    fun isConfigured(context: android.content.Context): Boolean =
        FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null

    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() = auth.signOut()

    /** Returns null when the account exists in Auth but the admin has not added it to users/. */
    suspend fun loadProfile(uid: String): UserProfile? {
        val doc = db.collection("users").document(uid).get().await()
        if (!doc.exists()) return null
        return UserProfile(
            uid = uid,
            email = doc.getString("email") ?: auth.currentUser?.email.orEmpty(),
            name = doc.getString("name").orEmpty().trim(),
            role = Role.from(doc.getString("role")),
        )
    }

    /**
     * Admins see every shop. Managers see shops where they are the Area Manager
     * or the Regional Manager; Firestore rules enforce the same split.
     */
    fun shops(profile: UserProfile): Flow<List<Shop>> {
        val shops = db.collection("shops")
        if (profile.isAdmin) return shops.asFlow()
        val asArea = shops.whereEqualTo("areaManager", profile.name).asFlow()
        val asRegional = shops.whereEqualTo("regionalManager", profile.name).asFlow()
        return combine(asArea, asRegional) { a, b -> (a + b).distinctBy { it.code } }
    }

    fun reportMeta(): Flow<ReportMeta?> = callbackFlow {
        val reg = db.collection("meta").document("report").addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            trySend(
                snap?.takeIf { it.exists() }?.let {
                    ReportMeta(
                        asOf = it.getTimestamp("asOf")?.toDate(),
                        periodLabel = it.getString("periodLabel").orEmpty(),
                        sourceFile = it.getString("sourceFile").orEmpty(),
                    )
                }
            )
        }
        awaitClose { reg.remove() }
    }

    fun broadcasts(): Flow<List<Broadcast>> = callbackFlow {
        val reg = db.collection("broadcasts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents.orEmpty().map { d ->
                    Broadcast(
                        id = d.id,
                        title = d.getString("title").orEmpty(),
                        body = d.getString("body").orEmpty(),
                        author = d.getString("author").orEmpty(),
                        createdAt = d.getTimestamp("createdAt")?.toDate(),
                    )
                })
            }
        awaitClose { reg.remove() }
    }

    suspend fun sendBroadcast(author: UserProfile, title: String, body: String) {
        db.collection("broadcasts").add(
            mapOf(
                "title" to title.trim(),
                "body" to body.trim(),
                "author" to author.name.ifBlank { author.email },
                "authorUid" to author.uid,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    suspend fun deleteBroadcast(id: String) {
        db.collection("broadcasts").document(id).delete().await()
    }

    private fun Query.asFlow(): Flow<List<Shop>> = callbackFlow {
        val reg = addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            trySend(snap?.documents.orEmpty().map { it.toShop() })
        }
        awaitClose { reg.remove() }
    }
}

private fun DocumentSnapshot.long(field: String): Long = (get(field) as? Number)?.toLong() ?: 0L
private fun DocumentSnapshot.double(field: String): Double = (get(field) as? Number)?.toDouble() ?: 0.0
private fun DocumentSnapshot.bool(field: String): Boolean = when (val v = get(field)) {
    is Boolean -> v
    is Number -> v.toInt() != 0
    else -> false
}

private fun DocumentSnapshot.toShop() = Shop(
    code = id,
    name = getString("name").orEmpty(),
    areaManager = getString("areaManager").orEmpty(),
    regionalManager = getString("regionalManager").orEmpty(),
    region = getString("region").orEmpty(),
    kaLevel = getString("kaLevel").orEmpty(),
    promoter = getString("promoter").orEmpty(),
    stock = long("stock"),
    st = long("st"),
    so = long("so"),
    stTarget = long("stTarget"),
    soTarget = long("soTarget"),
    avgSo = double("avgSo"),
    groupASt = long("groupASt"),
    groupBSt = long("groupBSt"),
    groupASo = long("groupASo"),
    groupBSo = long("groupBSo"),
    zeroSo3 = bool("zeroSo3"),
    zeroSo6 = bool("zeroSo6"),
    zeroSo10 = bool("zeroSo10"),
    dailySo = (get("dailySo") as? List<*>).orEmpty().map { (it as? Number)?.toLong() ?: 0L },
)
