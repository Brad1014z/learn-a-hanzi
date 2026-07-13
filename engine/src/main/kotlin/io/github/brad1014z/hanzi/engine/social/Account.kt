package io.github.brad1014z.hanzi.engine.social

import kotlinx.coroutines.flow.Flow

/**
 * The optional account layer (spec 12): sign-in unlocks sync, friends, and the weekly
 * board — and nothing else. Interfaces live in the pure engine (spec 06); Firebase
 * implementations live in :app, fakes exist so every social surface works offline.
 */
data class Profile(
    val uid: String,
    val displayName: String, // generated two-word name, re-rollable (spec 12)
    val avatarId: Int, // index into the preset avatar set
    val friendCode: String,
)

sealed interface AccountState {
    /** Everything works signed out, forever (constitution principle 1/4). */
    data object SignedOut : AccountState

    data class SignedIn(val profile: Profile) : AccountState
}

interface AccountRepository {
    val state: Flow<AccountState>

    /**
     * Launch the platform sign-in (Credential Manager → Firebase Auth). Creates the
     * profile (generated name + random avatar + friend code) on first sign-in.
     * Returns null on user-cancel or failure — never throws into UI.
     */
    suspend fun signIn(): Profile?

    /** Local sign-out: server data stays, device keeps working signed out (spec 12). */
    suspend fun signOut()

    suspend fun rerollDisplayName(): Profile?

    suspend fun setAvatar(avatarId: Int): Profile?

    /** Rotate the friend code (spec 12: codes are rotatable). */
    suspend fun rotateFriendCode(): Profile?
}
