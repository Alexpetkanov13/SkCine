package dev.dheaven.skcine.core.scene

/**
 * Validated scene identifier.
 *
 * Format: `name` or `namespace:name`, lowercase letters, digits, `_`, `-`, `.` and `/` (name only), at most 64 chars.
 * Input is trimmed and lower-cased so `RPG:Intro` and `rpg:intro` refer to the same scene.
 */
@JvmInline
value class SceneId private constructor(val value: String) {
    val namespace: String? get() = value.substringBefore(':', "").ifEmpty { null }
    val name: String get() = value.substringAfter(':')

    override fun toString() = value

    companion object {
        const val MAX_LENGTH = 64
        private val PATTERN = Regex("^(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+$")

        /** Returns the id, or null with [error] describing the problem. */
        fun parse(raw: String, error: (String) -> Unit = {}): SceneId? {
            val v = raw.trim().lowercase()
            return when {
                v.isEmpty() -> null.also { error("scene id must not be empty") }
                v.length > MAX_LENGTH -> null.also { error("scene id '$v' is longer than $MAX_LENGTH characters") }
                !PATTERN.matches(v) ->
                    null.also {
                        error("scene id '$v' may only contain a-z, 0-9, _, -, ., / and one optional 'namespace:' prefix")
                    }
                else -> SceneId(v)
            }
        }

        private var anonymousCounter = 0L

        /** Unique id for scenes created at runtime without a name. */
        @Synchronized
        fun anonymous(): SceneId = SceneId("dynamic:scene-${++anonymousCounter}")
    }
}
