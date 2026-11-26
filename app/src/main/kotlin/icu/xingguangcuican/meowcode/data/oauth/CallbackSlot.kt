package icu.xingguangcuican.meowcode.data.oauth

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred

/**
 * The single callback slot the browser flow routes into.
 *
 * Each [begin] takes ownership of the slot and retires the previous attempt, and
 * [deliver] only resumes the attempt whose `state` the redirect echoes. Both rules
 * matter: a deferred is completed once and stays completed, so reusing one across
 * logins made every login after the first resume instantly with the *old* code —
 * it failed the state check before the user had even consented, and the real
 * callback then arrived at a dead login.
 *
 * Pure Kotlin (no Android types) so the routing is unit-tested on the JVM.
 */
class CallbackSlot {
    private val attempt = AtomicReference<Attempt?>(null)

    private class Attempt(val state: String?, val result: CompletableDeferred<AuthorizationResult>)

    /**
     * Opens a new attempt for a request carrying [state]. An older attempt that is
     * still waiting is resolved as cancelled: its browser tab can no longer finish
     * this login, and its callback must not end the new one.
     */
    fun begin(state: String?): CompletableDeferred<AuthorizationResult> {
        val fresh = Attempt(state, CompletableDeferred())
        attempt.getAndSet(fresh)?.result?.complete(AuthorizationResult(null, null, "superseded", "已被新的登录尝试取代"))
        return fresh.result
    }

    /**
     * Hands [redirect] to the waiting attempt. Returns false when the slot is empty
     * or the redirect's `state` is not the one being awaited — an older browser tab
     * coming back late must never end the login that is actually in progress.
     */
    fun deliver(redirect: AuthorizationResult): Boolean {
        val slot = attempt.get() ?: return false
        if (slot.state != redirect.state) return false
        return attempt.compareAndSet(slot, null) && slot.result.complete(redirect)
    }

    /**
     * Resolves the waiting attempt as cancelled (back / cancel in the UI).
     *
     * Pass [waiting] to cancel only that attempt: a timed-out `authorize` must clear
     * its own slot, never the attempt a newer login has already installed.
     */
    fun cancel(waiting: CompletableDeferred<AuthorizationResult>? = null): Boolean {
        val slot = attempt.get() ?: return false
        if (waiting != null && slot.result !== waiting) return false
        return attempt.compareAndSet(slot, null) &&
            slot.result.complete(AuthorizationResult(null, null, "cancelled", "已取消授权"))
    }

    /** Test hook: whether a login is currently waiting for a callback. */
    internal fun isAwaiting(): Boolean = attempt.get() != null
}
