package icu.xingguangcuican.meowcode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import icu.xingguangcuican.meowcode.data.oauth.BrowserOAuthTransport
import icu.xingguangcuican.meowcode.data.storage.SessionStore
import icu.xingguangcuican.meowcode.ui.MeowApp
import icu.xingguangcuican.meowcode.ui.state.Destinations
import icu.xingguangcuican.meowcode.ui.state.MeowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MainActivity : ComponentActivity() {
    // Held outside composition: the transport must outlive the frame that starts a login.
    private lateinit var state: MeowState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A single app-scoped coroutine scope stands in for a ViewModel in the stub.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        state = MeowState(
            scope = scope,
            browserTransport = BrowserOAuthTransport(this),
            store = SessionStore(this),
        )
        // Cold start straight from the consent page (the process was killed in the browser).
        intent?.dataString?.let(::deliverCallback)
        setContent { MeowApp(state) }
    }

    /**
     * launchMode is singleTask: the browser's VIEW intent arrives here, not as a new
     * activity, so the suspended login in [BrowserOAuthTransport] resumes in place.
     */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let(::deliverCallback)
    }

    private fun deliverCallback(rawUri: String) {
        if (!state.handleOAuthCallback(rawUri)) return
        // The user tapped "同意" in the browser; show them where the result landed.
        state.destination = Destinations.Me
    }
}
