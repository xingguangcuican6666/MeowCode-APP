package icu.xingguangcuican.meowcode

import icu.xingguangcuican.meowcode.data.oauth.AuthorizationResult
import icu.xingguangcuican.meowcode.data.oauth.CallbackSlot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The callback slot is what made every login after a sign-out fail: a deferred is
 * completed once and stays completed, so a reused slot resumed the next login with
 * the previous attempt's code, and the real callback then landed nowhere. These
 * tests pin the two rules that prevent it — one attempt owns the slot at a time,
 * and only the matching `state` is delivered.
 */
class CallbackSlotTest {

    @Test
    fun `begin retires the previous attempt instead of leaving it completable`() = runTest {
        val slot = CallbackSlot()
        val first = slot.begin("state-1")
        val second = slot.begin("state-2")

        assertEquals("superseded", first.await().error)
        assertFalse(first.isActive)
        assertTrue(second.isActive)
        assertTrue(slot.isAwaiting())
    }

    @Test
    fun `a second login resumes from its own callback only`() = runTest {
        val slot = CallbackSlot()
        slot.begin("state-1")
        val waiting = slot.begin("state-2")

        // The tab from the first attempt comes back late.
        assertFalse(slot.deliver(AuthorizationResult("code-old", "state-1", null, null)))
        assertTrue(waiting.isActive)

        assertTrue(slot.deliver(AuthorizationResult("code-new", "state-2", null, null)))
        assertEquals("code-new", waiting.await().code)
        assertFalse(slot.isAwaiting())
    }

    @Test
    fun `deliver without a waiting attempt reports that nothing was resumed`() {
        val slot = CallbackSlot()
        assertFalse(slot.deliver(AuthorizationResult("code", "state-1", null, null)))
    }

    @Test
    fun `a redirected error with the right state wakes the waiter`() = runTest {
        val slot = CallbackSlot()
        val waiting = slot.begin("state-1")

        assertTrue(slot.deliver(AuthorizationResult(null, "state-1", "access_denied", "拒绝了授权")))
        val result = waiting.await()
        assertNull(result.code)
        assertEquals("access_denied", result.error)
    }

    @Test
    fun `cancel ends the waiting attempt and empties the slot`() = runTest {
        val slot = CallbackSlot()
        val waiting = slot.begin("state-1")

        assertTrue(slot.cancel())
        assertEquals("cancelled", waiting.await().error)
        assertFalse(slot.isAwaiting())
        assertEquals(false, slot.cancel())
    }

    @Test
    fun `a completed attempt cannot be woken twice`() {
        val slot = CallbackSlot()
        val waiting: CompletableDeferred<AuthorizationResult> = slot.begin("state-1")
        assertTrue(slot.deliver(AuthorizationResult("c1", "state-1", null, null)))
        assertFalse(slot.deliver(AuthorizationResult("c2", "state-1", null, null)))
        assertEquals("c1", waiting.getCompleted().code)
    }
}
