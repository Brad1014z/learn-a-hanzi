package io.github.brad1014z.hanzi.engine.social

import kotlin.random.Random

/**
 * Generated, safe identity (spec 12): a two-word display name with unlimited re-rolls
 * plus a preset avatar — no custom text names, no custom images, which closes the
 * name-moderation problem for an all-ages app. Pure and deterministic under a seed so
 * it's testable; the app passes real randomness.
 */
object Identity {

    // Kid-friendly, all-ages word lists. Naming tone is the co-designer's turf (spec 11);
    // these are placeholders he can swap wholesale.
    val ADJECTIVES = listOf(
        "Swift", "Brave", "Clever", "Sunny", "Mighty", "Gentle", "Lucky", "Cosmic",
        "Turbo", "Golden", "Silver", "Jade", "Silent", "Dancing", "Flying", "Blazing",
        "Frosty", "Thunder", "Shadow", "Bright", "Wandering", "Curious", "Happy", "Bouncy",
        "Rocket", "Pixel", "Noble", "Wild", "Calm", "Epic", "Neon", "Star",
    )
    val ANIMALS = listOf(
        "Otter", "Panda", "Tiger", "Crane", "Fox", "Dragon", "Rabbit", "Monkey",
        "Turtle", "Eagle", "Wolf", "Koala", "Dolphin", "Falcon", "Hedgehog", "Lynx",
        "Badger", "Owl", "Penguin", "Raccoon", "Squirrel", "Whale", "Yak", "Crab",
        "Gecko", "Heron", "Ibis", "Jackal", "Kiwi", "Lemur", "Magpie", "Narwhal",
    )

    /** Preset avatars (spec 12: bundled set, no custom images). Emoji for the prototype. */
    val AVATARS = listOf("🐼", "🐯", "🦊", "🐲", "🐰", "🐵", "🦉", "🐢", "🐧", "🦁", "🐨", "🦄")

    fun generateName(random: Random = Random.Default): String =
        "${ADJECTIVES.random(random)} ${ANIMALS.random(random)}"

    fun randomAvatarId(random: Random = Random.Default): Int = random.nextInt(AVATARS.size)

    fun avatar(id: Int): String = AVATARS[id.mod(AVATARS.size)]

    /**
     * Friend codes (spec 12: mutual codes are the only way to connect). 6 chars from an
     * alphabet without 0/O/1/I/L so kids can read them aloud across a room; rotatable.
     */
    const val CODE_LENGTH = 6
    const val CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

    fun generateFriendCode(random: Random = Random.Default): String =
        buildString { repeat(CODE_LENGTH) { append(CODE_ALPHABET.random(random)) } }

    /**
     * Normalize user-typed codes: uppercase, strip whitespace/dashes. The alphabet
     * already excludes the confusable glyphs (0/O, 1/I/L), so no remapping is needed —
     * a typed O or 0 simply fails validation and the kid re-reads the code.
     */
    fun normalizeCode(input: String): String =
        input.uppercase().filter { !it.isWhitespace() && it != '-' }

    fun isValidCode(code: String): Boolean =
        code.length == CODE_LENGTH && code.all { it in CODE_ALPHABET }
}
