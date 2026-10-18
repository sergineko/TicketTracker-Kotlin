package es.sergiotech.tickettracker.data.cloud

import es.sergiotech.tickettracker.BuildConfig
import es.sergiotech.tickettracker.domain.*
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class CloudflareAccountGateway(private val api: RemoteApi, private val store: CloudSessionStore) :
    AccountGateway {
    override val configured
        get() = api.configured

    override val accounts = flow {
        store.current()
        emitAll(
            store.sessions.map {
                it?.let { AccountInfo(it.userId, it.email, it.expiresAt, it.signInMethod) }
            }
        )
    }
    override val recoveryNeeded = store.recoveryNeeded

    override suspend fun requestCode(email: String): EmailChallenge {
        val response =
            api.request(
                "/v1/auth/email/request",
                method = "POST",
                body = JSONObject().put("email", email.trim()),
            )
        return EmailChallenge(response.getString("challenge_id"))
    }

    override suspend fun verifyCode(challengeId: String, code: String) {
        val response =
            api.request(
                "/v1/auth/email/verify",
                method = "POST",
                body = JSONObject().put("challenge_id", challengeId).put("code", code),
            )
        store.activate(response.session(SignInMethod.EMAIL_CODE))
    }

    override suspend fun googleChallenge(): GoogleChallenge {
        check(BuildConfig.GOOGLE_CLIENT_ID.isNotBlank()) { "Google client is not configured" }
        val response = api.request("/v1/auth/google/nonce", method = "POST")
        return GoogleChallenge(
            response.getString("challenge_id"),
            response.getString("nonce"),
            BuildConfig.GOOGLE_CLIENT_ID,
        )
    }

    override suspend fun verifyGoogle(challengeId: String, idToken: String) {
        val response =
            api.request(
                "/v1/auth/google",
                token = store.current()?.token,
                method = "POST",
                body = JSONObject().put("challenge_id", challengeId).put("id_token", idToken),
            )
        store.activate(response.session(SignInMethod.GOOGLE))
    }

    override suspend fun logout() {
        val current = store.current() ?: return
        // Revoke remotely first. A failed request leaves the account accessible offline to retry.
        try {
            api.request("/v1/auth/logout", current.token, "POST")
        } catch (failure: CloudApiException) {
            if (failure.status != 401) throw failure
        }
        store.logoutLocally()
    }
}

private fun JSONObject.session(method: SignInMethod): CloudSession {
    val user = getJSONObject("user")
    return CloudSession(
        user.getString("id"),
        user.getString("email"),
        getString("token"),
        getLong("expires_at"),
        method,
    )
}
