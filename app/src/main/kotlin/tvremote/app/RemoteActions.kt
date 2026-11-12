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
    fun openApp(link: String)
    fun typeText(text: String)
}
