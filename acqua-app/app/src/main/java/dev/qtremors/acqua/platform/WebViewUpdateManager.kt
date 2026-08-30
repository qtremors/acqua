package dev.qtremors.acqua.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.webkit.WebViewCompat

data class WebViewProviderInfo(
    val packageName: String,
    val label: String,
    val versionName: String?
)

object WebViewUpdateManager {
    fun currentProvider(context: Context): WebViewProviderInfo? {
        val packageInfo = runCatching { WebViewCompat.getCurrentWebViewPackage(context) }.getOrNull()
            ?: return null

        val label = packageInfo.applicationInfo
            ?.loadLabel(context.packageManager)
            ?.toString()
            ?.takeIf(String::isNotBlank)
            ?: packageInfo.packageName
        return WebViewProviderInfo(packageInfo.packageName, label, packageInfo.versionName)
    }

    fun openUpdatePage(context: Context, providerPackage: String?): Boolean {
        val packageName = providerPackage?.takeIf(String::isNotBlank) ?: DEFAULT_PROVIDER_PACKAGE
        val intents = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                .setPackage(PLAY_STORE_PACKAGE),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_WEBVIEW_SETTINGS)
        )
        return intents.any { intent ->
            runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrElse { error ->
                if (error is ActivityNotFoundException || error is SecurityException) false else throw error
            }
        }
    }

    private const val PLAY_STORE_PACKAGE = "com.android.vending"
    private const val DEFAULT_PROVIDER_PACKAGE = "com.google.android.webview"
}
