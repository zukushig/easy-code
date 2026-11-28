package com.codetree.data.progress

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.codetree.data.platform.toAppError
import com.codetree.domain.common.AppResult
import com.codetree.domain.content.ContentRepository
import com.codetree.domain.content.ProgressRepository
import com.codetree.domain.model.LearningStats
import com.codetree.domain.model.LearningStatus
import com.codetree.domain.model.TopicProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 学习进度仓库（DataStore Preferences，docs/specs/knowledge.md §7：只存用户数据，不写回内容包）。
 *
 * 存储格式：每个 Topic 一个键 `progress.<topicId>`，值为
 * `"<status>|<updatedAtEpochMs>|<lastVisitedAtEpochMs>"`（枚举名 + 两个时间戳），
 * 避免每条记录一段 JSON 的解析开销；未知/损坏记录按未学习处理，不崩溃。
 */
class ProgressRepositoryImpl(
    context: Context,
    private val contentProvider: () -> ContentRepository? = { null },
) : ProgressRepository {

    private val dataStore: DataStore<Preferences> = context.applicationContext.progressDataStore

    override fun observeStatus(topicId: String): Flow<LearningStatus> =
        dataStore.data
            .map { prefs -> decode(topicId, prefs[stringPreferencesKey(PREFIX + topicId)])?.status ?: LearningStatus.NOT_STARTED }
            .distinctUntilChanged()

    override fun observeAll(): Flow<Map<String, TopicProgress>> =
        dataStore.data.map { prefs -> decodeAll(prefs) }

    override suspend fun setStatus(topicId: String, status: LearningStatus): AppResult<Unit> {
        if (topicId.isBlank()) return AppResult.Failure(com.codetree.domain.common.AppError.Internal("topicId 不能为空"))
        return runCatching {
            val now = System.currentTimeMillis()
            dataStore.edit { prefs ->
                val key = stringPreferencesKey(PREFIX + topicId)
                val current = decode(topicId, prefs[key])
                prefs[key] = encode(
                    TopicProgress(
                        topicId = topicId,
                        status = status,
                        updatedAtEpochMs = now,
                        lastVisitedAtEpochMs = current?.lastVisitedAtEpochMs ?: 0L,
                    ),
                )
            }
            AppResult.Success(Unit)
        }.getOrElse { AppResult.Failure(it.toAppError("保存学习进度")) }
    }

    override suspend fun markVisited(topicId: String): AppResult<Unit> {
        if (topicId.isBlank()) return AppResult.Success(Unit)
        return runCatching {
            val now = System.currentTimeMillis()
            dataStore.edit { prefs ->
                val key = stringPreferencesKey(PREFIX + topicId)
                val current = decode(topicId, prefs[key])
                prefs[key] = encode(
                    TopicProgress(
                        topicId = topicId,
                        status = current?.status ?: LearningStatus.NOT_STARTED,
                        updatedAtEpochMs = current?.updatedAtEpochMs ?: now,
                        lastVisitedAtEpochMs = now,
                    ),
                )
            }
            AppResult.Success(Unit)
        }.getOrElse { AppResult.Failure(it.toAppError("记录访问")) }
    }

    override suspend fun stats(): LearningStats {
        val progress = try {
            observeAll().first()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            emptyMap()
        }
        val total = contentProvider()?.graph()?.topics?.size ?: progress.size
        val mastered = progress.values.count { it.status == LearningStatus.MASTERED }
        val learning = progress.values.count { it.status == LearningStatus.LEARNING }
        return LearningStats(
            totalTopics = total,
            mastered = mastered,
            learning = learning,
            notStarted = (total - mastered - learning).coerceAtLeast(0),
        )
    }

    // ------------------------------------------------------------------ 编解码

    private fun decodeAll(prefs: Preferences): Map<String, TopicProgress> {
        val result = LinkedHashMap<String, TopicProgress>()
        for ((key, value) in prefs.asMap()) {
            if (!key.name.startsWith(PREFIX)) continue
            if (value !is String) continue
            val topicId = key.name.removePrefix(PREFIX)
            val decoded = decode(topicId, value) ?: continue
            result[topicId] = decoded
        }
        return result
    }

    private fun encode(progress: TopicProgress): String =
        "${progress.status.name}|${progress.updatedAtEpochMs}|${progress.lastVisitedAtEpochMs}"

    private fun decode(topicId: String, raw: String?): TopicProgress? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split('|')
        if (parts.size != 3) return null
        val status = runCatching { LearningStatus.valueOf(parts[0]) }.getOrNull() ?: return null
        val updatedAt = parts[1].toLongOrNull() ?: return null
        val visitedAt = parts[2].toLongOrNull() ?: return null
        return TopicProgress(topicId = topicId, status = status, updatedAtEpochMs = updatedAt, lastVisitedAtEpochMs = visitedAt)
    }

    companion object {
        private const val PREFIX = "progress."
        const val DATA_STORE_NAME = "codetree_progress"
    }
}

private val Context.progressDataStore by preferencesDataStore(name = ProgressRepositoryImpl.DATA_STORE_NAME)
