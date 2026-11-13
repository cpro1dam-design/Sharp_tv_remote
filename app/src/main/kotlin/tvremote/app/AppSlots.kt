package tvremote.app

/**
 * The app buttons of the remote. [link] is sent to the TV, which opens the app
 * that declares it in its manifest. Opening an app by package name
 * (`market://launch?id=...`) goes through the Play Store and no longer works
 * for many apps, so each button uses a link the app itself handles.
 */
data class AppSlot(val name: String, val link: String)

val APP_SLOTS = listOf(
    // SmartTube handles youtube://play; without a video id it just opens its home screen.
    AppSlot("SmartTube", "youtube://play"),
    AppSlot("Netflix", "https://www.netflix.com/title"),
    // VLC's own "open the app" link (vlc:// is for playing a stream).
    AppSlot("VLC", "vlclauncher://org.videolan.vlc"),
    AppSlot("Disney+", "https://www.disneyplus.com"),
)
