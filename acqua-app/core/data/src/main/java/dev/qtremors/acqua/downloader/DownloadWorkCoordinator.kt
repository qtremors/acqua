package dev.qtremors.acqua.downloader

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.annotation.RequiresApi
import dev.qtremors.acqua.domain.ResolvedMedia
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterNotNull
import java.util.UUID
import java.util.concurrent.TimeUnit

class DownloadWorkCoordinator(context: Context, private val profiles: dev.qtremors.acqua.data.session.CookieProfileStore = dev.qtremors.acqua.data.session.CookieProfileStore(context)) : DownloadQueueGateway {
    private val applicationContext = context.applicationContext
    private val workManager = WorkManager.getInstance(context.applicationContext)
    private val requests = DownloadRequestStore(applicationContext, profiles)
    private val settings = dev.qtremors.acqua.data.settings.AppSettingsRepository(applicationContext)
    private val pendingLinks = PendingLinkStore(applicationContext)
    private val tasks = DownloadTaskStore(applicationContext)
    private val library = DownloadLibraryStore(applicationContext)
    private val userInitiatedScheduler = if (Build.VERSION.SDK_INT >= 34) {
        UserInitiatedDownloadScheduler(applicationContext)
    } else {
        null
    }

    init {
        if (Build.VERSION.SDK_INT >= 34) UserInitiatedDownloadRegistry.reconcile(applicationContext)
    }

    override fun enqueue(
        media: ResolvedMedia,
        outputMedia: ResolvedMedia,
        options: YtDlpDownloadOptions,
        sourceUrl: String
    ): UUID {
        CommandTemplateStore(applicationContext).validateAccepted(options)
        val accepted = DownloadStreams.accepted(media, options.copy(formatPolicy = options.formatPolicy.accepted()))
        DownloadAdvancedOptions.arguments(media, accepted)
        val input = DownloadWorkData.processedRequest(media, outputMedia, accepted, sourceUrl)
        val authorization = accepted.cookieProfileId?.let { profiles.accepted(it, sourceUrl, media.authorizedSession, accepted.cookieProfileFingerprint) }
        return enqueue(input, MediaTimelineSource.encode(if (MediaTimeline.active(accepted)) media else media.copy(chapters = emptyList())), authorization)
    }

    private fun enqueue(input: androidx.work.Data, timeline: String? = null, authorization: String? = null): UUID {
        val profileId = DownloadWorkData.decode(input)?.options?.cookieProfileId
        val sourceUrl = input.getString(DownloadWorkData.KEY_SOURCE_URL) ?: throw IllegalArgumentException("Missing source URL")
        val acceptedSession = profileId?.let { id ->
            if (authorization == null) profiles.accepted(id, sourceUrl) else authorization.also {
                val profile = profiles.restore(it)
                if (profile.id != id) throw dev.qtremors.acqua.data.session.CookieProfileException()
                profiles.requireAuthorized(profile, sourceUrl)
            }
        }
        val id = UUID.randomUUID()
        val snapshot = if (input.getString(DownloadWorkData.KEY_STORAGE) != null) input else {
            androidx.work.Data.Builder().putAll(input)
                .putString(DownloadWorkData.KEY_STORAGE, DownloadOptionsCodec.encodeStorage(settings.downloadSettings().let { storage ->
                    val options = DownloadWorkData.decode(input)?.options
                    storage.copy(filenamePattern = options?.filenamePatternOverride ?: storage.filenamePattern,
                        audioFilenamePattern = options?.audioFilenamePatternOverride ?: storage.audioFilenamePattern)
                }))
                .apply {
                    if (input.getString(DownloadWorkData.KEY_BACKEND) == dev.qtremors.acqua.domain.MediaBackend.DIRECT.name &&
                        input.getString(DownloadWorkData.KEY_OPTIONS) == null) {
                        val processing = settings.mediaProcessingSettings()
                        putBoolean(DownloadWorkData.KEY_EMBED_METADATA, processing.embedMetadata)
                        putBoolean(DownloadWorkData.KEY_ORIGINAL_METADATA, processing.preserveOriginalMetadata)
                    }
                }.build()
        }
        requests.put(id, androidx.work.Data.Builder().putAll(snapshot)
            .putString(DownloadWorkData.KEY_PUBLICATION_ID, snapshot.getString(DownloadWorkData.KEY_PUBLICATION_ID) ?: id.toString()).build(), timeline, acceptedSession)
        val reference = androidx.work.Data.Builder().putString(DownloadWorkData.KEY_REQUEST_REF, id.toString()).build()
        if (Build.VERSION.SDK_INT >= 34) {
            scheduleUserInitiated(reference, id)?.let { return it }
        }
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setId(id)
            .setInputData(reference)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
        DownloadNameOverrides.register(applicationContext, request.id.toString())
        try { workManager.enqueue(request) } catch (error: Exception) {
            requests.remove(id)
            DownloadNameOverrides.finish(applicationContext, id.toString())
            throw error
        }
        return request.id
    }

    override fun enqueueDirect(media: ResolvedMedia, index: Int, sourceUrl: String, itemCount: Int): UUID {
        val input = DownloadWorkData.directRequest(media, index, sourceUrl, itemCount)
        return enqueue(input)
    }

    override fun enqueueDirectConfigured(media: ResolvedMedia, index: Int, sourceUrl: String, itemCount: Int,
        options: YtDlpDownloadOptions): UUID {
        require(media.unavailableReason == null && !MediaTimeline.active(options)) {
            "Inspect this entry with the processing engine before clipping or splitting chapters."
        }
        return enqueue(androidx.work.Data.Builder().putAll(DownloadWorkData.directRequest(media, index, sourceUrl, itemCount))
            .putString(DownloadWorkData.KEY_OPTIONS, DownloadOptionsCodec.encode(options)).build(), authorization = options.cookieProfileId?.let {
                profiles.accepted(it, sourceUrl, media.authorizedSession, options.cookieProfileFingerprint)
            })
    }

    fun observe(id: UUID): Flow<DownloadQueueItem> {
        return if (userInitiatedScheduler != null &&
            UserInitiatedDownloadRegistry.contains(applicationContext, id)
        ) {
            UserInitiatedDownloadRegistry.observe(applicationContext)
                .map { it[id]?.let(::enrich) }
                .filterNotNull()
        } else {
            workManager.getWorkInfoByIdFlow(id).filterNotNull().map { enrich(toQueueItem(it)) }
        }
    }

    override fun observe(ids: List<UUID>): Flow<List<DownloadQueueItem>> =
        combine(ids.map(::observe)) { it.toList() }

    fun observeAll(): Flow<List<DownloadQueueItem>> {
        val workItems = workManager.getWorkInfosByTagFlow(WORK_TAG).map { jobs ->
            jobs.map(::toQueueItem)
        }
        val userItems = if (userInitiatedScheduler != null) {
            UserInitiatedDownloadRegistry.observe(applicationContext).map { items -> items.values.toList() }
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
        return combine(workItems, userItems, tasks.observe(), DownloadRequestStore.changes) { work, user, _, _ ->
            val platform = (work + user).map(::enrich)
            val discarded = tasks.record(platform)
            val active = platform.filter(DownloadQueueItem::isActive)
            if (discarded.isNotEmpty()) {
                requests.discard(discarded)
                UserInitiatedDownloadRegistry.removeTerminals(applicationContext, discarded)
            }
            (tasks.read() + active).associateBy(DownloadQueueItem::id).values
                .filterNot { it.id in tasks.hidden() }.map(::enrich)
        }
    }

    override fun observeQueue(): Flow<DownloadQueueSnapshot> = observeAll().map { jobs ->
        DownloadQueueSnapshot(
            jobs.sortedBy(DownloadQueueItem::createdAt)
        )
    }

    override fun cancel(id: UUID) {
        checkpointDownloadTask(applicationContext, DownloadQueueItem(id.toString(), DownloadQueueState.CANCELLED))
        DownloadNameOverrides.finish(applicationContext, id.toString())
        if (Build.VERSION.SDK_INT >= 34 && userInitiatedScheduler != null &&
            UserInitiatedDownloadRegistry.contains(applicationContext, id)
        ) {
            cancelUserInitiated(id)
        } else {
            workManager.cancelWorkById(id)
        }
    }

    override fun rename(id: UUID, name: String): Boolean {
        if (!DownloadNameOverrides.rename(applicationContext, id.toString(), name)) return false
        requests.input(id)?.let { input -> requests.put(id, androidx.work.Data.Builder().putAll(input)
            .putString(DownloadWorkData.KEY_DOWNLOAD_NAME, name.trim()).build()) }
        requests.changed()
        return true
    }

    override fun retry(id: UUID): UUID? = synchronized(RETRY_LOCK) {
        requests.replacement(id)?.let { return@synchronized it }
        val state = UserInitiatedDownloadRegistry.observe(applicationContext).value[id]?.state
        val terminal = if (state != null) state in setOf(DownloadQueueState.FAILED, DownloadQueueState.CANCELLED)
            else workManager.getWorkInfoById(id).get()?.let { it.state in setOf(WorkInfo.State.FAILED, WorkInfo.State.CANCELLED) }
                ?: (tasks.read().firstOrNull { it.id == id.toString() }?.state in setOf(DownloadQueueState.FAILED, DownloadQueueState.CANCELLED))
        if (!terminal) return@synchronized null
        requests.input(id)?.let { enqueue(it, requests.timeline(id), requests.authorization(id)) }?.also { requests.recordReplacement(id, it) }
    }

    override fun diagnostic(id: UUID): DownloadDiagnostic? = DownloadDiagnosticsStore(applicationContext).get(id.toString())

    override fun observePending() = pendingLinks.observe()
    override fun savePending(text: String) = pendingLinks.save(text)
    override fun updatePending(link: PendingLink) = pendingLinks.update(link)
    override fun removePending(url: String) = pendingLinks.remove(url)
    override fun observeArchive() = library.observeArchive()
    override fun removeArchive(key: String) = library.removeArchive(key)
    override fun observePresets() = library.observePresets()
    override fun savePreset(preset: DownloadPreset) = library.savePreset(preset)
    override fun removePreset(name: String) = library.removePreset(name)
    override fun forgetTask(id: UUID) {
        require(UserInitiatedDownloadRegistry.observe(applicationContext).value[id]?.isActive != true &&
            workManager.getWorkInfoById(id).get()?.state?.isFinished != false) { "Active tasks cannot be removed." }
        tasks.dismiss(id.toString())
        requests.discard(listOf(id.toString()))
        UserInitiatedDownloadRegistry.remove(applicationContext, id)
        workManager.pruneWork()
    }

    private fun enrich(item: DownloadQueueItem): DownloadQueueItem {
        val request = runCatching { requests.input(UUID.fromString(item.id))?.let(DownloadWorkData::decode) }.getOrNull()
        return item.copy(title = request?.media?.downloadName ?: request?.media?.title ?: request?.media?.username ?: item.title,
            sourceUrl = request?.sourceUrl ?: item.sourceUrl, createdAt = requests.created(item.id).takeIf { it > 0 } ?: item.createdAt,
            artifactCount = maxOf(item.artifactCount, requests.publishedCount(request?.publicationId ?: item.id)),
            acceptedOptions = request?.options ?: item.acceptedOptions,
            acceptedFolder = request?.storageSettings?.baseFolder ?: item.acceptedFolder,
            acceptedKind = request?.acceptedKind() ?: item.acceptedKind,
            canRetry = request != null && requests.replacement(UUID.fromString(item.id)) == null &&
                item.state in setOf(DownloadQueueState.FAILED, DownloadQueueState.CANCELLED))
    }

    @RequiresApi(34)
    private fun scheduleUserInitiated(input: androidx.work.Data, id: UUID): UUID? =
        userInitiatedScheduler?.let { scheduler ->
            runCatching { scheduler.schedule(input, id) }.getOrNull()
        }

    @RequiresApi(34)
    private fun cancelUserInitiated(id: UUID) {
        userInitiatedScheduler?.cancel(id)
    }

    private fun toQueueItem(job: WorkInfo) = DownloadQueueItem(
        id = job.id.toString(),
        state = when (job.state) {
            WorkInfo.State.ENQUEUED -> if (job.runAttemptCount > 0) {
                DownloadQueueState.RETRYING
            } else {
                DownloadQueueState.QUEUED
            }
            WorkInfo.State.RUNNING -> if (DownloadWorkData.phase(job.progress) == DownloadPhase.QUEUED) DownloadQueueState.QUEUED else DownloadQueueState.RUNNING
            WorkInfo.State.BLOCKED -> DownloadQueueState.QUEUED
            WorkInfo.State.SUCCEEDED -> if (job.outputData.getBoolean(DownloadWorkData.KEY_OUTCOME, false)) DownloadQueueState.SKIPPED else DownloadQueueState.SUCCEEDED
            WorkInfo.State.FAILED -> DownloadQueueState.FAILED
            WorkInfo.State.CANCELLED -> DownloadQueueState.CANCELLED
        },
        progress = job.progress.getFloat(DownloadWorkData.KEY_PROGRESS, 0f),
        etaSeconds = job.progress.getLong(DownloadWorkData.KEY_ETA_SECONDS, 0L),
        downloadedBytes = job.progress.getLong(DownloadWorkData.KEY_DOWNLOADED_BYTES, 0L),
        totalBytes = job.progress.getLong(DownloadWorkData.KEY_TOTAL_BYTES, 0L),
        error = job.outputData.getString(DownloadWorkData.KEY_ERROR),
        artifactCount = job.outputData.getInt(DownloadWorkData.KEY_ARTIFACT_COUNT, 0),
        phase = when (job.state) {
            WorkInfo.State.ENQUEUED -> if (job.runAttemptCount > 0) {
                DownloadWorkData.phase(job.progress)
            } else {
                DownloadPhase.QUEUED
            }
            WorkInfo.State.RUNNING,
            WorkInfo.State.BLOCKED -> DownloadWorkData.phase(job.progress)
            else -> DownloadPhase.COMPLETE
        }
    )

    private companion object {
        val RETRY_LOCK = Any()
        const val WORK_TAG = "acqua-yt-dlp-download"
    }
}
