package dev.qtremors.acqua

import android.Manifest
import android.app.Activity
import android.widget.Toast
import android.net.Uri
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.qtremors.acqua.downloader.AgeGateException
import dev.qtremors.acqua.downloader.ExpiredSessionException
import dev.qtremors.acqua.downloader.InstagramDownloader
import dev.qtremors.acqua.downloader.MediaResult
import dev.qtremors.acqua.auth.AuthenticatedMediaResolverActivity
import dev.qtremors.acqua.auth.BrowserActivity
import dev.qtremors.acqua.auth.BrowserSessionRegistry
import dev.qtremors.acqua.auth.SecureSessionStore
import dev.qtremors.acqua.auth.WebLink
import dev.qtremors.acqua.ui.theme.AcquaTheme
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

enum class LinkSupportState {
    EMPTY, SUPPORTED, UNSUPPORTED
}

private const val DEFAULT_FILENAME_FORMAT = "acqua_{username}_{resolution}_{date}_{time}_{index}"

private val FILENAME_VARIABLES = listOf(
    "{username}",
    "{resolution}",
    "{date}",
    "{time}",
    "{index}"
)

class MainActivity : ComponentActivity() {

    private val browserSessionRevision = mutableIntStateOf(0)
    private val browserDownloadRequestRevision = mutableIntStateOf(0)
    private val pendingBrowserDownloadUrl = mutableStateOf<String?>(null)
    private val useWebsiteSessionsState = mutableStateOf(false)
    private var pendingMediaResolution: CompletableDeferred<List<MediaResult>>? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> /* Result checked inline before writing */ }

    private val browserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (result.data?.getBooleanExtra(BrowserActivity.EXTRA_DOWNLOAD_CURRENT_PAGE, false) == true) {
                result.data?.getStringExtra(BrowserActivity.EXTRA_CURRENT_URL)
                    ?.let(WebLink::normalize)
                    ?.let { pendingBrowserDownloadUrl.value = it }
            } else {
                browserSessionRevision.intValue++
            }
        }
    }

    private val mediaResolverLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pending = pendingMediaResolution ?: return@registerForActivityResult
        pendingMediaResolution = null

        if (useWebsiteSessionsState.value) {
            SecureSessionStore.load(this)?.let { refreshedSession ->
                InstagramDownloader.setSessionCookies(
                    refreshedSession.cookies,
                    refreshedSession.userAgent
                )
            }
        } else {
            InstagramDownloader.clearSessionCookies()
        }

        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val media = runCatching {
                    AuthenticatedMediaResolverActivity.parseMediaResults(result.data).map { item ->
                        item.copy(
                            requestCookies = if (useWebsiteSessionsState.value) {
                                CookieManager.getInstance().getCookie(item.url)
                                    ?.takeIf { it.isNotBlank() }
                            } else {
                                null
                            }
                        )
                    }
                }.getOrElse {
                    pending.completeExceptionally(
                        Exception("The browser returned invalid media information.", it)
                    )
                    return@registerForActivityResult
                }
                if (media.isEmpty()) {
                    pending.completeExceptionally(Exception("The browser did not return any media."))
                } else {
                    pending.complete(media)
                }
            }
            AuthenticatedMediaResolverActivity.RESULT_SESSION_EXPIRED -> {
                pending.completeExceptionally(
                    ExpiredSessionException(
                        result.data?.getStringExtra(AuthenticatedMediaResolverActivity.EXTRA_ERROR)
                            ?: "The saved login session has expired."
                    )
                )
            }
            else -> {
                pending.completeExceptionally(
                    Exception(
                        result.data?.getStringExtra(AuthenticatedMediaResolverActivity.EXTRA_ERROR)
                            ?: "The browser could not resolve this media."
                    )
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AcquaTheme {
                val initialUrl = handleIncomingIntent(intent)
                AcquaAppDashboard(initialUrl = initialUrl)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun handleIncomingIntent(intent: Intent): String {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            return WebLink.extractFirst(sharedText).orEmpty()
        }
        return ""
    }

    private suspend fun resolveMediaItems(
        url: String,
        useWebsiteSessions: Boolean = useWebsiteSessionsState.value
    ): List<MediaResult> {
        val normalizedUrl = WebLink.normalize(url)
            ?: throw IllegalArgumentException("Enter a valid HTTP or HTTPS link.")

        if (!WebLink.isInstagramMediaUrl(normalizedUrl)) {
            if (!useWebsiteSessions) {
                throw Exception("Enable website sessions to resolve this page in the browser.")
            }
            return resolveMediaItemsInWebView(normalizedUrl)
        }

        val networkError = try {
            return withContext(Dispatchers.IO) { InstagramDownloader.getMediaItems(normalizedUrl) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }

        if (!useWebsiteSessions) throw networkError

        return try {
            resolveMediaItemsInWebView(normalizedUrl)
        } catch (e: ExpiredSessionException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (browserError: Exception) {
            throw Exception(
                "Network extraction failed: ${networkError.message}\n" +
                    "Browser extraction failed: ${browserError.message}"
            )
        }
    }

    private suspend fun resolveMediaItemsInWebView(url: String): List<MediaResult> =
        withContext(Dispatchers.Main.immediate) {
            pendingMediaResolution?.completeExceptionally(
                CancellationException("A newer media request replaced this one.")
            )
            val pending = CompletableDeferred<List<MediaResult>>()
            pendingMediaResolution = pending
            mediaResolverLauncher.launch(
                Intent(this@MainActivity, AuthenticatedMediaResolverActivity::class.java)
                    .putExtra(AuthenticatedMediaResolverActivity.EXTRA_URL, url)
            )
            try {
                pending.await()
            } finally {
                if (pendingMediaResolution === pending) pendingMediaResolution = null
            }
        }

    private fun selectBestResolvedMedia(
        sourceUrl: String,
        items: List<MediaResult>
    ): List<MediaResult> {
        val isSingleVideo = WebLink.isInstagramMediaUrl(sourceUrl) &&
            Regex("/(?:reel|tv)/", RegexOption.IGNORE_CASE).containsMatchIn(sourceUrl)
        if (!isSingleVideo) return items.distinctBy { it.url }

        val bestVideo = items.asSequence()
            .filter { it.isVideo }
            .maxWithOrNull(
                compareBy<MediaResult> { it.fileSize ?: 0L }
                    .thenBy { it.width.toLong() * it.height.toLong() }
            )
        return bestVideo?.let(::listOf) ?: items.distinctBy { it.url }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun AcquaAppDashboard(initialUrl: String = "") {
        var urlInput by remember { mutableStateOf(initialUrl) }
        var isFetchingInfo by remember { mutableStateOf(false) }
        var isSavingMedia by remember { mutableStateOf(false) }
        var extractionError by remember { mutableStateOf<String?>(null) }
        var parsedMediaItems by remember { mutableStateOf<List<MediaResult>?>(null) }
        var isDownloadSuccess by remember { mutableStateOf(false) }
        var currentTab by remember { mutableStateOf(0) }
        var savingProgressIndex by remember { mutableStateOf(0) }
        var useWebsiteSessions by useWebsiteSessionsState

        var websiteLogins by remember {
            mutableStateOf<List<BrowserSessionRegistry.WebsiteLogin>>(emptyList())
        }
        var sessionInitialized by remember { mutableStateOf(false) }

        // Dynamic login promotion states
        var showLoginPromptInError by remember { mutableStateOf(false) }

        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val colorScheme = MaterialTheme.colorScheme
        val openBrowser: (String?, Boolean, String?) -> Unit = { initialUrl, addLogin, loginName ->
            browserLauncher.launch(
                Intent(this@MainActivity, BrowserActivity::class.java).apply {
                    putExtra(BrowserActivity.EXTRA_ADD_LOGIN, addLogin)
                    loginName?.takeIf { it.isNotBlank() }?.let {
                        putExtra(BrowserActivity.EXTRA_LOGIN_NAME, it)
                    }
                    WebLink.normalize(initialUrl.orEmpty())?.let {
                        putExtra(BrowserActivity.EXTRA_INITIAL_URL, it)
                    }
                }
            )
        }

        val linkSupportState = remember(urlInput) {
            detectLinkSupport(urlInput)
        }

        LaunchedEffect(pendingBrowserDownloadUrl.value) {
            pendingBrowserDownloadUrl.value?.let { browserUrl ->
                val savedSession = withContext(Dispatchers.IO) {
                    SecureSessionStore.load(context)
                }
                if (useWebsiteSessions && savedSession != null) {
                    InstagramDownloader.setSessionCookies(savedSession.cookies, savedSession.userAgent)
                } else {
                    InstagramDownloader.clearSessionCookies()
                }
                websiteLogins = BrowserSessionRegistry.websiteLogins(context)
                urlInput = browserUrl
                currentTab = 0
                browserDownloadRequestRevision.intValue++
                pendingBrowserDownloadUrl.value = null
            }
        }

        // Initialize session settings on startup
        LaunchedEffect(browserSessionRevision.intValue) {
            val preferences = context.getSharedPreferences("acqua_prefs", Context.MODE_PRIVATE)
            val sessionsEnabled = preferences.getBoolean("acqua_use_session_cookies", false)
            val savedSession = withContext(Dispatchers.IO) {
                SecureSessionStore.load(context)
            }

            useWebsiteSessions = sessionsEnabled
            if (sessionsEnabled && savedSession != null) {
                InstagramDownloader.setSessionCookies(savedSession.cookies, savedSession.userAgent)
            } else {
                InstagramDownloader.clearSessionCookies()
            }
            websiteLogins = BrowserSessionRegistry.websiteLogins(context)
            sessionInitialized = true
        }

        // Automatic retrieval when a supported URL changes
        LaunchedEffect(
            urlInput,
            sessionInitialized,
            useWebsiteSessions,
            browserDownloadRequestRevision.intValue
        ) {
            val sanitizedUrl = WebLink.normalize(urlInput)
            if (!sessionInitialized || sanitizedUrl == null) {
                parsedMediaItems = null
                return@LaunchedEffect
            }
            delay(350)

            // Add search link into the history database instantly
            withContext(Dispatchers.IO) {
                addHistoryEntry(
                    context,
                    HistoryItem(
                        id = java.util.UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        url = sanitizedUrl,
                        fileName = "",
                        fileUri = "",
                        isVideo = false,
                        mimeType = "",
                        sizeBytes = 0L,
                        isDownloaded = false
                    )
                )
            }

            isFetchingInfo = true
            extractionError = null
            parsedMediaItems = null
            isDownloadSuccess = false
            showLoginPromptInError = false

            val result = runCatching {
                val items = resolveMediaItems(sanitizedUrl)
                val validated = withContext(Dispatchers.IO) {
                    items.map { item ->
                        async {
                            InstagramDownloader.validateAndResolveMediaMetadata(item)
                        }
                    }.awaitAll().filterNotNull()
                }
                selectBestResolvedMedia(sanitizedUrl, validated.ifEmpty {
                    throw Exception("The resolved links did not contain complete downloadable media files.")
                })
            }
            isFetchingInfo = false
            if (result.isFailure) {
                val exception = result.exceptionOrNull()
                when (exception) {
                    is AgeGateException -> {
                        extractionError = exception.message ?: "Authentication required."
                        showLoginPromptInError = true
                    }
                    is ExpiredSessionException -> {
                        extractionError = exception.message ?: "Saved login session expired."
                        showLoginPromptInError = true
                    }
                    else -> {
                        extractionError = exception?.message ?: "An unexpected error occurred"
                        showLoginPromptInError = true
                    }
                }
                return@LaunchedEffect
            }
            triggerHapticStart(context)
            parsedMediaItems = result.getOrThrow()
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Acqua",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier.clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = {
                            triggerHapticStart(context)
                            currentTab = 0
                        },
                        icon = { Icon(imageVector = Icons.Filled.Download, contentDescription = "Downloader") },
                        label = { Text("Downloader") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = {
                            triggerHapticStart(context)
                            currentTab = 1
                        },
                        icon = { Icon(imageVector = Icons.Filled.Public, contentDescription = "Browser") },
                        label = { Text("Browser") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = {
                            triggerHapticStart(context)
                            currentTab = 2
                        },
                        icon = { Icon(imageVector = Icons.Filled.History, contentDescription = "History") },
                        label = { Text("History") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = {
                            triggerHapticStart(context)
                            currentTab = 3
                        },
                        icon = { Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }
            },
            containerColor = colorScheme.background
        ) { paddingValues ->

            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (currentTab == 0) 1f else 0f
                            translationX = if (currentTab == 0) 0f else 2000f
                        }
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                Spacer(modifier = Modifier.height(32.dp))

                // Unified Input Search/Paste card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Paste Media Link",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            ),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = {
                                urlInput = it
                                parsedMediaItems = null
                                extractionError = null
                                showLoginPromptInError = false
                            },
                            label = { Text("URL Link") },
                            placeholder = { Text("Paste a media link...") },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clipData = clipboard.primaryClip
                                    if (clipData != null && clipData.itemCount > 0) {
                                        val pastedText = clipData.getItemAt(0).text?.toString() ?: ""
                                        if (pastedText.isNotEmpty()) {
                                            urlInput = pastedText
                                            parsedMediaItems = null
                                            extractionError = null
                                            showLoginPromptInError = false
                                        }
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentPaste,
                                        contentDescription = "Paste",
                                        tint = colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            maxLines = 3,
                            enabled = !isSavingMedia,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colorScheme.primary,
                                focusedLabelColor = colorScheme.primary,
                                cursorColor = colorScheme.primary,
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Dynamic UI panel based on link support
                when (linkSupportState) {
                    LinkSupportState.EMPTY -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = "Ready",
                                        tint = colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Ready to Download",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Paste any HTTP or HTTPS media link and Acqua will try to resolve it.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    LinkSupportState.SUPPORTED -> {
                        AnimatedVisibility(
                            visible = isFetchingInfo,
                            enter = fadeIn(tween(150)),
                            exit = fadeOut(tween(150)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            LinearWavyProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = colorScheme.primary,
                                trackColor = colorScheme.primary.copy(alpha = 0.2f)
                            )
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh)
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {

                                Button(
                                    onClick = {
                                        val url = WebLink.normalize(urlInput).orEmpty()
                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !hasWritePermission()) {
                                            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                        } else {
                                            coroutineScope.launch {
                                                extractionError = null
                                                showLoginPromptInError = false
                                                val items = parsedMediaItems ?: run {
                                                    isFetchingInfo = true
                                                    withContext(Dispatchers.IO) {
                                                        addHistoryEntry(
                                                            context,
                                                            HistoryItem(
                                                                id = java.util.UUID.randomUUID().toString(),
                                                                timestamp = System.currentTimeMillis(),
                                                                url = url,
                                                                fileName = "",
                                                                fileUri = "",
                                                                isVideo = false,
                                                                mimeType = "",
                                                                sizeBytes = 0L,
                                                                isDownloaded = false
                                                            )
                                                        )
                                                    }
                                                    val fetchResult = runCatching {
                                                        val resolved = resolveMediaItems(url)
                                                        val validated = withContext(Dispatchers.IO) {
                                                            resolved.map { item ->
                                                                async {
                                                                    InstagramDownloader.validateAndResolveMediaMetadata(item)
                                                                }
                                                            }.awaitAll().filterNotNull()
                                                        }
                                                        selectBestResolvedMedia(url, validated.ifEmpty {
                                                            throw Exception("The resolved links did not contain complete downloadable media files.")
                                                        })
                                                    }
                                                    isFetchingInfo = false
                                                    if (fetchResult.isFailure) {
                                                        val exception = fetchResult.exceptionOrNull()
                                                        when (exception) {
                                                            is AgeGateException -> {
                                                                extractionError = exception.message ?: "Authentication required."
                                                                showLoginPromptInError = true
                                                            }
                                                            is ExpiredSessionException -> {
                                                                extractionError = exception.message ?: "Saved login session expired."
                                                                showLoginPromptInError = true
                                                            }
                                                            else -> {
                                                                extractionError = exception?.message ?: "Extraction failed"
                                                                showLoginPromptInError = true
                                                            }
                                                        }
                                                        return@launch
                                                    }
                                                    fetchResult.getOrThrow().also { parsedMediaItems = it }
                                                }

                                                triggerHapticStart(context)
                                                isSavingMedia = true
                                                savingProgressIndex = 0

                                                val saveResult = runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        items.forEachIndexed { idx, item ->
                                                             savingProgressIndex = idx + 1
                                                             saveMediaToSystem(item, idx, url, context)
                                                        }
                                                    }
                                                }
                                                isSavingMedia = false

                                                if (saveResult.isSuccess) {
                                                    triggerHapticComplete(context)
                                                    isDownloadSuccess = true
                                                    delay(2500)
                                                    isDownloadSuccess = false
                                                    parsedMediaItems = null
                                                } else {
                                                    extractionError = saveResult.exceptionOrNull()?.message ?: "Failed to write media"
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    enabled = !isSavingMedia,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorScheme.primary,
                                        contentColor = colorScheme.onPrimary
                                    )
                                ) {
                                    when {
                                        isSavingMedia -> {
                                            CircularProgressIndicator(
                                                color = colorScheme.onPrimary,
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            val totalCount = parsedMediaItems?.size ?: 0
                                            Text(
                                                text = if (totalCount > 1) "$savingProgressIndex/$totalCount Downloaded" else "Saving...",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        isDownloadSuccess -> {
                                            Text("Saved to Downloads!", fontWeight = FontWeight.Bold)
                                        }
                                        else -> {
                                            Icon(
                                                imageVector = Icons.Filled.Download,
                                                contentDescription = "Download"
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Download Media", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Previews
                        AnimatedVisibility(
                            visible = parsedMediaItems != null,
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(200))
                        ) {
                            val mediaList = parsedMediaItems ?: emptyList()
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh)
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        text = if (mediaList.size > 1) "${mediaList.size} Files Found" else "Preview Content",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.onSurface
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        mediaList.forEachIndexed { idx, item -> MediaThumbnailItem(item, idx, urlInput) }
                                    }
                                }
                            }
                        }

                        // Extraction Error Card
                        AnimatedVisibility(
                            visible = extractionError != null,
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(200))
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = colorScheme.errorContainer)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Extraction Error",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                color = colorScheme.onErrorContainer,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Row {
                                            IconButton(onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("AcquaError", extractionError)
                                                clipboard.setPrimaryClip(clip)
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Filled.ContentCopy,
                                                    contentDescription = "Copy Error",
                                                    tint = colorScheme.onErrorContainer,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            TextButton(onClick = {
                                                extractionError = null
                                                showLoginPromptInError = false
                                            }) {
                                                Text("Dismiss", color = colorScheme.onErrorContainer)
                                            }
                                        }
                                    }

                                    SelectionContainer {
                                        Text(
                                            text = extractionError ?: "",
                                            style = MaterialTheme.typography.bodySmall.copy(color = colorScheme.onErrorContainer),
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }

                                    if (showLoginPromptInError) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                triggerHapticStart(context)
                                                openBrowser(urlInput, false, null)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = colorScheme.onErrorContainer,
                                                contentColor = colorScheme.errorContainer
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Lock,
                                                contentDescription = "Open Browser"
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Open in Browser",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    LinkSupportState.UNSUPPORTED -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(colorScheme.errorContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = "Invalid",
                                        tint = colorScheme.onErrorContainer,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Invalid Link",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.error
                                    ),
                                    color = colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Enter a complete HTTP or HTTPS website link.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (currentTab == 2) 1f else 0f
                            translationX = if (currentTab == 2) 0f else 2000f
                        }
                ) {
                    HistoryScreen(
                        paddingValues = PaddingValues(0.dp),
                        currentTab = currentTab,
                        onRefetch = { refetchedUrl ->
                            urlInput = refetchedUrl
                            currentTab = 0
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (currentTab == 1) 1f else 0f
                            translationX = if (currentTab == 1) 0f else 2000f
                        }
                ) {
                    BrowserSessionsScreen(
                        paddingValues = PaddingValues(0.dp),
                        useWebsiteSessions = useWebsiteSessions,
                        onUseWebsiteSessionsChange = { enabled ->
                            useWebsiteSessions = enabled
                            context.getSharedPreferences("acqua_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("acqua_use_session_cookies", enabled)
                                .apply()
                            if (enabled) {
                                SecureSessionStore.load(context)?.let { session ->
                                    InstagramDownloader.setSessionCookies(session.cookies, session.userAgent)
                                }
                            } else {
                                InstagramDownloader.clearSessionCookies()
                            }
                        },
                        websiteLogins = websiteLogins,
                        addWebsiteLogin = { name, url -> openBrowser(url, true, name) },
                        openWebsiteLogin = { origin -> openBrowser(origin, false, null) },
                        clearSelectedWebsiteData = { origins ->
                            BrowserSessionRegistry.clearWebsiteData(context, origins) {
                                runOnUiThread {
                                    websiteLogins = BrowserSessionRegistry.websiteLogins(context)
                                    browserSessionRevision.intValue++
                                }
                            }
                        },
                        clearBrowserData = {
                            BrowserSessionRegistry.clearAll(context) {
                                runOnUiThread {
                                    InstagramDownloader.clearSessionCookies()
                                    websiteLogins = emptyList()
                                    browserSessionRevision.intValue++
                                }
                            }
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (currentTab == 3) 1f else 0f
                            translationX = if (currentTab == 3) 0f else 2000f
                        }
                ) {
                    SettingsScreen(paddingValues = PaddingValues(0.dp))
                }
            }
        }
    }

    @Composable
    private fun MediaThumbnailItem(item: MediaResult, index: Int, sourceUrl: String) {
        val previewUrl = item.previewUrl
        val useWebsiteSessions = useWebsiteSessionsState.value
        var hasFailedToLoad by remember(previewUrl) { mutableStateOf(false) }
        val previewCookies = remember(previewUrl, item.url, item.requestCookies, useWebsiteSessions) {
            when {
                !useWebsiteSessions -> null
                previewUrl == null -> null
                WebLink.host(previewUrl) == WebLink.host(item.url) -> item.requestCookies
                else -> CookieManager.getInstance().getCookie(previewUrl)?.takeIf { it.isNotBlank() }
            }
        }

        val imageBitmapState = produceState<ImageBitmap?>(initialValue = null, previewUrl) {
            if (previewUrl == null) {
                value = null
                return@produceState
            }
            val resultBmp = withContext(Dispatchers.IO) {
                runCatching {
                    val rawBytes = InstagramDownloader.fetchBytes(
                        previewUrl,
                        item.referer,
                        previewCookies
                    )
                    BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)?.asImageBitmap()
                }.getOrNull()
            }
            if (resultBmp == null) {
                hasFailedToLoad = true
            }
            value = resultBmp
        }

        Column(
            modifier = Modifier.width(150.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val bitmap = imageBitmapState.value
            val previewAspectRatio = when {
                bitmap != null && bitmap.height > 0 -> bitmap.width.toFloat() / bitmap.height.toFloat()
                item.width > 0 && item.height > 0 -> item.width.toFloat() / item.height.toFloat()
                else -> 3f / 4f
            }
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .aspectRatio(previewAspectRatio)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    bitmap != null -> {
                        Image(
                            bitmap = bitmap,
                            contentDescription = if (item.isVideo) "Video Preview" else "Image Preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    previewUrl == null || hasFailedToLoad -> {
                        Icon(
                            imageVector = if (item.isVideo) Icons.Filled.Movie else Icons.Filled.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    else -> {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                if (item.isVideo && bitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Metadata badge overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${index + 1} • ${if (item.isVideo) "Video" else "Photo"}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    )
                }

                // Download button overlay
                var isDownloading by remember { mutableStateOf(false) }
                var isSuccess by remember { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()
                val context = LocalContext.current

                IconButton(
                    onClick = {
                        if (!isDownloading && !isSuccess) {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !hasWritePermission()) {
                                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                coroutineScope.launch {
                                    triggerHapticStart(context)
                                    isDownloading = true
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) {
                                            val downloadItem = if (!item.isVideo && bitmap != null) {
                                                item.copy(width = bitmap.width, height = bitmap.height)
                                            } else {
                                                item
                                            }
                                            saveMediaToSystem(downloadItem, index, sourceUrl, context)
                                        }
                                    }
                                    isDownloading = false
                                    if (result.isSuccess) {
                                        triggerHapticComplete(context)
                                        isSuccess = true
                                        Toast.makeText(context, "Saved to Downloads!", Toast.LENGTH_SHORT).show()
                                        delay(2000)
                                        isSuccess = false
                                    } else {
                                        val errMsg = result.exceptionOrNull()?.message ?: "Failed to save"
                                        Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSuccess) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                            else Color.Black.copy(alpha = 0.5f)
                        )
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (isSuccess) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Saved",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Download Item",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Resolution and Size Metadata
            val resolvedWidth = if (!item.isVideo && bitmap != null) bitmap.width else item.width
            val resolvedHeight = if (!item.isVideo && bitmap != null) bitmap.height else item.height
            val resText = if (resolvedWidth > 0 && resolvedHeight > 0) "${resolvedWidth}x${resolvedHeight}" else ""
            val sizeText = item.fileSize?.let {
                String.format("%.1f MB", it / (1024.0 * 1024.0))
            } ?: ""

            if (resText.isNotEmpty() || sizeText.isNotEmpty()) {
                val metaText = listOf(resText, sizeText).filter { it.isNotEmpty() }.joinToString("\n")
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    private fun saveMediaToSystem(item: MediaResult, index: Int, sourceUrl: String, context: Context): Uri? {
        val mediaUrl = item.url
        val isVideo = item.isVideo

        val sharedPrefs = context.getSharedPreferences("acqua_prefs", Context.MODE_PRIVATE)
        val baseFolder = sharedPrefs.getString("acqua_base_folder", "Acqua") ?: "Acqua"
        val categorize = sharedPrefs.getBoolean("acqua_categorize_media", false)
        val requestCookies = item.requestCookies.takeIf {
            sharedPrefs.getBoolean("acqua_use_session_cookies", false)
        }
        val formatPattern = sharedPrefs.getString("acqua_filename_format", DEFAULT_FILENAME_FORMAT)
            ?: DEFAULT_FILENAME_FORMAT

        val relativePath = if (categorize) {
            "Download/$baseFolder/" + (if (isVideo) "Videos" else "Images")
        } else {
            "Download/$baseFolder"
        }

        val name = formatFilename(formatPattern, item.username, item.width, item.height, index, isVideo)
        val mime = if (isVideo) "video/mp4" else "image/jpeg"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw Exception("Could not insert metadata record in MediaStore Downloads table")

            try {
                val bytesDownloaded = resolver.openOutputStream(uri)?.use { outputStream ->
                    InstagramDownloader.downloadToStream(
                        mediaUrl,
                        outputStream,
                        isVideo,
                        item.referer,
                        requestCookies
                    )
                } ?: throw Exception("Could not open the destination media file.")

                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                addToHistory(
                    context,
                    HistoryItem(
                        id = java.util.UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        url = sourceUrl,
                        fileName = name,
                        fileUri = uri.toString(),
                        isVideo = isVideo,
                        mimeType = mime,
                        sizeBytes = bytesDownloaded,
                        isDownloaded = true,
                        thumbnailUrl = item.thumbnailUrl ?: item.previewUrl
                    )
                )
                return uri
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                throw error
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(downloadsDir, if (categorize) "$baseFolder/" + (if (isVideo) "Videos" else "Images") else baseFolder)
            targetDir.mkdirs()
            val file = File(targetDir, name)
            try {
                val bytesDownloaded = file.outputStream().use { outputStream ->
                    InstagramDownloader.downloadToStream(
                        mediaUrl,
                        outputStream,
                        isVideo,
                        item.referer,
                        requestCookies
                    )
                }
                val uri = Uri.fromFile(file)
                addToHistory(
                    context,
                    HistoryItem(
                        id = java.util.UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        url = sourceUrl,
                        fileName = name,
                        fileUri = uri.toString(),
                        isVideo = isVideo,
                        mimeType = mime,
                        sizeBytes = bytesDownloaded,
                        isDownloaded = true,
                        thumbnailUrl = item.thumbnailUrl ?: item.previewUrl
                    )
                )
                return uri
            } catch (error: Exception) {
                file.delete()
                throw error
            }
        }
    }

    private fun detectLinkSupport(url: String): LinkSupportState {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return LinkSupportState.EMPTY
        return if (WebLink.normalize(trimmed) != null) LinkSupportState.SUPPORTED else LinkSupportState.UNSUPPORTED
    }

    private fun hasWritePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    // SQLite Database Helpers
    class AcquaDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE history (
                    id TEXT PRIMARY KEY,
                    timestamp INTEGER,
                    url TEXT,
                    file_name TEXT,
                    file_uri TEXT,
                    is_video INTEGER,
                    mime_type TEXT,
                    size_bytes INTEGER,
                    is_downloaded INTEGER,
                    thumbnail_url TEXT
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                runCatching {
                    db.execSQL("ALTER TABLE history ADD COLUMN thumbnail_url TEXT")
                }
            }
        }

        companion object {
            const val DATABASE_NAME = "acqua_history.db"
            const val DATABASE_VERSION = 2
        }
    }

    private fun loadHistoryFromDb(context: Context): List<HistoryItem> {
        val dbHelper = AcquaDbHelper(context)
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "history",
            null,
            null,
            null,
            null,
            null,
            "timestamp DESC"
        )
        val items = mutableListOf<HistoryItem>()
        with(cursor) {
            while (moveToNext()) {
                val thumbUrl = runCatching { getString(getColumnIndexOrThrow("thumbnail_url")) }.getOrNull()
                items.add(
                    HistoryItem(
                        id = getString(getColumnIndexOrThrow("id")),
                        timestamp = getLong(getColumnIndexOrThrow("timestamp")),
                        url = getString(getColumnIndexOrThrow("url")),
                        fileName = getString(getColumnIndexOrThrow("file_name")),
                        fileUri = getString(getColumnIndexOrThrow("file_uri")),
                        isVideo = getInt(getColumnIndexOrThrow("is_video")) == 1,
                        mimeType = getString(getColumnIndexOrThrow("mime_type")),
                        sizeBytes = getLong(getColumnIndexOrThrow("size_bytes")),
                        isDownloaded = getInt(getColumnIndexOrThrow("is_downloaded")) == 1,
                        thumbnailUrl = thumbUrl
                    )
                )
            }
        }
        cursor.close()
        db.close()
        return items
    }

    private fun addHistoryEntry(context: Context, item: HistoryItem) {
        val dbHelper = AcquaDbHelper(context)
        val db = dbHelper.writableDatabase

        var idToUse = item.id
        if (!item.isDownloaded) {
            val cursor = db.query(
                "history",
                arrayOf("id"),
                "url = ? AND is_downloaded = 0",
                arrayOf(item.url),
                null, null, null
            )
            if (cursor.moveToFirst()) {
                idToUse = cursor.getString(0)
            }
            cursor.close()
        }

        val values = ContentValues().apply {
            put("id", idToUse)
            put("timestamp", item.timestamp)
            put("url", item.url)
            put("file_name", item.fileName)
            put("file_uri", item.fileUri)
            put("is_video", if (item.isVideo) 1 else 0)
            put("mime_type", item.mimeType)
            put("size_bytes", item.sizeBytes)
            put("is_downloaded", if (item.isDownloaded) 1 else 0)
            put("thumbnail_url", item.thumbnailUrl)
        }
        db.insertWithOnConflict("history", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
    }

    private fun deleteHistoryEntry(context: Context, id: String) {
        val dbHelper = AcquaDbHelper(context)
        val db = dbHelper.writableDatabase
        db.delete("history", "id = ?", arrayOf(id))
        db.close()
    }

    private fun clearAllHistory(context: Context) {
        val dbHelper = AcquaDbHelper(context)
        val db = dbHelper.writableDatabase
        db.delete("history", null, null)
        db.close()
    }

    private fun addToHistory(context: Context, item: HistoryItem) {
        addHistoryEntry(context, item)
    }

    private fun openFile(uriString: String, mimeType: String, context: Context) {
        runCatching {
            val uri = Uri.parse(uriString)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(uriString: String, mimeType: String, context: Context) {
        runCatching {
            val uri = Uri.parse(uriString)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Media"))
        }.onFailure {
            Toast.makeText(context, "Could not share this file", Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    private fun HistoryScreen(
        paddingValues: PaddingValues,
        currentTab: Int,
        onRefetch: (String) -> Unit
    ) {
        val context = LocalContext.current
        val colorScheme = MaterialTheme.colorScheme
        var historyList by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }
        var historyTabState by remember { mutableStateOf(0) }
        val tabs = listOf("All Media", "Photos", "Videos", "Links")

        val filteredList = remember(historyList, historyTabState) {
            when (historyTabState) {
                0 -> historyList.filter { it.isDownloaded }
                1 -> historyList.filter { it.isDownloaded && !it.isVideo }
                2 -> historyList.filter { it.isDownloaded && it.isVideo }
                3 -> historyList.filter { !it.isDownloaded }
                else -> historyList
            }
        }

        LaunchedEffect(currentTab) {
            if (currentTab == 2) {
                withContext(Dispatchers.IO) {
                    historyList = loadHistoryFromDb(context)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Downloads & Links",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.onSurface
                )
                if (historyList.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            triggerHapticWarning(context)
                            clearAllHistory(context)
                            historyList = emptyList()
                            Toast.makeText(context, "History Cleared", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.error)
                    ) {
                        Text("Clear All")
                    }
                }
            }

            // Capsule Tabs Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    val selected = historyTabState == index
                    val containerColor = if (selected) colorScheme.primary else colorScheme.surfaceContainerHigh
                    val contentColor = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant

                    Surface(
                        onClick = {
                            triggerHapticStart(context)
                            historyTabState = index
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = containerColor,
                        contentColor = contentColor,
                        modifier = Modifier.height(40.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No history records found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredList) { item ->
                        HistoryRowItem(
                            item = item,
                            onOpen = {
                                triggerHapticStart(context)
                                openFile(item.fileUri, item.mimeType, context)
                            },
                            onShare = {
                                triggerHapticStart(context)
                                shareFile(item.fileUri, item.mimeType, context)
                            },
                            onDelete = {
                                triggerHapticStart(context)
                                deleteHistoryEntry(context, item.id)
                                historyList = loadHistoryFromDb(context)
                            },
                            onRefetch = {
                                triggerHapticStart(context)
                                onRefetch(item.url)
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    private fun HistoryRowItem(
        item: HistoryItem,
        onOpen: () -> Unit,
        onShare: () -> Unit,
        onDelete: () -> Unit,
        onRefetch: () -> Unit
    ) {
        val colorScheme = MaterialTheme.colorScheme
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dynamic Thumbnail Image Loader
                val previewUrl = item.thumbnailUrl
                var imageBitmap by remember(item.id, previewUrl) { mutableStateOf<ImageBitmap?>(null) }
                val context = LocalContext.current

                LaunchedEffect(item.id, previewUrl, item.fileUri, item.isDownloaded, item.isVideo) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            if (item.isDownloaded && !item.isVideo && item.fileUri.isNotEmpty()) {
                                // Load local photo file
                                val inputStream = context.contentResolver.openInputStream(Uri.parse(item.fileUri))
                                inputStream?.use { stream ->
                                    val bitmap = BitmapFactory.decodeStream(stream)
                                    if (bitmap != null) {
                                        imageBitmap = bitmap.asImageBitmap()
                                    }
                                }
                            } else if (!previewUrl.isNullOrEmpty()) {
                                // Load cached remote preview image
                                val client = OkHttpClient()
                                val request = Request.Builder().url(previewUrl).build()
                                client.newCall(request).execute().use { response ->
                                    if (response.isSuccessful) {
                                        val bytes = response.body?.bytes()
                                        if (bytes != null) {
                                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            if (bitmap != null) {
                                                imageBitmap = bitmap.asImageBitmap()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = imageBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (!item.isDownloaded) Icons.Filled.Link
                                          else if (item.isVideo) Icons.Filled.Movie
                                          else Icons.Filled.Image,
                            contentDescription = null,
                            tint = if (item.isDownloaded) colorScheme.primary.copy(alpha = 0.6f)
                                   else colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Metadata Details
                Column(modifier = Modifier.weight(1f)) {
                    val displayName = if (!item.isDownloaded) {
                        val shortcode = InstagramDownloader.extractShortcode(item.url) ?: ""
                        if (shortcode.isNotEmpty()) "Media • $shortcode" else "Media Link"
                    } else {
                        item.fileName
                    }

                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val formattedSize = if (!item.isDownloaded) {
                        item.url
                    } else if (item.sizeBytes > 0L) {
                        String.format("%.2f MB", item.sizeBytes / (1024.0 * 1024.0))
                    } else {
                        "Unknown Size"
                    }
                    val date = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(item.timestamp))

                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = formattedSize,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (!item.isDownloaded) colorScheme.primary else colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                // Row actions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isDownloaded) {
                        IconButton(onClick = onOpen) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Open File",
                                tint = colorScheme.primary
                            )
                        }
                        IconButton(onClick = onShare) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share File",
                                tint = colorScheme.primary
                            )
                        }
                    } else {
                        IconButton(onClick = onRefetch) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refetch Link",
                                tint = colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete Record",
                            tint = colorScheme.error
                        )
                    }
                }
            }
        }
    }

    private fun triggerHapticStart(context: Context) {
        val vibrator = getSystemVibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(8)
        }
    }

    private fun triggerHapticComplete(context: Context) {
        val vibrator = getSystemVibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 12, 50, 15), -1)
        }
    }

    private fun triggerHapticWarning(context: Context) {
        val vibrator = getSystemVibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }

    private fun getSystemVibrator(context: Context): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun formatFilename(
        pattern: String,
        username: String?,
        width: Int,
        height: Int,
        index: Int,
        isVideo: Boolean
    ): String {
        val dateStr = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
        val timeStr = java.text.SimpleDateFormat("HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val ext = if (isVideo) "mp4" else "jpg"

        val userVal = username ?: ""
        val resVal = if (width > 0 && height > 0) "${width}x${height}" else ""

        var name = pattern
            .replace("{username}", userVal)
            .replace("{resolution}", resVal)
            .replace("{date}", dateStr)
            .replace("{time}", timeStr)
            .replace("{index}", (index + 1).toString())

        while (name.contains("__")) {
            name = name.replace("__", "_")
        }
        name = name.trim('_')

        if (name.isEmpty()) {
            name = "acqua_${dateStr}_${timeStr}_${index + 1}"
        }

        return "$name.$ext"
    }

    @Composable
    private fun WebsiteLoginTile(
        login: BrowserSessionRegistry.WebsiteLogin,
        onClick: () -> Unit
    ) {
        val iconKey = login.iconFile?.let { "${it.absolutePath}:${it.lastModified()}" }
        val iconBitmap by produceState<ImageBitmap?>(initialValue = null, iconKey) {
            value = withContext(Dispatchers.IO) {
                login.iconFile
                    ?.takeIf(File::isFile)
                    ?.let { BitmapFactory.decodeFile(it.absolutePath) }
                    ?.asImageBitmap()
            }
        }
        val colorScheme = MaterialTheme.colorScheme

        Surface(
            onClick = onClick,
            modifier = Modifier.width(96.dp).height(112.dp),
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap!!,
                            contentDescription = "${login.name} (${login.host}) icon",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = null,
                            tint = colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = login.name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    @Composable
    private fun AddWebsiteLoginTile(onClick: () -> Unit) {
        val colorScheme = MaterialTheme.colorScheme
        Surface(
            onClick = onClick,
            modifier = Modifier.width(96.dp).height(112.dp),
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.primaryContainer
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add website login",
                    tint = colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Add login",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onPrimaryContainer
                )
            }
        }
    }

    @Composable
    private fun BrowserSessionsScreen(
        paddingValues: PaddingValues,
        useWebsiteSessions: Boolean,
        onUseWebsiteSessionsChange: (Boolean) -> Unit,
        websiteLogins: List<BrowserSessionRegistry.WebsiteLogin>,
        addWebsiteLogin: (String, String) -> Unit,
        openWebsiteLogin: (String) -> Unit,
        clearSelectedWebsiteData: (Collection<String>) -> Unit,
        clearBrowserData: () -> Unit
    ) {
        val colorScheme = MaterialTheme.colorScheme

        var showAddWebsiteDialog by remember { mutableStateOf(false) }
        var websiteNameInput by remember { mutableStateOf("") }
        var websiteUrlInput by remember { mutableStateOf("") }
        var websiteDialogError by remember { mutableStateOf<String?>(null) }
        var showManageWebsiteDataDialog by remember { mutableStateOf(false) }
        var selectedWebsiteOrigins by remember { mutableStateOf<Set<String>>(emptySet()) }

        if (showAddWebsiteDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAddWebsiteDialog = false
                    websiteNameInput = ""
                    websiteUrlInput = ""
                    websiteDialogError = null
                },
                title = { Text("Add website") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = websiteNameInput,
                            onValueChange = {
                                websiteNameInput = it.take(40)
                                websiteDialogError = null
                            },
                            label = { Text("Name") },
                            placeholder = { Text("Instagram") },
                            singleLine = true,
                            isError = websiteDialogError == "Enter a name.",
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = websiteUrlInput,
                            onValueChange = {
                                websiteUrlInput = it
                                websiteDialogError = null
                            },
                            label = { Text("Website URL") },
                            placeholder = { Text("instagram.com") },
                            singleLine = true,
                            isError = websiteDialogError == "Enter a valid HTTP or HTTPS website.",
                            modifier = Modifier.fillMaxWidth()
                        )
                        websiteDialogError?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val name = websiteNameInput.trim()
                        val normalizedUrl = WebLink.normalize(websiteUrlInput)
                        when {
                            name.isEmpty() -> websiteDialogError = "Enter a name."
                            normalizedUrl == null -> websiteDialogError = "Enter a valid HTTP or HTTPS website."
                            else -> {
                                showAddWebsiteDialog = false
                                websiteDialogError = null
                                websiteNameInput = ""
                                websiteUrlInput = ""
                                addWebsiteLogin(name, normalizedUrl)
                            }
                        }
                    }) {
                        Text("Open website")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAddWebsiteDialog = false
                        websiteNameInput = ""
                        websiteUrlInput = ""
                        websiteDialogError = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showManageWebsiteDataDialog) {
            val allOrigins = websiteLogins.map { it.origin }.toSet()
            AlertDialog(
                onDismissRequest = {
                    showManageWebsiteDataDialog = false
                    selectedWebsiteOrigins = emptySet()
                },
                title = { Text("Manage website data") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (websiteLogins.isEmpty()) {
                            Text(
                                text = "No saved website bookmarks. You can still clear all shared browser data.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Select websites",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                TextButton(onClick = {
                                    selectedWebsiteOrigins = if (selectedWebsiteOrigins == allOrigins) {
                                        emptySet()
                                    } else {
                                        allOrigins
                                    }
                                }) {
                                    Text(if (selectedWebsiteOrigins == allOrigins) "Deselect all" else "Select all")
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                websiteLogins.forEach { login ->
                                    val selected = login.origin in selectedWebsiteOrigins
                                    Surface(
                                        onClick = {
                                            selectedWebsiteOrigins = if (selected) {
                                                selectedWebsiteOrigins - login.origin
                                            } else {
                                                selectedWebsiteOrigins + login.origin
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) {
                                            colorScheme.primaryContainer
                                        } else {
                                            colorScheme.surfaceContainer
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(checked = selected, onCheckedChange = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(login.name, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    login.host,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            text = "Selected websites: clears their cookies, site storage, bookmark, and icon. Clear all also removes the shared WebView cache, form data, and HTTP authentication.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                showManageWebsiteDataDialog = false
                                selectedWebsiteOrigins = emptySet()
                                clearBrowserData()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear all browser data", color = colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val selected = selectedWebsiteOrigins
                            showManageWebsiteDataDialog = false
                            selectedWebsiteOrigins = emptySet()
                            clearSelectedWebsiteData(selected)
                        },
                        enabled = selectedWebsiteOrigins.isNotEmpty()
                    ) {
                        Text("Clear selected")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showManageWebsiteDataDialog = false
                        selectedWebsiteOrigins = emptySet()
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "Browser & Sessions",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Text(
                text = "Website Sessions",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = colorScheme.primary),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.errorContainer.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, colorScheme.errorContainer.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Warning",
                                tint = colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Browse responsibly",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Only sign in to websites you trust and download content you are allowed to access. Acqua never stores your password.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use sessions for downloads",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Allow extraction and downloads to use saved website logins.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = useWebsiteSessions,
                            onCheckedChange = onUseWebsiteSessionsChange
                        )
                    }

                    Text(
                        text = "Website Logins",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (websiteLogins.isEmpty()) {
                            "Tap + to name a website and enter its URL."
                        } else {
                            "Tap a bookmark to reopen its saved browser session."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        websiteLogins.forEach { login ->
                            WebsiteLoginTile(login = login) {
                                openWebsiteLogin(login.origin)
                            }
                        }
                        AddWebsiteLoginTile(onClick = { showAddWebsiteDialog = true })
                    }

                    OutlinedButton(
                        onClick = {
                            selectedWebsiteOrigins = emptySet()
                            showManageWebsiteDataDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    ) {
                        Text("Manage Website Data")
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    @Composable
    private fun SettingsScreen(paddingValues: PaddingValues) {
        val context = LocalContext.current
        val colorScheme = MaterialTheme.colorScheme
        var baseFolderInput by remember { mutableStateOf("") }
        var categorizeMediaState by remember { mutableStateOf(false) }
        var filenameFormatInput by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            val sharedPrefs = context.getSharedPreferences("acqua_prefs", Context.MODE_PRIVATE)
            baseFolderInput = sharedPrefs.getString("acqua_base_folder", "Acqua") ?: "Acqua"
            categorizeMediaState = sharedPrefs.getBoolean("acqua_categorize_media", false)
            filenameFormatInput = sharedPrefs.getString(
                "acqua_filename_format",
                DEFAULT_FILENAME_FORMAT
            ) ?: DEFAULT_FILENAME_FORMAT
        }

        fun saveFilenameFormat(value: String) {
            filenameFormatInput = value
            context.getSharedPreferences("acqua_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("acqua_filename_format", value)
                .apply()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Text(
                text = "Downloads & Filenames",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                ),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = baseFolderInput,
                        onValueChange = {
                            baseFolderInput = it
                            context.getSharedPreferences("acqua_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putString("acqua_base_folder", it)
                                .apply()
                        },
                        label = { Text("Base Download Folder") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Categorize by Media Type",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Save to Images/ and Videos/ subdirectories",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = categorizeMediaState,
                            onCheckedChange = { checked ->
                                categorizeMediaState = checked
                                context.getSharedPreferences("acqua_prefs", Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("acqua_categorize_media", checked)
                                    .apply()
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    OutlinedTextField(
                        value = filenameFormatInput,
                        onValueChange = ::saveFilenameFormat,
                        label = { Text("Filename Format Pattern") },
                        placeholder = { Text(DEFAULT_FILENAME_FORMAT) },
                        supportingText = {
                            Text("Tap a variable below to add or remove it.")
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FILENAME_VARIABLES.forEach { variable ->
                            val selected = filenameFormatInput.contains(variable)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    saveFilenameFormat(
                                        toggleFilenameVariable(
                                            pattern = filenameFormatInput,
                                            variable = variable
                                        )
                                    )
                                },
                                label = { Text(variable) },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else {
                                    null
                                }
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { saveFilenameFormat(DEFAULT_FILENAME_FORMAT) },
                        enabled = filenameFormatInput != DEFAULT_FILENAME_FORMAT,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset to Acqua default")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    private fun toggleFilenameVariable(pattern: String, variable: String): String {
        if (!pattern.contains(variable)) {
            return when {
                pattern.isBlank() -> variable
                pattern.last() in listOf('_', '-', ' ', '.') -> "$pattern$variable"
                else -> "${pattern}_$variable"
            }
        }

        var updated = pattern.replace(variable, "")
        while (updated.contains("__") || updated.contains("--") || updated.contains("  ")) {
            updated = updated
                .replace("__", "_")
                .replace("--", "-")
                .replace("  ", " ")
        }
        return updated.trim('_', '-', ' ')
    }
}

data class HistoryItem(
    val id: String,
    val timestamp: Long,
    val url: String,
    val fileName: String,
    val fileUri: String,
    val isVideo: Boolean,
    val mimeType: String,
    val sizeBytes: Long,
    val isDownloaded: Boolean,
    val thumbnailUrl: String? = null
)
