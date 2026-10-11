package dev.qtremors.acqua.settings

import android.content.Context
import androidx.activity.ComponentActivity

open class LocalizedActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppLanguages.wrap(newBase)) }
}
