package io.github.brad1014z.hanzi.cloud

import io.github.brad1014z.hanzi.data.HanziDatabase
import io.github.brad1014z.hanzi.data.MetaEntity
import io.github.brad1014z.hanzi.engine.social.AccountRepository
import io.github.brad1014z.hanzi.engine.social.AccountState
import io.github.brad1014z.hanzi.engine.social.BoardEntry
import io.github.brad1014z.hanzi.engine.social.Friend
import io.github.brad1014z.hanzi.engine.social.Identity
import io.github.brad1014z.hanzi.engine.social.Profile
import io.github.brad1014z.hanzi.engine.social.SocialRepository
import io.github.brad1014z.hanzi.engine.social.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json

/**
 * Offline fakes for the whole cloud layer (spec 12 requires them for tests; they also
 * power "preview mode" when no Firebase project is configured — the family can feel
 * the flows before any console setup). The profile survives restarts via Meta; friends
 * are clearly-labeled demo companions, never real people.
 */
class FakeCloud(private val db: HanziDatabase) {

    private val json = Json { ignoreUnknownKeys = true }
    private val stateFlow = MutableStateFlow<AccountState>(AccountState.SignedOut)
    private val friendsFlow = MutableStateFlow(demoFriends())
    private val myWeekXp = MutableStateFlow(0)

    private fun demoFriends() = listOf(
        Friend(uid = "demo-1", displayName = "Demo Panda", avatarId = 0, weeklyXp = 85),
        Friend(uid = "demo-2", displayName = "Demo Dragon", avatarId = 3, weeklyXp = 40),
    )

    val account: AccountRepository = object : AccountRepository {
        override val state: Flow<AccountState> = stateFlow

        override suspend fun signIn(): Profile? {
            val saved = db.metaDao().get(META_PROFILE)?.let {
                runCatching { json.decodeFromString<StoredProfile>(it) }.getOrNull()
            }
            val profile = saved?.toProfile() ?: Profile(
                uid = "preview-me",
                displayName = Identity.generateName(),
                avatarId = Identity.randomAvatarId(),
                friendCode = Identity.generateFriendCode(),
            ).also { persist(it) }
            stateFlow.value = AccountState.SignedIn(profile)
            return profile
        }

        override suspend fun signOut() {
            stateFlow.value = AccountState.SignedOut
        }

        override suspend fun rerollDisplayName() =
            update { it.copy(displayName = Identity.generateName()) }

        override suspend fun setAvatar(avatarId: Int) = update { it.copy(avatarId = avatarId) }

        override suspend fun rotateFriendCode() =
            update { it.copy(friendCode = Identity.generateFriendCode()) }

        private suspend fun update(transform: (Profile) -> Profile): Profile? {
            val current = (stateFlow.value as? AccountState.SignedIn)?.profile ?: return null
            val next = transform(current)
            persist(next)
            stateFlow.value = AccountState.SignedIn(next)
            return next
        }
    }

    val social: SocialRepository = object : SocialRepository {
        override val friends: Flow<List<Friend>> = friendsFlow

        override suspend fun addFriendByCode(code: String): String? {
            if (!Identity.isValidCode(code)) return null
            val name = "Demo ${Identity.ANIMALS.random()}"
            friendsFlow.value = friendsFlow.value +
                Friend(uid = "demo-${code.lowercase()}", displayName = name, avatarId = 5, weeklyXp = 0, pending = true)
            return name
        }

        override suspend fun confirmFriend(uid: String) {
            friendsFlow.value = friendsFlow.value.map {
                if (it.uid == uid) it.copy(pending = false) else it
            }
        }

        override suspend fun removeFriend(uid: String) {
            friendsFlow.value = friendsFlow.value.filterNot { it.uid == uid }
        }

        override fun weeklyBoard(weekId: String): Flow<List<BoardEntry>> =
            combine(stateFlow, friendsFlow, myWeekXp) { state, friends, mine ->
                val me = (state as? AccountState.SignedIn)?.profile ?: return@combine emptyList()
                (friends.filterNot { it.pending }
                    .map { BoardEntry(it.displayName, it.avatarId, it.weeklyXp, isMe = false) } +
                    BoardEntry(me.displayName, me.avatarId, mine, isMe = true))
                    .sortedByDescending { it.weeklyXp }
            }

        override suspend fun publishWeeklyXp(weekId: String, totalXp: Int) {
            myWeekXp.value = totalXp
        }
    }

    val sync: SyncRepository = object : SyncRepository {
        override suspend fun drainOutbox() {
            // Preview mode: pretend the upload happened so the outbox stays tidy.
            val outbox = db.outboxDao()
            while (true) {
                val batch = outbox.oldest(100)
                if (batch.isEmpty()) break
                outbox.delete(batch.map { it.uuid })
            }
        }

        override suspend fun restore() = Unit // nothing remote to restore from
    }

    private suspend fun persist(profile: Profile) {
        db.metaDao().put(
            MetaEntity(META_PROFILE, json.encodeToString(StoredProfile.from(profile))),
        )
    }

    @kotlinx.serialization.Serializable
    private data class StoredProfile(
        val uid: String, val displayName: String, val avatarId: Int, val friendCode: String,
    ) {
        fun toProfile() = Profile(uid, displayName, avatarId, friendCode)

        companion object {
            fun from(p: Profile) = StoredProfile(p.uid, p.displayName, p.avatarId, p.friendCode)
        }
    }

    companion object {
        const val META_PROFILE = "previewProfile"
    }
}
