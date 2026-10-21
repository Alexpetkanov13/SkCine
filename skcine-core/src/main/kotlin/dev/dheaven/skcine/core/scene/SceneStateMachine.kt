package dev.dheaven.skcine.core.scene

/** Lifecycle of a scene instance. */
enum class SceneState(val id: String) {
    CREATED("created"),
    PREPARING("preparing"),
    READY("ready"),
    PLAYING("playing"),
    PAUSED("paused"),
    WAITING_FOR_INPUT("waiting for input"),
    CLEANING_UP("cleaning up"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    FAILED("failed"),
    ;

    val isTerminal get() = this == COMPLETED || this == CANCELLED || this == FAILED

    /** Scene owns viewers/resources and is ticked by the scheduler. */
    val isActive get() = this == PLAYING || this == PAUSED || this == WAITING_FOR_INPUT

    /** Not yet ended: active, or still starting up. */
    val isAlive get() = !isTerminal && this != CLEANING_UP
}

/** Why a scene instance ended. Determines the terminal state and which events fire. */
enum class EndReason(val id: String, val terminal: SceneState) {
    COMPLETED("completed", SceneState.COMPLETED),
    SKIPPED("skipped", SceneState.COMPLETED),
    STOPPED("stopped", SceneState.CANCELLED),
    NO_VIEWERS("no viewers", SceneState.CANCELLED),
    VIEWER_LEFT("viewer left", SceneState.CANCELLED),
    REPLACED("replaced by another scene", SceneState.CANCELLED),
    SCRIPT_UNLOADED("script unloaded", SceneState.CANCELLED),
    PLUGIN_DISABLED("plugin disabled", SceneState.CANCELLED),
    TIME_LIMIT("time limit reached", SceneState.CANCELLED),
    ERROR("error", SceneState.FAILED),
}

class IllegalSceneTransition(from: SceneState, to: SceneState) : IllegalStateException("cannot change scene state from ${from.id} to ${to.id}")

/**
 * Validated state machine. All transitions not listed in [allowed] are rejected; terminal states are final.
 * Not thread-safe: scene instances are driven from the server main thread.
 */
class SceneStateMachine {
    var state = SceneState.CREATED
        private set

    var endReason: EndReason? = null
        private set

    private val listeners = ArrayList<(SceneState, SceneState) -> Unit>()

    fun onTransition(listener: (from: SceneState, to: SceneState) -> Unit) {
        listeners.add(listener)
    }

    fun canTransition(to: SceneState) = to in (allowed[state] ?: emptySet())

    /** Performs the transition if valid. Returns false (without throwing) for invalid or repeated transitions. */
    fun tryTransition(to: SceneState): Boolean {
        if (!canTransition(to)) return false
        val from = state
        state = to
        listeners.forEach { it(from, to) }
        return true
    }

    fun transition(to: SceneState) {
        if (!tryTransition(to)) throw IllegalSceneTransition(state, to)
    }

    /**
     * Begins ending the scene. Returns false if it is already ending or ended, which makes repeated
     * stop/skip/cancel calls harmless.
     */
    fun beginEnd(reason: EndReason): Boolean {
        if (state.isTerminal || state == SceneState.CLEANING_UP) return false
        endReason = reason
        if (state == SceneState.CREATED || state == SceneState.PREPARING || state == SceneState.READY) {
            // Nothing was acquired yet; still pass through cleanup so partial resources are released.
            state = SceneState.CLEANING_UP
            return true
        }
        return tryTransition(SceneState.CLEANING_UP)
    }

    /** Completes the end started by [beginEnd]. */
    fun finishEnd() {
        val reason = endReason ?: EndReason.COMPLETED
        if (state == SceneState.CLEANING_UP) tryTransition(reason.terminal)
    }

    companion object {
        private val allowed: Map<SceneState, Set<SceneState>> =
            mapOf(
                SceneState.CREATED to setOf(SceneState.PREPARING, SceneState.CLEANING_UP, SceneState.FAILED),
                SceneState.PREPARING to setOf(SceneState.READY, SceneState.CLEANING_UP, SceneState.FAILED),
                SceneState.READY to setOf(SceneState.PLAYING, SceneState.CLEANING_UP, SceneState.FAILED),
                SceneState.PLAYING to setOf(SceneState.PAUSED, SceneState.WAITING_FOR_INPUT, SceneState.CLEANING_UP, SceneState.FAILED),
                SceneState.PAUSED to setOf(SceneState.PLAYING, SceneState.WAITING_FOR_INPUT, SceneState.CLEANING_UP, SceneState.FAILED),
                SceneState.WAITING_FOR_INPUT to setOf(SceneState.PLAYING, SceneState.PAUSED, SceneState.CLEANING_UP, SceneState.FAILED),
                SceneState.CLEANING_UP to setOf(SceneState.COMPLETED, SceneState.CANCELLED, SceneState.FAILED),
                SceneState.COMPLETED to emptySet(),
                SceneState.CANCELLED to emptySet(),
                SceneState.FAILED to emptySet(),
            )
    }
}
