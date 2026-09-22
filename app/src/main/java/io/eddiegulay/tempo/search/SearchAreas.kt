package io.eddiegulay.tempo.search

/** One source Search can look through. Missing store keys mean on, so an upgrade does not go silent. */
enum class SearchArea {
    Apps,
    Phone,
    Contacts,
    WhatsApp,
    Google,
    Email,
    Calendar,
    Spotify,
}

data class SearchAreas(
    val apps: Boolean = true,
    val phone: Boolean = true,
    val contacts: Boolean = true,
    val whatsApp: Boolean = true,
    val google: Boolean = true,
    val email: Boolean = true,
    val calendar: Boolean = true,
    val spotify: Boolean = true,
) {
    fun isOn(area: SearchArea): Boolean = when (area) {
        SearchArea.Apps -> apps
        SearchArea.Phone -> phone
        SearchArea.Contacts -> contacts
        SearchArea.WhatsApp -> whatsApp
        SearchArea.Google -> google
        SearchArea.Email -> email
        SearchArea.Calendar -> calendar
        SearchArea.Spotify -> spotify
    }

    fun with(area: SearchArea, on: Boolean): SearchAreas = when (area) {
        SearchArea.Apps -> copy(apps = on)
        SearchArea.Phone -> copy(phone = on)
        SearchArea.Contacts -> copy(contacts = on)
        SearchArea.WhatsApp -> copy(whatsApp = on)
        SearchArea.Google -> copy(google = on)
        SearchArea.Email -> copy(email = on)
        SearchArea.Calendar -> copy(calendar = on)
        SearchArea.Spotify -> copy(spotify = on)
    }
}
