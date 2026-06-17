# R8 rules for Tempo (release builds).
#
# Project-specific keep rules also live in src/main/keepRules/, which AGP merges
# into the R8 invocation automatically. Jetpack Compose, AndroidX and Kotlin all
# ship their own consumer rules via their AARs, so very little is needed here.

# Keep the notification listener service: it is instantiated by the framework by
# name, so R8 must not rename or strip it.
-keep class io.eddiegulay.tempo.notification.TempoNotificationListener { *; }

# Kotlin metadata + coroutines/Compose generally need no extra rules thanks to
# bundled consumer rules. Add app-specific keeps below as the app grows.
