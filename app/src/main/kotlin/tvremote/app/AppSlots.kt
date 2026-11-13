package tvremote.app

/**
 * The app buttons of the remote. [link] is sent to the TV, which opens whatever
 * app handles it: a deep link, or `market://launch?id=<package>` to start an app
 * by its package name.
 */
data class AppSlot(val name: String, val link: String)

val APP_SLOTS = listOf(
    AppSlot("SmartTube", launchPackage("com.teamsmart.videomanager.tv")),
    AppSlot("Netflix", "https://www.netflix.com/title"),
    AppSlot("VLC", "vlc://"),
    AppSlot("Disney+", "https://www.disneyplus.com"),
)

fun launchPackage(packageName: String) = "market://launch?id=$packageName"
