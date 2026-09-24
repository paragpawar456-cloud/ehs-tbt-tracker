package com.ehs.tbttracker.data.remote

/** Apps Script Web App deployment + shared secret (from BuildConfig / local.properties). */
data class BackendConfig(val webAppUrl: String, val token: String) {
    val isConfigured: Boolean
        get() = webAppUrl.startsWith("https://script.google.com/") && !webAppUrl.contains("REPLACE_") &&
            token.isNotBlank() && !token.contains("REPLACE_")
}
