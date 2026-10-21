package dev.dheaven.skcine.platform

import java.util.logging.Level
import java.util.logging.Logger

/**
 * Developer-facing error reporting.
 *
 * Messages are prefixed with `[SkCine]` by the plugin logger. Repeated messages with the same key are suppressed for
 * [repeatWindowMillis] so a broken scene cannot flood the console every tick.
 */
class Diagnostics(
    private val logger: Logger,
    var debugEnabled: Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val repeatWindowMillis: Long = 10_000,
) {
    private val lastLogged = HashMap<String, Long>()
    private val suppressed = HashMap<String, Int>()

    var errorCount = 0L
        private set

    fun error(message: String, key: String = message, cause: Throwable? = null) {
        errorCount++
        log(Level.SEVERE, message, key, cause)
    }

    fun warn(message: String, key: String = message) = log(Level.WARNING, message, key, null)

    fun info(message: String) = logger.info(message)

    fun debug(message: () -> String) {
        if (debugEnabled) logger.info("[debug] " + message())
    }

    private fun log(level: Level, message: String, key: String, cause: Throwable?) {
        val now = clock()
        synchronized(lastLogged) {
            val last = lastLogged[key]
            if (last != null && now - last < repeatWindowMillis) {
                suppressed[key] = (suppressed[key] ?: 0) + 1
                return
            }
            lastLogged[key] = now
            val count = suppressed.remove(key) ?: 0
            val suffix = if (count > 0) " (repeated $count more time${if (count == 1) "" else "s"})" else ""
            if (lastLogged.size > 2048) lastLogged.clear()
            if (cause != null &&
                debugEnabled
            ) {
                logger.log(level, message + suffix, cause)
            } else {
                logger.log(
                    level,
                    message + suffix + (cause?.let { " — ${it.javaClass.simpleName}: ${it.message}" } ?: ""),
                )
            }
        }
    }

    companion object {
        /** Formats a consistent scene-related message. */
        fun scene(sceneId: String?, script: String?, message: String): String {
            val where =
                buildString {
                    if (sceneId != null) append("Scene '").append(sceneId).append("'")
                    if (script != null) append(if (isEmpty()) "" else " ").append("(").append(script).append(")")
                }
            return if (where.isEmpty()) message else "$where: $message"
        }
    }
}
