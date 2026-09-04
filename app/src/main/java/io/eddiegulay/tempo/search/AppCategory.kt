package io.eddiegulay.tempo.search

import android.content.pm.ApplicationInfo
import io.eddiegulay.tempo.i18n.SearchStrings

/** Tempo's word for an app category, in the UI language. The system title follows the device locale. */
fun appCategoryLabel(categoryId: Int, copy: SearchStrings): String? = when (categoryId) {
    ApplicationInfo.CATEGORY_GAME -> copy.categoryGame
    ApplicationInfo.CATEGORY_AUDIO -> copy.categoryAudio
    ApplicationInfo.CATEGORY_VIDEO -> copy.categoryVideo
    ApplicationInfo.CATEGORY_IMAGE -> copy.categoryImage
    ApplicationInfo.CATEGORY_SOCIAL -> copy.categorySocial
    ApplicationInfo.CATEGORY_NEWS -> copy.categoryNews
    ApplicationInfo.CATEGORY_MAPS -> copy.categoryMaps
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> copy.categoryProductivity
    ApplicationInfo.CATEGORY_ACCESSIBILITY -> copy.categoryAccessibility
    else -> null
}
