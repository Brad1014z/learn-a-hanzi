package io.github.brad1014z.hanzi.engine.social

import io.github.brad1014z.hanzi.engine.progress.CharacterProgress

/**
 * Sync merge rules (spec 12), as pure functions — the trustworthy core of backup and
 * multi-device sync. The Firestore plumbing calls these; tests pin the semantics.
 */
object SyncMerge {

    /**
     * Per character, the record with the latest `lastReviewedAt` wins (spec 12).
     * A never-reviewed record loses to any reviewed one; ties keep local.
     */
    fun mergeProgress(
        local: Map<String, CharacterProgress>,
        remote: Map<String, CharacterProgress>,
    ): Map<String, CharacterProgress> {
        val merged = HashMap<String, CharacterProgress>(local.size + remote.size)
        merged.putAll(local)
        for ((char, theirs) in remote) {
            val mine = merged[char]
            if (mine == null || (theirs.lastReviewedAt ?: -1) > (mine.lastReviewedAt ?: -1)) {
                merged[char] = theirs
            }
        }
        return merged
    }

    /**
     * ReviewLog is an append-only set union keyed by client-generated UUID (spec 12).
     * Returns the remote-only entries to insert locally (local-only ones are the
     * outbox's job to upload).
     */
    fun logEntriesToInsert(localUuids: Set<String>, remoteUuids: Set<String>): Set<String> =
        remoteUuids - localUuids

    /** XP backup: totals never go down through sync (effort is hard to lose, spec 10). */
    fun mergeXpTotal(local: Int, remote: Int): Int = maxOf(local, remote)
}

/** One queued upload (PR #1's outbox design, spec 12): drained on connectivity. */
data class OutboxItem(
    val uuid: String, // client-generated, idempotent retries
    val kind: String, // "progress" | "reviewLog" | "xp"
    val payload: String, // JSON, interpreted by kind
    val createdAt: Long,
)

interface SyncRepository {
    /** Drain queued local changes to the backend. Safe to call anytime; no-op offline/signed-out. */
    suspend fun drainOutbox()

    /**
     * Pull the remote backup and fold it into local state using [SyncMerge] rules.
     * Called after sign-in (restore) and periodically. Never deletes local data.
     */
    suspend fun restore()
}
