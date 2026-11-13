package tvremote.app

/** Everything the screens can ask for; implemented by [RemoteViewModel]. */
interface RemoteActions {
    fun startSearch()
    fun chooseTv(name: String, host: String)
    fun changeTv()
    fun connect()
    fun retryPairing()
    fun submitCode(code: String)
    fun backToSetup()
    fun key(code: Int)
    /** Opens the app of button [slot] (index in [APP_SLOTS]). */
    fun openApp(slot: Int)
    /** Makes button [slot] open the app currently shown on the TV. */
    fun bindAppToCurrent(slot: Int)
    fun typeText(text: String)
}
