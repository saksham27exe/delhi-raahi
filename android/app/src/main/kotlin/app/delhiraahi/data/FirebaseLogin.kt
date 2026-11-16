package app.delhiraahi.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.tasks.await

/** Words for Firebase sign-in errors, in the app language. */
class LoginError(message: String) : Exception(message)

/**
 * Email + password sign-in with Firebase Authentication. The app only signs in here; everything else
 * (wallet, tickets) goes to our backend with the Firebase ID token, which the backend verifies.
 * Only active when the app was built with google-services.json.
 */
object FirebaseLogin {
    fun available(ctx: Context): Boolean = FirebaseApp.getApps(ctx).isNotEmpty()

    private val auth get() = FirebaseAuth.getInstance()
    val signedIn: Boolean get() = auth.currentUser != null

    /** A fresh ID token (Firebase refreshes it when it is near expiry); null when signed out. */
    suspend fun idToken(): String? = auth.currentUser?.getIdToken(false)?.await()?.token

    private suspend fun <T> guard(hindi: Boolean, block: suspend () -> T): T = try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        val en = !hindi
        throw LoginError(when (e) {
            is FirebaseAuthWeakPasswordException -> if (en) "Use at least 8 characters for the password." else "पासवर्ड कम से कम 8 अक्षर का रखें।"
            is FirebaseAuthUserCollisionException -> if (en) "An account with this email already exists. Log in instead." else "इस ईमेल से खाता पहले से है। लॉग इन करें।"
            is FirebaseAuthInvalidUserException -> if (en) "No account with this email." else "इस ईमेल से कोई खाता नहीं है।"
            is FirebaseAuthInvalidCredentialsException -> if (en) "Wrong email or password." else "ईमेल या पासवर्ड गलत है।"
            is FirebaseTooManyRequestsException -> if (en) "Too many attempts. Try again in a few minutes." else "बहुत ज़्यादा कोशिशें। कुछ मिनट बाद फिर कोशिश करें।"
            is FirebaseNetworkException -> if (en) "No internet connection." else "इंटरनेट नहीं है।"
            else -> e.message ?: "Sign-in failed."
        })
    }

    suspend fun signIn(email: String, password: String, hindi: Boolean) = guard(hindi) {
        auth.signInWithEmailAndPassword(email.trim(), password).await(); Unit
    }

    /** Creates the sign-in, sets the display name, and refreshes the token so it carries the name. */
    suspend fun signUp(name: String, email: String, password: String, hindi: Boolean) = guard(hindi) {
        if (password.length < 8) throw FirebaseAuthWeakPasswordException("weak", "short", "short")
        val u = auth.createUserWithEmailAndPassword(email.trim(), password).await().user!!
        u.updateProfile(userProfileChangeRequest { displayName = name.trim() }).await()
        u.getIdToken(true).await(); Unit
    }

    suspend fun resendVerification(hindi: Boolean) = guard(hindi) { auth.currentUser?.sendEmailVerification()?.await(); Unit }

    /** After the link is opened: reload the user and get a new token, so the server sees email_verified. */
    suspend fun reloadVerified(hindi: Boolean): Boolean = guard(hindi) {
        val u = auth.currentUser ?: return@guard false
        u.reload().await()
        u.getIdToken(true).await()
        u.isEmailVerified
    }

    suspend fun sendReset(email: String, hindi: Boolean) = guard(hindi) { auth.sendPasswordResetEmail(email.trim()).await(); Unit }

    /** Checks the current password again (needed before changing it or deleting the account). */
    suspend fun reauth(password: String, hindi: Boolean) = guard(hindi) {
        val u = auth.currentUser ?: throw FirebaseAuthInvalidUserException("none", "signed out")
        u.reauthenticate(EmailAuthProvider.getCredential(u.email.orEmpty(), password)).await()
        u.getIdToken(true).await(); Unit           // a token whose auth_time is now: the server needs it to delete
    }

    suspend fun changePassword(old: String, new: String, hindi: Boolean) {
        reauth(old, hindi)
        guard(hindi) {
            if (new.length < 8) throw FirebaseAuthWeakPasswordException("weak", "short", "short")
            auth.currentUser!!.updatePassword(new).await(); Unit
        }
    }

    fun signOut() { runCatching { auth.signOut() } }
}
