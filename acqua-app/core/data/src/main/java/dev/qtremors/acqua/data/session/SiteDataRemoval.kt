package dev.qtremors.acqua.data.session

import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature

interface SiteDataRemoval {
    val supportsSiteRemoval: Boolean
    fun clearSite(origin: String, onComplete: () -> Unit)
    fun clearAllCookies(onComplete: () -> Unit)
    fun flushCookies()
}

internal class WebViewSiteDataRemoval : SiteDataRemoval {
    override val supportsSiteRemoval: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)

    override fun clearSite(origin: String, onComplete: () -> Unit) {
        WebStorageCompat.deleteBrowsingDataForSite(WebStorage.getInstance(), origin, onComplete)
    }

    override fun clearAllCookies(onComplete: () -> Unit) {
        CookieManager.getInstance().removeAllCookies { onComplete() }
    }

    override fun flushCookies() { CookieManager.getInstance().flush() }
}
