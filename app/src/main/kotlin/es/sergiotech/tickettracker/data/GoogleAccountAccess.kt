package es.sergiotech.tickettracker.data

import android.content.Context
import android.content.MutableContextWrapper
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.*
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import es.sergiotech.tickettracker.domain.CloudOperationException
import es.sergiotech.tickettracker.domain.GoogleChallenge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun googleToken(context: Context, challenge: GoogleChallenge): String =
    withContext(Dispatchers.Main.immediate) {
        val wrapper = MutableContextWrapper(context)
        try {
            val option =
                GetSignInWithGoogleOption.Builder(challenge.clientId)
                    .setNonce(challenge.nonce)
                    .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential =
                CredentialManager.create(context.applicationContext)
                    .getCredential(wrapper, request)
                    .credential
            if (
                credential !is CustomCredential ||
                    credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            )
                throw CloudOperationException("google_response_invalid")
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (_: NoCredentialException) {
            throw CloudOperationException("google_unavailable")
        } catch (cancelled: GetCredentialCancellationException) {
            throw CancellationException("Google sign-in cancelled", cancelled)
        } catch (_: GetCredentialProviderConfigurationException) {
            throw CloudOperationException("google_device_setup")
        } catch (_: GetCredentialUnsupportedException) {
            throw CloudOperationException("google_device_setup")
        } catch (_: GoogleIdTokenParsingException) {
            throw CloudOperationException("google_response_invalid")
        } catch (_: GetCredentialException) {
            throw CloudOperationException("google_signin_failed")
        } finally {
            wrapper.baseContext = context.applicationContext
        }
    }

internal suspend fun clearGoogleCredentialState(context: Context) {
    try {
        CredentialManager.create(context.applicationContext)
            .clearCredentialState(ClearCredentialStateRequest())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: ClearCredentialException) {
        throw CloudOperationException("credential_clear_failed")
    }
}
