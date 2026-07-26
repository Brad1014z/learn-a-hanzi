package io.github.brad1014z.hanzi.cloud

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import io.github.brad1014z.hanzi.data.HanziDatabase
import io.github.brad1014z.hanzi.data.MetaEntity
import io.github.brad1014z.hanzi.data.Outbox
import io.github.brad1014z.hanzi.data.ReviewLogEntity
import io.github.brad1014z.hanzi.data.RoomProgressRepository
import io.github.brad1014z.hanzi.engine.progress.CharacterProgress
import io.github.brad1014z.hanzi.engine.progress.SrsState
import io.github.brad1014z.hanzi.engine.social.AccountRepository
import io.github.brad1014z.hanzi.engine.social.AccountState
import io.github.brad1014z.hanzi.engine.social.BoardEntry
import io.github.brad1014z.hanzi.engine.social.Friend
import io.github.brad1014z.hanzi.engine.social.Identity
import io.github.brad1014z.hanzi.engine.social.Profile
import io.github.brad1014z.hanzi.engine.social.SocialRepository
import io.github.brad1014z.hanzi.engine.social.SyncMerge
import io.github.brad1014z.hanzi.engine.social.SyncRepository
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** Small await() so we don't pull in another coroutines artifact. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val e = task.exception
        when {
            e != null -> cont.resumeWithException(e)
            task.isCanceled -> cont.cancel()
            else -> cont.resume(task.result)
        }
    }
}

/**
 * Google sign-in (Credential Manager → Firebase Auth, spec 01/12) + the generated
 * profile (name/avatar/friend code — spec 12: the Google identity never shows to
 * other users; only the generated profile does).
 */
class FirebaseAccountRepository(
    private val context: Context,
    private val activity: () -> Activity?,
) : AccountRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val stateFlow = MutableStateFlow<AccountState>(AccountState.SignedOut)

    init {
        auth.addAuthStateListener { a ->
            val uid = a.currentUser?.uid
            if (uid == null) {
                stateFlow.value = AccountState.SignedOut
            } else {
                users(uid).addSnapshotListener { snap, _ ->
                    val p = snap?.toProfile(uid)
                    if (p != null) stateFlow.value = AccountState.SignedIn(p)
                }
            }
        }
    }

    override val state: Flow<AccountState> = stateFlow

    override suspend fun signIn(): Profile? {
        val host = activity() ?: return null
        val webClientId = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName,
        ).takeIf { it != 0 }?.let(context::getString) ?: return null
        return runCatching {
            val credential = CredentialManager.create(host).getCredential(
                host,
                GetCredentialRequest.Builder()
                    .addCredentialOption(
                        GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(webClientId)
                            .build(),
                    )
                    .build(),
            ).credential
            check(
                credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            ) { "unexpected credential type" }
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val uid = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .await().user!!.uid
            ensureProfile(uid)
        }.getOrNull() // user-cancel / no network: calm no-op (spec 12)
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun rerollDisplayName(): Profile? = updateProfile { profile ->
        users(profile.uid).update("displayName", Identity.generateName()).await()
    }

    override suspend fun setAvatar(avatarId: Int): Profile? = updateProfile { profile ->
        users(profile.uid).update("avatarId", avatarId).await()
    }

    override suspend fun rotateFriendCode(): Profile? = updateProfile { profile ->
        val newCode = Identity.generateFriendCode()
        db.batch()
            .delete(db.collection("friendCodes").document(profile.friendCode))
            .set(db.collection("friendCodes").document(newCode), mapOf("uid" to profile.uid))
            .update(users(profile.uid), "friendCode", newCode)
            .commit().await()
    }

    private suspend fun ensureProfile(uid: String): Profile {
        val existing = users(uid).get().await().toProfile(uid)
        if (existing != null) return existing
        val profile = Profile(
            uid = uid,
            displayName = Identity.generateName(),
            avatarId = Identity.randomAvatarId(),
            friendCode = Identity.generateFriendCode(),
        )
        db.batch()
            .set(
                users(uid),
                mapOf(
                    "displayName" to profile.displayName,
                    "avatarId" to profile.avatarId,
                    "friendCode" to profile.friendCode,
                    "createdAt" to System.currentTimeMillis(),
                ),
            )
            .set(db.collection("friendCodes").document(profile.friendCode), mapOf("uid" to uid))
            .commit().await()
        return profile
    }

    private suspend fun updateProfile(block: suspend (Profile) -> Unit): Profile? {
        val profile = (stateFlow.value as? AccountState.SignedIn)?.profile ?: return null
        runCatching { block(profile) }.getOrElse { return null }
        return users(profile.uid).get().await().toProfile(profile.uid)
    }

    private fun users(uid: String) = db.collection("users").document(uid)
}

private fun com.google.firebase.firestore.DocumentSnapshot?.toProfile(uid: String): Profile? {
    val name = this?.getString("displayName") ?: return null
    return Profile(
        uid = uid,
        displayName = name,
        avatarId = (getLong("avatarId") ?: 0L).toInt(),
        friendCode = getString("friendCode") ?: "",
    )
}

/** Friends by mutual code + the weekly board (spec 12, free-tier slice: no functions). */
class FirestoreSocialRepository : SocialRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private fun me(): String? = auth.currentUser?.uid

    private fun edgeId(a: String, b: String) =
        if (a < b) "${a}_$b" else "${b}_$a"

    override val friends: Flow<List<Friend>> = callbackFlow {
        val uid = me() ?: run { trySend(emptyList()); awaitClose { }; return@callbackFlow }
        // otherUid → (pending, requestedBy); one map slice per query field.
        val slices = mutableMapOf<String, Map<String, Pair<Boolean, String>>>()
        val listeners = mutableListOf<ListenerRegistration>()
        fun push() {
            launch {
                val merged = slices.values.flatMap { it.entries }.associate { it.key to it.value }
                trySend(
                    merged.entries.map { (other, meta) ->
                        val snap = db.collection("users").document(other).get().await()
                        Friend(
                            uid = other,
                            displayName = snap.getString("displayName") ?: "…",
                            avatarId = (snap.getLong("avatarId") ?: 0L).toInt(),
                            weeklyXp = 0, // the board owns XP display
                            pending = meta.first,
                        )
                    },
                )
            }
        }
        for (field in listOf("uidA", "uidB")) {
            listeners += db.collection("friendEdges").whereEqualTo(field, uid)
                .addSnapshotListener { snap, _ ->
                    if (snap == null) return@addSnapshotListener
                    slices[field] = snap.documents.associate { d ->
                        val other = if (d.getString("uidA") == uid) d.getString("uidB")!! else d.getString("uidA")!!
                        other to Pair(d.get("confirmedAt") == null, d.getString("requestedBy") ?: "")
                    }
                    push()
                }
        }
        awaitClose { listeners.forEach { it.remove() } }
    }

    override suspend fun addFriendByCode(code: String): String? {
        val uid = me() ?: return null
        val mapping = db.collection("friendCodes").document(code).get().await()
        val other = mapping.getString("uid") ?: return null
        if (other == uid) return null
        // Edge first: the rules let us read the profile only once an edge exists.
        db.collection("friendEdges").document(edgeId(uid, other)).set(
            mapOf(
                "uidA" to minOf(uid, other),
                "uidB" to maxOf(uid, other),
                "requestedBy" to uid,
                "confirmedAt" to null,
            ),
            SetOptions.merge(),
        ).await()
        val profile = db.collection("users").document(other).get().await()
        return profile.getString("displayName")
    }

    override suspend fun confirmFriend(uid: String) {
        val myUid = me() ?: return
        db.collection("friendEdges").document(edgeId(myUid, uid))
            .update("confirmedAt", System.currentTimeMillis()).await()
    }

    override suspend fun removeFriend(uid: String) {
        val myUid = me() ?: return
        db.collection("friendEdges").document(edgeId(myUid, uid)).delete().await()
    }

    override fun weeklyBoard(weekId: String): Flow<List<BoardEntry>> = callbackFlow {
        val uid = me() ?: run { trySend(emptyList()); awaitClose { }; return@callbackFlow }
        val xpListeners = mutableMapOf<String, ListenerRegistration>()
        val xp = mutableMapOf<String, Int>()
        val names = mutableMapOf<String, Pair<String, Int>>() // uid → (name, avatar)
        var members = setOf(uid)

        fun emitBoard() {
            trySend(
                members.mapNotNull { m ->
                    val (name, avatar) = names[m] ?: return@mapNotNull null
                    BoardEntry(name, avatar, xp[m] ?: 0, isMe = m == uid)
                }.sortedByDescending { it.weeklyXp },
            )
        }

        fun watch(m: String) {
            if (xpListeners.containsKey(m)) return
            db.collection("users").document(m).get().addOnSuccessListener { snap ->
                names[m] = (snap.getString("displayName") ?: "…") to (snap.getLong("avatarId") ?: 0L).toInt()
                emitBoard()
            }
            xpListeners[m] = db.collection("users").document(m)
                .collection("weeklyXp").document(weekId)
                .addSnapshotListener { snap, _ ->
                    xp[m] = (snap?.getLong("xp") ?: 0L).toInt()
                    emitBoard()
                }
        }

        watch(uid)
        val edges = db.collection("friendEdges")
        val edgeListeners = listOf("uidA", "uidB").map { field ->
            edges.whereEqualTo(field, uid).addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val confirmed = snap.documents.filter { it.get("confirmedAt") != null }.map { d ->
                    if (d.getString("uidA") == uid) d.getString("uidB")!! else d.getString("uidA")!!
                }
                members = members + confirmed
                confirmed.forEach(::watch)
                emitBoard()
            }
        }
        awaitClose {
            xpListeners.values.forEach { it.remove() }
            edgeListeners.forEach { it.remove() }
        }
    }

    override suspend fun publishWeeklyXp(weekId: String, totalXp: Int) {
        val uid = me() ?: return
        db.collection("users").document(uid).collection("weeklyXp").document(weekId)
            .set(mapOf("xp" to totalXp)).await()
    }
}

/** Backup/sync over the outbox + restore-with-merge (spec 12, rules in SyncMerge). */
class FirestoreSyncRepository(private val room: HanziDatabase) : SyncRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override suspend fun drainOutbox() {
        val uid = auth.currentUser?.uid ?: return
        val outbox = room.outboxDao()
        while (true) {
            val items = outbox.oldest(100)
            if (items.isEmpty()) return
            val batch = db.batch()
            val user = db.collection("users").document(uid)
            for (item in items) {
                when (item.kind) {
                    Outbox.KIND_REVIEW_LOG -> {
                        val p = Outbox.json.decodeFromString<Outbox.ReviewLogPayload>(item.payload)
                        batch.set(
                            user.collection("reviewLog").document(p.uuid),
                            mapOf(
                                "character" to p.character, "reviewedAt" to p.reviewedAt,
                                "grade" to p.grade, "drawnCorrectly" to p.drawnCorrectly,
                                "durationMs" to p.durationMs, "session" to p.session,
                            ),
                        )
                    }
                    Outbox.KIND_PROGRESS -> {
                        val p = Outbox.json.decodeFromString<Outbox.ProgressPayload>(item.payload)
                        batch.set(
                            user.collection("progress").document(p.character),
                            mapOf(
                                "state" to p.state, "dueAt" to p.dueAt,
                                "intervalDays" to p.intervalDays, "ease" to p.ease,
                                "reps" to p.reps, "lapses" to p.lapses,
                                "lastReviewedAt" to p.lastReviewedAt, "lastGrade" to p.lastGrade,
                            ),
                        )
                    }
                    Outbox.KIND_XP -> {
                        val p = Outbox.json.decodeFromString<Outbox.XpPayload>(item.payload)
                        batch.set(user, mapOf("xpTotal" to p.total), SetOptions.merge())
                        batch.set(
                            user.collection("weeklyXp").document(p.weekId),
                            mapOf("xp" to p.weekXp),
                        )
                    }
                }
            }
            batch.commit().await()
            outbox.delete(items.map { it.uuid })
        }
    }

    override suspend fun restore() {
        val uid = auth.currentUser?.uid ?: return
        val user = db.collection("users").document(uid)
        val progressRepo = RoomProgressRepository(room)

        // Progress: latest-reviewed wins (SyncMerge).
        val remote = user.collection("progress").get().await().documents.associate { d ->
            d.id to CharacterProgress(
                character = d.id,
                state = runCatching { SrsState.valueOf(d.getString("state") ?: "") }
                    .getOrDefault(SrsState.LEARNING),
                dueAt = d.getLong("dueAt") ?: 0L,
                intervalDays = d.getDouble("intervalDays") ?: 0.0,
                ease = d.getDouble("ease") ?: CharacterProgress.INITIAL_EASE,
                reps = (d.getLong("reps") ?: 0L).toInt(),
                lapses = (d.getLong("lapses") ?: 0L).toInt(),
                lastReviewedAt = d.getLong("lastReviewedAt"),
                lastGrade = d.getLong("lastGrade")?.toInt(),
            )
        }
        val local = progressRepo.allProgress()
        val merged = SyncMerge.mergeProgress(local, remote)
        for ((char, progress) in merged) {
            if (progress !== local[char]) progressRepo.upsert(progress)
        }

        // ReviewLog: set union by uuid.
        val remoteLogs = user.collection("reviewLog").get().await().documents
        val missing = SyncMerge.logEntriesToInsert(
            localUuids = room.progressDao().logUuids().toSet(),
            remoteUuids = remoteLogs.map { it.id }.toSet(),
        )
        for (d in remoteLogs) {
            if (d.id !in missing) continue
            room.progressDao().insertLogIgnore(
                ReviewLogEntity(
                    character = d.getString("character") ?: continue,
                    reviewedAt = d.getLong("reviewedAt") ?: 0L,
                    grade = (d.getLong("grade") ?: 0L).toInt(),
                    drawnCorrectly = d.getBoolean("drawnCorrectly") ?: false,
                    durationMs = d.getLong("durationMs"),
                    session = d.getString("session"),
                    uuid = d.id,
                ),
            )
        }

        // XP: never goes down through sync.
        val remoteXp = (user.get().await().getLong("xpTotal") ?: 0L).toInt()
        val localXp = progressRepo.xpTotal()
        val mergedXp = SyncMerge.mergeXpTotal(localXp, remoteXp)
        if (mergedXp != localXp) {
            room.metaDao().put(MetaEntity(RoomProgressRepository.XP_KEY, mergedXp.toString()))
        }
    }
}
