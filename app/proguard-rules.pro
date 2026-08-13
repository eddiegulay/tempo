# R8 rules for Tempo (release builds).
#
# Project-specific keep rules also live in src/main/keepRules/, which AGP merges
# into the R8 invocation automatically. Jetpack Compose, AndroidX and Kotlin all
# ship their own consumer rules via their AARs, so very little is needed here.

# Keep the notification listener service: it is instantiated by the framework by
# name, so R8 must not rename or strip it.
-keep class io.eddiegulay.tempo.notification.TempoNotificationListener { *; }

# 鍛錬's health foreground service, same reason: the framework instantiates it from
# the manifest name and delivers the two notification actions into onStartCommand.
#
# R8 IS enabled for release (app/build.gradle.kts: isMinifyEnabled = true,
# isShrinkResources = true), so the question is real rather than academic. Strictly,
# this rule is belt-and-braces: AGP generates keep rules from the merged manifest and
# every <service> named there is already kept. It is written out anyway because it
# matches the rule directly above it — the day someone reads this file to find out
# which components survive shrinking, a service that is kept only implicitly reads as
# a service that was forgotten.
-keep class io.eddiegulay.tempo.gym.TrainingService { *; }

# Kotlin metadata + coroutines/Compose generally need no extra rules thanks to
# bundled consumer rules. Add app-specific keeps below as the app grows.
