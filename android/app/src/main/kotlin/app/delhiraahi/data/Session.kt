package app.delhiraahi.data

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val android.content.Context.sessionData by preferencesDataStore(name = "session")

/**
 * Signed-in user, DEMO wallet and passes. The login token is kept in app-private storage only and
 * sent as a Bearer header; it is never logged. Guests have `user == null`.
 */
class SessionViewModel(app: Application) : AndroidViewModel(app) {
    private val kToken = stringPreferencesKey("token")
    private val ctx get() = getApplication<Application>()
    val tickets = TicketStore(app)

    var ready by mutableStateOf(false); private set
    var user by mutableStateOf<User?>(null); private set
    var wallet by mutableStateOf<Wallet?>(null); private set
    var passes by mutableStateOf<List<DemoPass>?>(null); private set
    var catalog by mutableStateOf<PassCatalog?>(null); private set

    val loggedIn get() = user != null

    /** Built with Firebase config (google-services.json): sign-in is email + password with Firebase. */
    val firebase = FirebaseLogin.available(app)
    /** What the backend uses: "firebase" or "local" (null until known / offline). */
    var serverStore by mutableStateOf<String?>(null); private set

    init {
        viewModelScope.launch {
            if (firebase) {
                Api.tokenProvider = { FirebaseLogin.idToken() }
                if (FirebaseLogin.signedIn) refreshMe()
            } else {
                Api.token = runCatching { ctx.sessionData.data.first()[kToken] }.getOrNull()
                if (Api.token != null) refreshMe()
            }
            ready = true
        }
    }

    suspend fun checkServer() { serverStore = runCatching { Api.health().accounts_store }.getOrNull() }

    suspend fun loginEmail(email: String, password: String, hindi: Boolean) {
        FirebaseLogin.signIn(email, password, hindi); afterFirebase()
    }
    suspend fun registerEmail(name: String, email: String, password: String, hindi: Boolean) {
        FirebaseLogin.signUp(name, email, password, hindi); afterFirebase()
    }
    suspend fun resetPassword(email: String, hindi: Boolean) = FirebaseLogin.sendReset(email, hindi)

    private suspend fun afterFirebase() {
        try { user = Api.me().user } catch (e: Exception) { FirebaseLogin.signOut(); throw e }
        syncAll()
    }

    /** Check the token; a 401 means it expired or was signed out elsewhere. */
    suspend fun refreshMe() {
        runCatching { Api.me() }
            .onSuccess { user = it.user; wallet = (wallet ?: Wallet()).copy(balance = it.wallet_balance) }
            .onFailure { if (it is ApiException && it.status == 401) clearLocal() }
    }

    private suspend fun start(r: AuthResponse) {
        Api.token = r.token
        ctx.sessionData.edit { it[kToken] = r.token }
        user = r.user
        syncAll()
    }

    suspend fun login(phone: String, password: String) = start(Api.login(phone, password))
    suspend fun register(phone: String, password: String, name: String) = start(Api.register(phone, password, name))

    suspend fun syncAll() {
        refreshWallet(); refreshPasses(); syncTickets()
    }

    suspend fun syncTickets() {
        if (!loggedIn) return
        runCatching { Api.myTickets().tickets }.onSuccess { tickets.replaceAll(it) }
    }

    suspend fun refreshWallet() {
        if (!loggedIn) return
        runCatching { Api.wallet() }.onSuccess { wallet = it }
    }

    suspend fun refreshPasses() {
        if (!loggedIn) return
        runCatching { Api.myPasses().passes }.onSuccess { passes = it }
    }

    suspend fun loadCatalog() {
        if (catalog == null) catalog = runCatching { Api.passCatalog() }.getOrNull()
    }

    suspend fun topup(amount: Int) { wallet = Api.topup(amount) }

    suspend fun buyPass(kind: String, busType: String): DemoPass {
        val p = Api.buyPass(kind, busType)
        passes = listOf(p) + passes.orEmpty()
        refreshWallet()
        return p
    }

    suspend fun onBooked(t: DemoTicket) {
        tickets.add(t)
        refreshWallet()
    }

    suspend fun setWomanTraveller(on: Boolean) { user = Api.updateMe(womanTraveller = on).user }
    suspend fun setName(name: String) { user = Api.updateMe(name = name).user }

    suspend fun changePassword(old: String, new: String, hindi: Boolean = false) {
        if (firebase) { FirebaseLogin.changePassword(old, new, hindi); return }
        val tok = Api.changePassword(old, new).token
        Api.token = tok
        ctx.sessionData.edit { it[kToken] = tok }
    }

    /** Signs out on every device (server bumps the token version), then clears this device. */
    fun logout() = viewModelScope.launch {
        if (!firebase) runCatching { Api.logout() }
        clearLocal()
    }

    suspend fun deleteAccount(password: String, hindi: Boolean = false) {
        if (firebase) FirebaseLogin.reauth(password, hindi)      // confirm it's really you
        Api.deleteAccount(password)                               // server deletes the data and the sign-in
        clearLocal()
    }

    private suspend fun clearLocal() {
        if (firebase) FirebaseLogin.signOut()
        Api.token = null
        ctx.sessionData.edit { it.remove(kToken) }
        tickets.clear()
        user = null; wallet = null; passes = null
    }
}
