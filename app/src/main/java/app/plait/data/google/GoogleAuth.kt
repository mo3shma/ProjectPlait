package app.plait.data.google

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface AuthOutcome {
    data class Token(val accessToken: String) : AuthOutcome
    /** The user has to approve access once; launch [pendingIntent] and pass the result to [GoogleAuth.tokenFrom]. */
    data class NeedsConsent(val pendingIntent: PendingIntent) : AuthOutcome
    data class Failed(val reason: String) : AuthOutcome
}

/**
 * Read-only Calendar access through Google's Authorization API. No client ID or secret lives in
 * the app: Google identifies it by package name and signing certificate, which are registered
 * once in Google Cloud (see README).
 */
object GoogleAuth {
    private const val CALENDAR_READONLY = "https://www.googleapis.com/auth/calendar.readonly"
    private const val CALENDAR_EVENTS = "https://www.googleapis.com/auth/calendar.events"

    private fun request() = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(CALENDAR_READONLY)))
        .build()

    /** Asks, only when you first change a meeting, for permission to edit events too. */
    suspend fun authorizeWrite(context: Context): AuthOutcome = authorize(context, AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(CALENDAR_READONLY), Scope(CALENDAR_EVENTS))).build())

    suspend fun authorize(context: Context, req: AuthorizationRequest = request()): AuthOutcome = try {
        val result = Identity.getAuthorizationClient(context).authorize(req).await()
        when {
            result.hasResolution() -> AuthOutcome.NeedsConsent(result.pendingIntent!!)
            result.accessToken != null -> AuthOutcome.Token(result.accessToken!!)
            else -> AuthOutcome.Failed("Google didn't return an access token.")
        }
    } catch (e: ApiException) {
        AuthOutcome.Failed(explain(e))
    } catch (e: Exception) {
        AuthOutcome.Failed(e.message ?: "Google sign-in failed.")
    }

    /** Reads the token out of the result of the consent screen. */
    fun tokenFrom(context: Context, data: Intent?): String? = try {
        if (data == null) null else Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken
    } catch (e: ApiException) {
        null
    }

    private fun explain(e: ApiException): String = when (e.statusCode) {
        CommonStatusCodes.DEVELOPER_ERROR, 10 ->
            "Google sign-in isn't set up for this build yet. Register the app's package name and SHA-1 in Google Cloud."
        CommonStatusCodes.NETWORK_ERROR -> "No connection to Google."
        CommonStatusCodes.CANCELED, 16 -> "Sign-in was cancelled."
        else -> "Google sign-in failed (code ${e.statusCode})."
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
