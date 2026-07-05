package com.kangle.kardleaf

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import com.kangle.kardleaf.data.repository.PrefsManager
import java.util.Locale

object AppLocaleManager {
    fun wrap(context: Context): ContextWrapper {
        val language = PrefsManager(context).getAppLanguage()
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return ContextWrapper(context.createConfigurationContext(config))
    }
}
