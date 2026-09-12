package dev.qtremors.acqua.data.updater

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.trackedReposDataStore by preferencesDataStore(name = "acqua_tracked_repos_prefs")

class TrackedRepoRepository(private val context: Context) {
    companion object {
        private val REPOS = stringPreferencesKey("tracked_repositories")
        private val DEFAULTS_SEEDED = booleanPreferencesKey("default_repositories_seeded")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val serializer = ListSerializer(TrackedRepo.serializer())

    val trackedRepos: Flow<List<TrackedRepo>> = context.trackedReposDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences -> decode(preferences[REPOS]).getOrDefault(emptyList()) }

    suspend fun addOrUpdateRepo(repo: TrackedRepo) {
        context.trackedReposDataStore.edit { preferences ->
            val repos = decode(preferences[REPOS]).getOrDefault(emptyList()).toMutableList()
            val index = repos.indexOfFirst { it.fullName.equals(repo.fullName, ignoreCase = true) }
            if (index >= 0) repos[index] = repo.copy(addedAt = repos[index].addedAt)
            else repos += repo
            preferences[REPOS] = json.encodeToString(serializer, repos)
        }
    }

    suspend fun ensureDefaultRepos(): Boolean {
        var seeded = false
        context.trackedReposDataStore.edit { preferences ->
            if (preferences[DEFAULTS_SEEDED] == true) return@edit
            val repos = decode(preferences[REPOS]).getOrDefault(emptyList()).toMutableList()
            val existing = repos.mapTo(hashSetOf()) { it.fullName.lowercase() }
            repos += DefaultTrackedRepos.repositories.filter { it.fullName.lowercase() !in existing }
            preferences[REPOS] = json.encodeToString(serializer, repos)
            preferences[DEFAULTS_SEEDED] = true
            seeded = true
        }
        return seeded
    }

    suspend fun removeRepo(fullName: String) {
        context.trackedReposDataStore.edit { preferences ->
            val repos = decode(preferences[REPOS]).getOrDefault(emptyList())
                .filterNot { it.fullName.equals(fullName, ignoreCase = true) }
            preferences[REPOS] = json.encodeToString(serializer, repos)
        }
    }

    suspend fun updateAll(repos: List<TrackedRepo>) {
        context.trackedReposDataStore.edit { it[REPOS] = json.encodeToString(serializer, repos) }
    }

    suspend fun exportBackup(): String = json.encodeToString(
        TrackedRepoBackup.serializer(),
        TrackedRepoBackup(repositories = trackedReposSnapshot())
    )

    suspend fun importBackup(jsonString: String): Result<Int> = runCatching {
        val imported = runCatching {
            json.decodeFromString(TrackedRepoBackup.serializer(), jsonString).repositories
        }.recoverCatching {
            json.decodeFromString(serializer, jsonString)
        }.getOrThrow()
        require(imported.all { it.owner.isNotBlank() && it.name.isNotBlank() && it.fullName == "${it.owner}/${it.name}" }) {
            "Backup contains an invalid repository"
        }
        val merged = trackedReposSnapshot().associateBy { it.fullName.lowercase() }.toMutableMap()
        imported.forEach { merged[it.fullName.lowercase()] = it }
        updateAll(merged.values.sortedBy(TrackedRepo::addedAt))
        imported.size
    }

    private suspend fun trackedReposSnapshot(): List<TrackedRepo> = trackedRepos.first()

    private fun decode(value: String?): Result<List<TrackedRepo>> = runCatching {
        if (value.isNullOrBlank()) emptyList() else json.decodeFromString(serializer, value)
    }.recoverCatching { error ->
        if (error is SerializationException || error is IllegalArgumentException) emptyList() else throw error
    }
}

@Serializable
data class TrackedRepoBackup(
    val schemaVersion: Int = 1,
    val repositories: List<TrackedRepo>
)
