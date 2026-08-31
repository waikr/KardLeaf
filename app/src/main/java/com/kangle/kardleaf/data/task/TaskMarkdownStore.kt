package com.kangle.kardleaf.data.task

import android.content.Context
import android.os.SystemClock
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.database.TaskDao
import com.kangle.kardleaf.data.database.TaskEntity
import com.kangle.kardleaf.data.database.TaskGroupEntity
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.repository.MetadataManager
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.repository.RoomNoteRepository
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.data.utils.KardLeafLogTags
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class DuplicateTaskIdConflict(
    val id: Long,
    val first: MarkdownTaskItem,
    val second: MarkdownTaskItem,
)

data class TaskMarkdownSyncResult(
    val success: Boolean,
    val duplicateTaskIdConflict: DuplicateTaskIdConflict? = null,
    val missingManagedFile: MissingTaskMarkdownFile? = null,
)

data class MissingTaskMarkdownFile(
    val path: String,
    val activeTaskCount: Int,
)

data class SavedTaskBatch(
    val task: TaskEntity,
    val children: List<TaskEntity>,
)

enum class TaskSaveFailureReason {
    MissingManagedFile,
    DuplicateTaskId,
    EditConflict,
    InvalidParent,
    ParentCycle,
    InvalidSubtask,
    FileWrite,
    PermissionDenied,
    IoError,
    CacheUpdate,
    Unknown,
}

data class TaskSaveFailure(
    val reason: TaskSaveFailureReason,
    val message: String,
)

sealed interface TaskSaveResult {
    data class Success(val batch: SavedTaskBatch) : TaskSaveResult

    data class Failure(val failure: TaskSaveFailure) : TaskSaveResult
}

class TaskMarkdownStore(
    private val context: Context,
    private val noteRepository: RoomNoteRepository,
    private val taskDao: TaskDao = AppDatabase.getDatabase(context.applicationContext).taskDao(),
    private val prefsManager: PrefsManager = PrefsManager(context.applicationContext),
) {
    private data class SynchronizeResult(
        val success: Boolean,
        val current: Note?,
        val duplicateTaskIdConflict: DuplicateTaskIdConflict? = null,
        val missingManagedFile: MissingTaskMarkdownFile? = null,
    )

    private fun SynchronizeResult.toTaskSaveFailure(): TaskSaveFailure =
        when {
            duplicateTaskIdConflict != null ->
                TaskSaveFailure(
                    TaskSaveFailureReason.DuplicateTaskId,
                    "任务清单中存在重复 ID（${duplicateTaskIdConflict.id}），请先解决冲突",
                )
            missingManagedFile != null ->
                TaskSaveFailure(
                    TaskSaveFailureReason.MissingManagedFile,
                    "任务清单文件不存在，请先恢复或创建：${missingManagedFile.path}",
                )
            current != null && !current.content.contains(MANAGED_MARKER) ->
                TaskSaveFailure(TaskSaveFailureReason.FileWrite, "任务清单不是 KardLeaf 管理文件，未覆盖原文件")
            else -> TaskSaveFailure(TaskSaveFailureReason.Unknown, "任务清单同步失败，请检查文件后重试")
        }

    suspend fun synchronize(): TaskMarkdownSyncResult =
        writeMutex.withLock {
            val operationId = nextOperationId()
            val startedAt = SystemClock.elapsedRealtime()
            val result = synchronizeLocked()
            KardLeafLog.i(
                LOG_TAG,
                "sync finished op=$operationId success=${result.success} path=${currentManagedNotePath()} " +
                    "missing=${result.missingManagedFile != null} duplicate=${result.duplicateTaskIdConflict != null} " +
                    "elapsed=${SystemClock.elapsedRealtime() - startedAt}ms",
            )
            TaskMarkdownSyncResult(result.success, result.duplicateTaskIdConflict, result.missingManagedFile)
        }

    suspend fun inspectMissingManagedFile(): MissingTaskMarkdownFile? =
        writeMutex.withLock {
            val path = currentManagedNotePath()
            if (!migrateLegacyManagedNoteIfNeeded()) return@withLock null
            if (findManagedNote(path) != null) return@withLock null
            val activeTaskCount = taskDao.getAllTasksSnapshot().count { !it.isTrashed }
            activeTaskCount.takeIf { it > 0 }?.let { count ->
                KardLeafLog.i(LOG_TAG, "startup missing managed task file path=$path activeTasks=$count")
                MissingTaskMarkdownFile(path, count)
            }
        }

    private suspend fun synchronizeLocked(
        groupsForRecovery: List<TaskGroupEntity>? = null,
    ): SynchronizeResult {
        val managedPath = currentManagedNotePath()
        if (!migrateLegacyManagedNoteIfNeeded()) return SynchronizeResult(false, null)
        var current = findManagedNote(managedPath)
        val tasks = taskDao.getAllTasksSnapshot()
        var groups = groupsForRecovery ?: taskDao.getAllGroupsSnapshot()
        if (groupsForRecovery != null) {
            taskDao.renameGroupsKeepingIds(groupsForRecovery)
        }
        if (current == null) {
            val activeTaskCount = tasks.count { !it.isTrashed }
            if (activeTaskCount > 0) {
                KardLeafLog.w(
                    LOG_TAG,
                    "managed task Markdown missing path=$managedPath activeTasks=$activeTaskCount " +
                        "trashedTasks=${tasks.count(TaskEntity::isTrashed)}; waiting for user decision",
                )
                return SynchronizeResult(
                    success = false,
                    current = null,
                    missingManagedFile = MissingTaskMarkdownFile(managedPath, activeTaskCount),
                )
            }
            prefsManager.clearPendingTaskTrashIds()
            prefsManager.clearPendingTaskDeleteIds()
            prefsManager.clearPendingTaskRestoreIds()
            KardLeafLog.i(
                LOG_TAG,
                "managed task Markdown absent with no active tasks path=$managedPath; leaving file absent",
            )
            return SynchronizeResult(true, null)
        }
        if (!current.content.contains(MANAGED_MARKER)) {
            KardLeafLog.w(
                LOG_TAG,
                "sync abort unmanaged note path=$managedPath contentLength=${current.content.length}",
            )
            return SynchronizeResult(false, current)
        }

        var parsed =
            parseMarkdownTasks(
                listOf(MarkdownTaskSource(managedPath, MANAGED_NOTE_TITLE, current.content)),
            )
        if (parsed.any { it.taskId == null }) {
            val nextId =
                maxOf(
                    taskDao.getMaxTaskId(),
                    parsed.mapNotNull { it.taskId?.removePrefix(TASK_ID_PREFIX)?.toLongOrNull() }.maxOrNull() ?: 0L,
                ) + 1L
            val contentWithIds = addManagedTaskIds(current.content, parsed, nextId)
            val savedPath =
                noteRepository.saveNoteFromQuickEditor(
                    current.copy(content = contentWithIds, lastModified = Date()),
                    current.file,
                    saveHistory = false,
                )
            if (savedPath != managedPath) {
                KardLeafLog.w(LOG_TAG, "sync assign ids save failed path=$managedPath")
                return SynchronizeResult(false, current)
            }
            current = findManagedNote(managedPath) ?: return SynchronizeResult(false, null)
            parsed =
                parseMarkdownTasks(
                    listOf(MarkdownTaskSource(managedPath, MANAGED_NOTE_TITLE, contentWithIds)),
                )
        }

        val parsedPairs =
            parsed.mapNotNull { item ->
                item.managedTaskId()?.let { it to item }
            }
        val duplicate = parsedPairs.groupBy { it.first }.entries.firstOrNull { it.value.size > 1 }
        if (duplicate != null) {
            val (id, matches) = duplicate
            val conflict = DuplicateTaskIdConflict(id, matches[0].second, matches[1].second)
            KardLeafLog.e(
                LOG_TAG,
                "duplicate managed task id=$id lines=${conflict.first.lineNumber},${conflict.second.lineNumber}",
            )
            return SynchronizeResult(false, current, conflict)
        }
        val parsedById = parsedPairs.toMap()
        val pendingTrashIds = prefsManager.getPendingTaskTrashIds()
        val pendingDeleteIds = prefsManager.getPendingTaskDeleteIds()
        val pendingRestoreIds = prefsManager.getPendingTaskRestoreIds()
        val parentIds = buildParentIds(parsed)
        val orderById = parsedPairs.mapIndexed { index, (id, _) -> id to index.toLong() }.toMap()

        var nextGroupId = (groups.maxOfOrNull { it.id } ?: 0L) + 1L
        var nextGroupOrder = (groups.maxOfOrNull { it.sortOrder } ?: -1) + 1
        managedGroupNames(current.content).filterNot { name -> groups.any { it.name == name } }.forEach { name ->
            val group = TaskGroupEntity(nextGroupId++, name, nextGroupOrder++, System.currentTimeMillis())
            taskDao.insertGroup(group)
            groups = groups + group
        }
        val groupIdsByLine = managedTaskGroupIds(current.content, groups)
        val now = System.currentTimeMillis()
        tasks.forEach { task ->
            val item = parsedById[task.id]
            if (item == null) {
                when {
                    task.id in pendingDeleteIds -> taskDao.delete(task)
                    task.isTrashed -> return@forEach
                    task.id in pendingTrashIds -> {
                    taskDao.update(task.copy(isTrashed = true, updatedAt = now))
                    }
                    else -> taskDao.delete(task)
                }
                TaskReminderScheduler(context).cancel(task.id)
                KardLeafLog.w(
                    LOG_TAG,
                    "managed task removed from Markdown taskId=${task.id} " +
                        "cacheAction=${when {
                            task.id in pendingDeleteIds -> "permanent_delete"
                            task.isTrashed -> "keep_trash"
                            task.id in pendingTrashIds -> "move_to_trash"
                            else -> "remove"
                        }}",
                )
                return@forEach
            }
            if (task.isTrashed && task.id !in pendingRestoreIds) return@forEach
            val updated =
                task.copy(
                    taskText = item.taskText.ifBlank { task.taskText },
                    done = item.done,
                    groupId = groupIdsByLine[item.lineNumber],
                    priority = item.priorityMarker.toTaskPriority(task.priority),
                    dueAt = item.dueDate.toTaskTimestamp(task.dueAt, defaultHour = 23, defaultMinute = 59),
                    reminderAt = item.scheduledDate.toTaskTimestamp(task.reminderAt, defaultHour = 9, defaultMinute = 0),
                    repeatRule = item.recurrenceRule.toTaskRepeat(task.repeatRule),
                    notes = item.notes,
                    parentTaskId = parentIds[task.id],
                    manualOrder = orderById[task.id] ?: task.manualOrder,
                    isTrashed = false,
                    updatedAt = if (taskMatchesMarkdown(task, item, groupIdsByLine[item.lineNumber])) task.updatedAt else now,
                )
            if (updated != task) {
                taskDao.update(updated)
                TaskReminderScheduler(context).schedule(updated)
            }
        }
        val existingIds = tasks.mapTo(mutableSetOf()) { it.id }
        parsedById.forEach { (id, item) ->
            if (id <= 0L || id in existingIds) return@forEach
            val imported =
                TaskEntity(
                    id = id,
                    taskText = item.taskText.ifBlank { "未命名任务" },
                    done = item.done,
                    reminderAt = item.scheduledDate.toTaskTimestamp(null, defaultHour = 9, defaultMinute = 0),
                    groupId = groupIdsByLine[item.lineNumber],
                    priority = item.priorityMarker.toTaskPriority(0),
                    dueAt = item.dueDate.toTaskTimestamp(null, defaultHour = 23, defaultMinute = 59),
                    repeatRule = item.recurrenceRule.toTaskRepeat("NONE"),
                    notes = item.notes,
                    createdAt = item.createdDate.toTaskTimestamp(null, defaultHour = 0, defaultMinute = 0) ?: now,
                    updatedAt = item.doneDate.toTaskTimestamp(null, defaultHour = 23, defaultMinute = 59) ?: now,
                    parentTaskId = parentIds[id],
                    manualOrder = orderById[id] ?: 0L,
                )
            taskDao.insert(imported)
            TaskReminderScheduler(context).schedule(imported)
        }
        if (pendingTrashIds.isNotEmpty()) prefsManager.clearPendingTaskTrashIds(pendingTrashIds)
        if (pendingDeleteIds.isNotEmpty()) prefsManager.clearPendingTaskDeleteIds(pendingDeleteIds)
        if (pendingRestoreIds.isNotEmpty()) prefsManager.clearPendingTaskRestoreIds(pendingRestoreIds)
        return SynchronizeResult(true, current)
    }

    suspend fun saveTask(
        original: TaskEntity?,
        candidate: TaskEntity,
        parentTaskId: Long? = null,
    ): TaskEntity? = saveTaskBatch(original, candidate, parentTaskId)?.task

    suspend fun saveTaskBatch(
        original: TaskEntity?,
        candidate: TaskEntity,
        parentTaskId: Long? = null,
        childTaskTexts: List<String> = emptyList(),
        updatedSubtasks: List<TaskEntity> = emptyList(),
    ): SavedTaskBatch? =
        (saveTaskBatchResult(
            original = original,
            candidate = candidate,
            parentTaskId = parentTaskId,
            childTaskTexts = childTaskTexts,
            updatedSubtasks = updatedSubtasks,
        ) as? TaskSaveResult.Success)?.batch

    suspend fun saveTaskBatchResult(
        original: TaskEntity?,
        candidate: TaskEntity,
        parentTaskId: Long? = null,
        childTaskTexts: List<String> = emptyList(),
        updatedSubtasks: List<TaskEntity> = emptyList(),
    ): TaskSaveResult =
        writeMutex.withLock {
            val operationId = nextOperationId()
            val startedAt = SystemClock.elapsedRealtime()
            KardLeafLog.d(
                KardLeafLogTags.TASK_SAVE,
                "batch start op=$operationId taskId=${original?.id ?: candidate.id} originalId=${original?.id ?: 0} " +
                    "titleLen=${candidate.taskText.length} notesLen=${candidate.notes.length} " +
                    "childInputs=${childTaskTexts.size} nonBlankChildren=${childTaskTexts.count(String::isNotBlank)} " +
                    "updatedSubtasks=${updatedSubtasks.size}",
            )
            val sync = synchronizeLocked()
            if (!sync.success) {
                KardLeafLog.w(
                    KardLeafLogTags.TASK_SAVE,
                    "batch stop op=$operationId stage=synchronize success=false elapsed=${SystemClock.elapsedRealtime() - startedAt}ms",
                )
                return@withLock TaskSaveResult.Failure(sync.toTaskSaveFailure())
            }
            val currentTasks = taskDao.getAllTasksSnapshot()
            val currentOriginal = original?.let { value -> currentTasks.firstOrNull { it.id == value.id } }
            if (original != null &&
                (currentOriginal == null || currentOriginal.copy(manualOrder = original.manualOrder) != original)
            ) {
                KardLeafLog.w(LOG_TAG, "reject stale task edit taskId=${original.id}")
                KardLeafLog.w(KardLeafLogTags.TASK_SAVE, "batch stop op=$operationId stage=stale-task taskId=${original.id}")
                return@withLock TaskSaveResult.Failure(
                    TaskSaveFailure(TaskSaveFailureReason.EditConflict, "任务已被外部修改，请重新打开后再保存"),
                )
            }
            val descendantIds = original?.let { TaskHierarchy.descendants(currentTasks, setOf(it.id)) }.orEmpty()
            if (updatedSubtasks.any { it.id !in descendantIds } ||
                updatedSubtasks.map { it.id }.distinct().size != updatedSubtasks.size
            ) {
                KardLeafLog.w(LOG_TAG, "reject invalid subtask edit taskId=${original?.id ?: 0}")
                KardLeafLog.w(
                    KardLeafLogTags.TASK_SAVE,
                    "batch stop op=$operationId stage=invalid-subtasks taskId=${original?.id ?: 0}",
                )
                return@withLock TaskSaveResult.Failure(
                    TaskSaveFailure(TaskSaveFailureReason.InvalidSubtask, "子任务已变化，请重新打开后再保存"),
                )
            }
            val editedSubtasks = updatedSubtasks.mapNotNull { candidateSubtask ->
                val currentSubtask = currentTasks.firstOrNull { it.id == candidateSubtask.id }
                    ?: return@mapNotNull null
                candidateSubtask.taskText.trim().takeIf(String::isNotBlank)?.let { text ->
                    currentSubtask.copy(
                        taskText = text,
                        notes = candidateSubtask.notes.trim(),
                        updatedAt = candidateSubtask.updatedAt,
                    )
                }
            }
            if (editedSubtasks.size != updatedSubtasks.size) {
                KardLeafLog.w(LOG_TAG, "reject missing subtask edit taskId=${original?.id ?: 0}")
                KardLeafLog.w(
                    KardLeafLogTags.TASK_SAVE,
                    "batch stop op=$operationId stage=missing-subtask taskId=${original?.id ?: 0}",
                )
                return@withLock TaskSaveResult.Failure(
                    TaskSaveFailure(TaskSaveFailureReason.InvalidSubtask, "子任务已不存在，请重新打开后再保存"),
                )
            }
            val groups = taskDao.getAllGroupsSnapshot()
            val nextId = taskDao.getMaxTaskId() + 1L
            val childTexts = childTaskTexts.map(String::trim).filter(String::isNotBlank)
            val nextOrder = (currentTasks.minOfOrNull(TaskEntity::manualOrder) ?: 0L) - childTexts.size - 1L
            val taskId = original?.id ?: nextId
            val requestedParentId =
                when {
                    original == null -> parentTaskId
                    original.groupId != candidate.groupId -> null
                    else -> original.parentTaskId
                }
            val task =
                candidate.copy(
                    id = taskId,
                    groupId = candidate.groupId?.takeIf { groupId -> groups.any { it.id == groupId } },
                    manualOrder = currentOriginal?.manualOrder ?: nextOrder,
                )
            val validParentId =
                requestedParentId?.takeIf { parentId ->
                    currentTasks.any { parent ->
                        parent.id == parentId && !parent.isTrashed && parent.groupId == task.groupId
                    }
                }
            if (requestedParentId != null && validParentId == null) {
                KardLeafLog.w(
                    LOG_TAG,
                    "reject invalid task parent taskId=${task.id} parentId=$requestedParentId groupId=${task.groupId}",
                )
                KardLeafLog.w(KardLeafLogTags.TASK_SAVE, "batch stop op=$operationId stage=invalid-parent taskId=${task.id}")
                return@withLock TaskSaveResult.Failure(
                    TaskSaveFailure(TaskSaveFailureReason.InvalidParent, "任务层级关系无效，请重新打开后再试"),
                )
            }
            if (validParentId != null) {
                val parentIds = currentTasks.associate { it.id to it.parentTaskId }
                if (TaskHierarchy.createsCycle(parentIds, task.id, validParentId)) {
                    KardLeafLog.w(LOG_TAG, "reject task parent cycle taskId=${task.id} parentId=$validParentId")
                    KardLeafLog.w(KardLeafLogTags.TASK_SAVE, "batch stop op=$operationId stage=parent-cycle taskId=${task.id}")
                    return@withLock TaskSaveResult.Failure(
                        TaskSaveFailure(TaskSaveFailureReason.ParentCycle, "任务层级关系会形成循环，请重新打开后再试"),
                    )
                }
            }
            val savedTask = task.copy(parentTaskId = validParentId)
            val children =
                childTexts.mapIndexed { index, text ->
                    TaskEntity(
                        id = nextId + if (original == null) index + 1L else index.toLong(),
                        taskText = text,
                        groupId = savedTask.groupId,
                        createdAt = savedTask.updatedAt,
                        updatedAt = savedTask.updatedAt,
                        parentTaskId = savedTask.id,
                        manualOrder = savedTask.manualOrder + index + 1L,
                    )
                }
            val movedDescendants =
                if (original != null && original.groupId != savedTask.groupId) {
                    val descendantIds = TaskHierarchy.descendants(currentTasks, setOf(savedTask.id), includeRoots = true) - savedTask.id
                    currentTasks.filter { it.id in descendantIds }.map { descendant ->
                        descendant.copy(groupId = savedTask.groupId, updatedAt = savedTask.updatedAt)
                    }
                } else {
                    emptyList()
                }
            val changedTasks = listOf(savedTask) + editedSubtasks + movedDescendants + children
            val nextTasks = currentTasks.filterNot { current -> changedTasks.any { it.id == current.id } } + changedTasks
            KardLeafLog.d(
                KardLeafLogTags.TASK_SAVE,
                "batch markdown start op=$operationId taskId=${savedTask.id} changedTasks=${changedTasks.size} " +
                    "children=${children.size}",
            )
            val persistFailure = persistWithFailure(nextTasks, groups, sync.current)
            if (persistFailure != null) {
                KardLeafLog.w(KardLeafLogTags.TASK_SAVE, "batch stop op=$operationId stage=markdown success=false taskId=${savedTask.id}")
                return@withLock TaskSaveResult.Failure(persistFailure)
            }
            KardLeafLog.d(KardLeafLogTags.TASK_SAVE, "batch markdown success op=$operationId taskId=${savedTask.id}")
            if (!updateCacheAfterMarkdown("save") { taskDao.upsertTasks(changedTasks) }) {
                KardLeafLog.w(KardLeafLogTags.TASK_SAVE, "batch stop op=$operationId stage=room success=false taskId=${savedTask.id}")
                return@withLock TaskSaveResult.Failure(
                    TaskSaveFailure(TaskSaveFailureReason.CacheUpdate, "任务文件已写入，但任务缓存更新失败，请重新打开任务页"),
                )
            }
            KardLeafLog.i(
                KardLeafLogTags.TASK_SAVE,
                "batch success op=$operationId taskId=${savedTask.id} children=${children.size} " +
                    "elapsed=${SystemClock.elapsedRealtime() - startedAt}ms",
            )
            TaskSaveResult.Success(SavedTaskBatch(savedTask, children))
        }

    suspend fun moveTasksToTrash(tasksToTrash: List<TaskEntity>): Boolean =
        writeMutex.withLock {
            val requestedIds = tasksToTrash.mapTo(hashSetOf()) { it.id }
            val sync = synchronizeLocked()
            if (!sync.success) {
                return@withLock false
            }
            val currentTasks = taskDao.getAllTasksSnapshot()
            val selectedIds = TaskHierarchy.descendants(currentTasks, requestedIds, includeRoots = true)
            if (selectedIds.isEmpty()) {
                return@withLock true
            }
            val now = System.currentTimeMillis()
            val updatedTasks =
                currentTasks.map { task ->
                    if (task.id in selectedIds) task.copy(isTrashed = true, updatedAt = now) else task
                }
            val groups = taskDao.getAllGroupsSnapshot()
            prefsManager.addPendingTaskTrashIds(selectedIds)
            val persisted = persist(updatedTasks, groups, sync.current)
            if (!persisted) {
                prefsManager.clearPendingTaskTrashIds(selectedIds)
                return@withLock false
            }
            val changed = updatedTasks.filter { it.id in selectedIds }
            if (!updateCacheAfterMarkdown("trash") { taskDao.upsertTasks(changed) }) {
                return@withLock false
            }
            prefsManager.clearPendingTaskTrashIds(selectedIds)
            changed.forEach { TaskReminderScheduler(context).cancel(it.id) }
            true
        }

    /**
     * The user explicitly confirmed that the missing managed file should be treated as deleted.
     * There is no Markdown write to perform here; preserve the tasks in Room's task recycle bin.
     */
    suspend fun moveTasksToTrashAfterManagedFileDeletion(): Boolean =
        writeMutex.withLock {
            val path = currentManagedNotePath()
            if (findManagedNote(path) != null) {
                KardLeafLog.w(LOG_TAG, "missing-file trash confirmation ignored because file reappeared path=$path")
                return@withLock false
            }
            val currentTasks = taskDao.getAllTasksSnapshot()
            val activeTasks = currentTasks.filterNot(TaskEntity::isTrashed)
            if (activeTasks.isEmpty()) {
                KardLeafLog.i(LOG_TAG, "missing-file trash confirmation no active tasks path=$path")
                return@withLock true
            }
            val now = System.currentTimeMillis()
            return@withLock try {
                taskDao.upsertTasks(activeTasks.map { it.copy(isTrashed = true, updatedAt = now) })
                activeTasks.forEach { TaskReminderScheduler(context).cancel(it.id) }
                KardLeafLog.i(
                    LOG_TAG,
                    "missing-file deletion confirmed; moved tasks to recycle bin path=$path count=${activeTasks.size}",
                )
                true
            } catch (error: Throwable) {
                KardLeafLog.e(
                    LOG_TAG,
                    "missing-file deletion confirmed but task recycle-bin update failed path=$path count=${activeTasks.size}",
                    error,
                )
                false
            }
        }

    suspend fun restoreTasks(tasksToRestore: List<TaskEntity>): Boolean =
        writeMutex.withLock {
            val sync = synchronizeLocked()
            if (!sync.success) return@withLock false
            val selectedIds = tasksToRestore.mapTo(hashSetOf()) { it.id }
            if (selectedIds.isEmpty()) return@withLock true
            val currentTasks = taskDao.getAllTasksSnapshot()
            val restoredIds = TaskHierarchy.descendants(currentTasks, selectedIds, includeRoots = true)
            val now = System.currentTimeMillis()
            val updatedTasks =
                currentTasks.map { task ->
                    if (task.id in restoredIds) {
                        task.copy(
                            isTrashed = false,
                            parentTaskId = task.parentTaskId?.takeIf { it in restoredIds || currentTasks.any { parent -> parent.id == it && !parent.isTrashed } },
                            updatedAt = now,
                        )
                    } else {
                        task
                    }
                }
            val groups = taskDao.getAllGroupsSnapshot()
            prefsManager.addPendingTaskRestoreIds(restoredIds)
            if (!persist(updatedTasks, groups, sync.current)) {
                prefsManager.clearPendingTaskRestoreIds(restoredIds)
                return@withLock false
            }
            val changed = updatedTasks.filter { it.id in restoredIds }
            if (!updateCacheAfterMarkdown("restore") { taskDao.upsertTasks(changed) }) {
                return@withLock false
            }
            prefsManager.clearPendingTaskRestoreIds(restoredIds)
            changed.forEach { TaskReminderScheduler(context).schedule(it) }
            true
        }

    suspend fun deleteTasksPermanently(tasksToDelete: List<TaskEntity>): Boolean =
        writeMutex.withLock {
            val sync = synchronizeLocked()
            if (!sync.success) return@withLock false
            val currentTasks = taskDao.getAllTasksSnapshot()
            val selectedIds = TaskHierarchy.descendants(
                currentTasks,
                tasksToDelete.mapTo(hashSetOf()) { it.id },
                includeRoots = true,
            )
            if (selectedIds.isEmpty()) return@withLock true
            val remainingTasks = currentTasks.filterNot { it.id in selectedIds }
            val groups = taskDao.getAllGroupsSnapshot()
            prefsManager.addPendingTaskDeleteIds(selectedIds)
            if (!persist(remainingTasks, groups, sync.current)) {
                prefsManager.clearPendingTaskDeleteIds(selectedIds)
                return@withLock false
            }
            val deleted = currentTasks.filter { it.id in selectedIds }
            if (deleted.isEmpty()) {
                prefsManager.clearPendingTaskDeleteIds(selectedIds)
                return@withLock true
            }
            if (!updateCacheAfterMarkdown("permanent delete") { taskDao.deleteTasks(deleted) }) {
                return@withLock false
            }
            prefsManager.clearPendingTaskDeleteIds(selectedIds)
            deleted.forEach { TaskReminderScheduler(context).cancel(it.id) }
            true
        }

    suspend fun moveTasksToGroup(
        tasksToMove: List<TaskEntity>,
        groupId: Long?,
        updatedAt: Long,
    ): Boolean =
        writeMutex.withLock {
            val sync = synchronizeLocked()
            if (!sync.success) return@withLock false
            val currentTasks = taskDao.getAllTasksSnapshot()
            val requestedIds = tasksToMove.mapTo(hashSetOf()) { it.id }
            val selectedIds = TaskHierarchy.descendants(currentTasks, requestedIds, includeRoots = true)
            if (selectedIds.isEmpty()) return@withLock true
            val tasksById = currentTasks.associateBy { it.id }
            val updatedTasks =
                currentTasks.map { task ->
                    if (task.id in selectedIds) {
                        task.copy(
                            groupId = groupId,
                            parentTaskId =
                                task.parentTaskId?.takeIf { parentId ->
                                    parentId in selectedIds || tasksById[parentId]?.takeUnless(TaskEntity::isTrashed)?.groupId == groupId
                                },
                            updatedAt = updatedAt,
                        )
                    } else {
                        task
                    }
                }
            if (!persist(updatedTasks, taskDao.getAllGroupsSnapshot(), sync.current)) return@withLock false
            if (!updateCacheAfterMarkdown("move") {
                taskDao.upsertTasks(updatedTasks.filter { it.id in selectedIds })
            }) return@withLock false
            true
        }

    suspend fun setTaskDone(
        task: TaskEntity,
        updated: TaskEntity,
    ): Pair<TaskEntity, TaskEntity?>? =
        writeMutex.withLock {
            val sync = synchronizeLocked()
            if (!sync.success) {
                KardLeafLog.w(LOG_TAG, "setTaskDone synchronize failed taskId=${task.id}")
                return@withLock null
            }
            val currentTasks = taskDao.getAllTasksSnapshot()
            val current =
                currentTasks.firstOrNull { it.id == task.id } ?: run {
                    KardLeafLog.w(LOG_TAG, "setTaskDone task missing after synchronize taskId=${task.id}")
                    return@withLock null
                }
            val merged = current.copy(done = updated.done, updatedAt = updated.updatedAt)
            val next =
                nextTaskOccurrence(current, updated.updatedAt)
                    ?.takeIf {
                        updated.done && !current.done &&
                            current.done == task.done && current.updatedAt == task.updatedAt
                    }
                    ?.copy(
                        id = taskDao.getMaxTaskId() + 1L,
                        parentTaskId = current.parentTaskId,
                        manualOrder = current.manualOrder + 1L,
                    )
            val tasks = currentTasks.filterNot { it.id == task.id } + merged + listOfNotNull(next)
            if (!persist(tasks, taskDao.getAllGroupsSnapshot(), sync.current)) {
                KardLeafLog.w(LOG_TAG, "setTaskDone persist failed taskId=${task.id}")
                return@withLock null
            }
            if (!updateCacheAfterMarkdown("complete") {
                taskDao.update(merged)
                next?.let { taskDao.insert(it) }
            }) return@withLock null
            merged to next
        }

    suspend fun undoTaskCompletion(
        completedTask: TaskEntity,
        nextOccurrence: TaskEntity?,
    ): Boolean =
        writeMutex.withLock {
            val sync = synchronizeLocked()
            if (!sync.success) return@withLock false
            val currentTasks = taskDao.getAllTasksSnapshot()
            val current = currentTasks.firstOrNull { it.id == completedTask.id } ?: return@withLock false
            val nextId = nextOccurrence?.id
            val restored = current.copy(done = false, updatedAt = System.currentTimeMillis())
            val remainingTasks =
                currentTasks
                    .filterNot { it.id == current.id || it.id == nextId }
                    .plus(restored)
            if (!persist(remainingTasks, taskDao.getAllGroupsSnapshot(), sync.current)) return@withLock false
            if (!updateCacheAfterMarkdown("undo completion") {
                taskDao.update(restored)
                currentTasks.firstOrNull { it.id == nextId }?.let { next -> taskDao.delete(next) }
            }) return@withLock false
            nextId?.let { TaskReminderScheduler(context).cancel(it) }
            TaskReminderScheduler(context).schedule(restored)
            true
        }

    suspend fun moveTask(
        task: TaskEntity,
        groupId: Long?,
        updatedAt: Long,
    ): Boolean = moveTasksToGroup(listOf(task), groupId, updatedAt)

    suspend fun reorderTasks(orderedTaskIds: List<Long>): Boolean =
        writeMutex.withLock {
            if (orderedTaskIds.size < 2) return@withLock true
            if (orderedTaskIds.distinct().size != orderedTaskIds.size) return@withLock false
            val sync = synchronizeLocked()
            if (!sync.success) return@withLock false
            val currentTasks = taskDao.getAllTasksSnapshot()
            val orderedTasks = orderedTaskIds.mapNotNull { id -> currentTasks.firstOrNull { it.id == id } }
            if (orderedTasks.size != orderedTaskIds.size) return@withLock false
            val selectedIds = orderedTaskIds.toHashSet()
            val orderedIterator = orderedTasks.iterator()
            val reorderedById =
                currentTasks.sortedWith(compareBy<TaskEntity> { it.manualOrder }.thenBy { it.id })
                    .map { task -> if (task.id in selectedIds) orderedIterator.next() else task }
                    .mapIndexed { index, task -> task.copy(manualOrder = index.toLong()) }
                    .associateBy { it.id }
            val nextTasks = currentTasks.map { reorderedById[it.id] ?: it }
            if (!persist(nextTasks, taskDao.getAllGroupsSnapshot(), sync.current)) return@withLock false
            if (!updateCacheAfterMarkdown("reorder") { taskDao.upsertTasks(reorderedById.values.toList()) }) {
                return@withLock false
            }
            true
        }

    suspend fun saveGroup(
        group: TaskGroupEntity?,
        name: String,
    ): TaskGroupEntity? =
        writeMutex.withLock {
            val sync = synchronizeLocked()
            if (!sync.success) return@withLock null
            val groups = taskDao.getAllGroupsSnapshot()
            val saved =
                if (group == null) {
                    TaskGroupEntity(
                        id = taskDao.getMaxGroupId() + 1L,
                        name = name,
                        sortOrder = taskDao.getMaxGroupOrder() + 1,
                        createdAt = System.currentTimeMillis(),
                    )
                } else {
                    group.copy(name = name)
                }
            val nextGroups =
                if (group == null) {
                    groups + saved
                } else {
                    renameGroupPaths(groups, group.name, saved.name)
                }
            // 写入前先校验整棵子树映射：目标路径与子树外已有分组冲突（或映射内部重复）时整次拒绝，
            // Markdown 与 Room 都保持不变。
            val targetNameAlreadyUsed =
                group != null && groups.any { it.id != group.id && it.name == saved.name }
            val duplicatedName = firstDuplicatedGroupName(nextGroups)
            if (targetNameAlreadyUsed || duplicatedName != null) {
                KardLeafLog.w(
                    LOG_TAG,
                    "reject group save name=${saved.name} conflict=${saved.name.takeIf { targetNameAlreadyUsed } ?: duplicatedName}",
                )
                return@withLock null
            }
            val renamedGroups =
                if (group == null) {
                    emptyList()
                } else {
                    nextGroups.filter { next ->
                        groups.firstOrNull { it.id == next.id }?.name != next.name
                    }
                }
            val tasks = taskDao.getAllTasksSnapshot()
            if (!persist(tasks, nextGroups, sync.current)) return@withLock null
            if (!updateCacheAfterMarkdown("save group", nextGroups.takeIf { group != null }) {
                    if (group == null) {
                        taskDao.insertGroup(saved)
                    } else {
                        taskDao.renameGroupsKeepingIds(renamedGroups)
                    }
                }
            ) {
                return@withLock null
            }
            saved
        }

    suspend fun swapGroups(
        first: TaskGroupEntity,
        second: TaskGroupEntity,
    ): Boolean =
        writeMutex.withLock {
            val sync = synchronizeLocked()
            if (!sync.success) return@withLock false
            val originalGroups = taskDao.getAllGroupsSnapshot()
            val groups =
                originalGroups.map {
                    when (it.id) {
                        first.id -> it.copy(sortOrder = second.sortOrder)
                        second.id -> it.copy(sortOrder = first.sortOrder)
                        else -> it
                    }
                }
            val tasks = taskDao.getAllTasksSnapshot()
            if (!persist(tasks, groups, sync.current)) return@withLock false
            if (!updateCacheAfterMarkdown("reorder groups") {
                    taskDao.swapGroupOrder(first, second)
                }
            ) {
                return@withLock false
            }
            true
        }

    suspend fun deleteGroup(group: TaskGroupEntity): Boolean =
        writeMutex.withLock {
            val sync = synchronizeLocked()
            if (!sync.success) return@withLock false
            val originalTasks = taskDao.getAllTasksSnapshot()
            val tasks =
                originalTasks.map {
                    if (it.groupId == group.id) it.copy(groupId = null, updatedAt = System.currentTimeMillis()) else it
                }
            val originalGroups = taskDao.getAllGroupsSnapshot()
            val groups = originalGroups.filterNot { it.id == group.id }
            if (!persist(tasks, groups, sync.current)) return@withLock false
            if (!updateCacheAfterMarkdown("delete group") {
                    taskDao.deleteGroupKeepingTasks(group, System.currentTimeMillis())
                }
            ) {
                return@withLock false
            }
            true
        }

    suspend fun setMarkdownTaskDone(
        item: MarkdownTaskItem,
        done: Boolean,
    ): Boolean =
        writeMutex.withLock {
            val current = noteRepository.getNote(item.notePath) ?: return@withLock false
            val patched =
                patchMarkdownTaskDone(current.content, item, done) as? MarkdownTaskPatchResult.Success
                    ?: return@withLock false
            noteRepository.saveNoteFromQuickEditor(
                current.copy(content = patched.content, lastModified = Date()),
                current.file,
                saveHistory = false,
            ).isNotBlank()
        }

    suspend fun resolveDuplicateTaskId(
        conflict: DuplicateTaskIdConflict,
        firstTaskId: String,
        firstTaskText: String,
        secondTaskId: String,
        secondTaskText: String,
    ): Boolean =
        writeMutex.withLock {
            val firstId = normalizeManagedTaskId(firstTaskId) ?: return@withLock false
            val secondId = normalizeManagedTaskId(secondTaskId) ?: return@withLock false
            if (firstId == secondId || firstTaskText.isBlank() || secondTaskText.isBlank()) return@withLock false

            val current = findManagedNote(currentManagedNotePath()) ?: return@withLock false
            val items =
                parseMarkdownTasks(
                    listOf(MarkdownTaskSource(currentManagedNotePath(), MANAGED_NOTE_TITLE, current.content)),
                )
            val first =
                items.firstOrNull { it.lineNumber == conflict.first.lineNumber && it.rawLine == conflict.first.rawLine }
                    ?: return@withLock false
            val second =
                items.firstOrNull { it.lineNumber == conflict.second.lineNumber && it.rawLine == conflict.second.rawLine }
                    ?: return@withLock false
            val conflictLines = setOf(first.lineNumber, second.lineNumber)
            val occupiedIds = items.filterNot { it.lineNumber in conflictLines }.mapNotNull { it.taskId }.toSet()
            if (firstId in occupiedIds || secondId in occupiedIds) return@withLock false

            val firstPatched =
                patchMarkdownTaskFields(current.content, first, firstTaskText, firstId)
                    as? MarkdownTaskPatchResult.Success
                    ?: return@withLock false
            val secondPatched =
                patchMarkdownTaskFields(firstPatched.content, second, secondTaskText, secondId)
                    as? MarkdownTaskPatchResult.Success
                    ?: return@withLock false
            val savedPath =
                noteRepository.saveNoteFromQuickEditor(
                    current.copy(content = secondPatched.content, lastModified = Date()),
                    current.file,
                    saveHistory = false,
                )
            savedPath == currentManagedNotePath()
        }

    private suspend fun updateCacheAfterMarkdown(
        operation: String,
        groupsForRecovery: List<TaskGroupEntity>? = null,
        update: suspend () -> Unit,
    ): Boolean {
        val operationId = nextOperationId()
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            update()
            KardLeafLog.d(
                LOG_TAG,
                "task cache updated op=$operationId operation=$operation elapsed=${SystemClock.elapsedRealtime() - startedAt}ms",
            )
            true
        } catch (error: Throwable) {
            KardLeafLog.e(
                LOG_TAG,
                "task cache failed after Markdown save op=$operationId operation=$operation " +
                    "elapsed=${SystemClock.elapsedRealtime() - startedAt}ms; Markdown kept",
                error,
            )
            val resync =
                runCatching { synchronizeLocked(groupsForRecovery) }
                    .onFailure { syncError ->
                        KardLeafLog.e(
                            LOG_TAG,
                            "task cache resynchronize failed op=$operationId operation=$operation; retry required",
                            syncError,
                        )
                    }
                    .getOrNull()
            KardLeafLog.w(
                LOG_TAG,
                "task cache failure result op=$operationId operation=$operation " +
                    "resyncSuccess=${resync?.success == true} missing=${resync?.missingManagedFile != null}",
            )
            false
        }
    }

    private suspend fun persist(
        tasks: List<TaskEntity>,
        groups: List<TaskGroupEntity>,
    ): Boolean = persistWithFailure(tasks, groups, findManagedNote(currentManagedNotePath())) == null

    private suspend fun persist(
        tasks: List<TaskEntity>,
        groups: List<TaskGroupEntity>,
        currentOverride: Note?,
    ): Boolean = persistWithFailure(tasks, groups, currentOverride) == null

    private suspend fun persistWithFailure(
        tasks: List<TaskEntity>,
        groups: List<TaskGroupEntity>,
        currentOverride: Note?,
    ): TaskSaveFailure? {
        val managedPath = currentManagedNotePath()
        val current = currentOverride
        if (current != null && !current.content.contains(MANAGED_MARKER)) {
            KardLeafLog.e(LOG_TAG, "refusing to overwrite unmanaged note path=$managedPath")
            return TaskSaveFailure(TaskSaveFailureReason.FileWrite, "任务清单不是 KardLeaf 管理文件，未覆盖原文件")
        }
        val previousItems =
            current?.let {
                parseMarkdownTasks(listOf(MarkdownTaskSource(managedPath, MANAGED_NOTE_TITLE, it.content)))
            }.orEmpty()
        val groupNamesByLine = current?.content?.let(::taskGroupNames).orEmpty()
        val preservedExternalTasks = preservedExternalTaskBlocks(previousItems, groupNamesByLine)
        val newline = if (current?.content?.contains("\r\n") == true) "\r\n" else "\n"
        val content =
            renderTaskMarkdown(
                tasks = tasks,
                groups = groups,
                previousItems = previousItems,
                preservedExternalTasks = preservedExternalTasks,
                newline = newline,
            )
        val latest = findManagedNote(managedPath)
        if ((current == null && latest != null) ||
            (current != null && (latest == null || latest.content != current.content))
        ) {
            KardLeafLog.w(LOG_TAG, "task Markdown changed before save path=$managedPath")
            return TaskSaveFailure(TaskSaveFailureReason.EditConflict, "任务清单已被外部修改，请重新打开后再保存")
        }
        val now = Date()
        val note =
            current?.copy(content = content, lastModified = now) ?: Note(
                file = File(managedPath),
                title = MANAGED_NOTE_TITLE,
                content = content,
                lastModified = now,
                createdAt = now,
                color = 0xFFFFFFFF,
            )
        val savedPath =
            try {
                noteRepository.saveNoteFromQuickEditor(note, current?.file, saveHistory = false)
            } catch (error: SecurityException) {
                KardLeafLog.e(LOG_TAG, "task markdown permission denied path=$managedPath", error)
                return TaskSaveFailure(TaskSaveFailureReason.PermissionDenied, "没有权限写入任务清单，请检查笔记库权限")
            } catch (error: IOException) {
                KardLeafLog.e(LOG_TAG, "task markdown IO failed path=$managedPath", error)
                return TaskSaveFailure(TaskSaveFailureReason.IoError, "任务清单读写失败，请检查存储空间后重试")
            }
        if (savedPath != managedPath) {
            KardLeafLog.e(LOG_TAG, "task markdown save failed path=$managedPath")
            return TaskSaveFailure(TaskSaveFailureReason.FileWrite, "任务清单写入失败，请检查笔记库权限后重试")
        }
        return null
    }

    private suspend fun migrateLegacyManagedNoteIfNeeded(): Boolean {
        val currentPath = currentManagedNotePath()
        if (findManagedNote(currentPath) != null) return true
        if (currentPath != MANAGED_NOTE_PATH) return true
        val legacyPaths =
            listOf(
                prefsManager.legacyTaskFolderPath()?.let { "$it/$MANAGED_NOTE_TITLE.md" },
                "${PrefsManager.LEGACY_TASK_FOLDER_NAME}/$MANAGED_NOTE_TITLE.md",
            ).filterNotNull().distinct().filterNot { it == MANAGED_NOTE_PATH }
        val legacy =
            legacyPaths.firstNotNullOfOrNull { path -> findManagedNote(path) }
                ?: return true
        if (!legacy.content.contains(MANAGED_MARKER)) {
            KardLeafLog.e(LOG_TAG, "legacy task file is not managed path=${legacy.file.path}")
            return false
        }
        val savedPath =
            noteRepository.saveNoteFromQuickEditor(
                legacy.copy(file = File(MANAGED_NOTE_PATH), lastModified = Date()),
                legacy.file,
                saveHistory = false,
            )
        if (savedPath != MANAGED_NOTE_PATH) {
            KardLeafLog.e(LOG_TAG, "legacy task file move failed savedPath=$savedPath")
            return false
        }
        prefsManager.clearLegacyTaskFolderPath()
        return true
    }

    private fun currentManagedNotePath(): String =
        prefsManager.getTaskFolderPath().trim('/').let { folder ->
            if (folder.isBlank()) "$MANAGED_NOTE_TITLE.md" else "$folder/$MANAGED_NOTE_TITLE.md"
        }

    suspend fun moveManagedNoteToFolder(targetFolder: String): Boolean =
        writeMutex.withLock {
            val normalizedFolder = targetFolder.trim().replace('\\', '/').trim('/')
            if (normalizedFolder.isNotBlank() && normalizedFolder.split('/').any { it.isBlank() || it == "." || it == ".." }) return@withLock false
            if (normalizedFolder.any { it == ':' || it == '*' || it == '?' || it == '"' || it == '<' || it == '>' || it == '|' }) return@withLock false
            val oldPath = currentManagedNotePath()
            val oldFolder = oldPath.substringBeforeLast('/', missingDelimiterValue = "")
            if (normalizedFolder == oldFolder) return@withLock true
            if (!noteRepository.folderPathExists(normalizedFolder)) return@withLock false
            val current = findManagedNote(oldPath) ?: return@withLock false
            val newPath = if (normalizedFolder.isBlank()) "$MANAGED_NOTE_TITLE.md" else "$normalizedFolder/$MANAGED_NOTE_TITLE.md"
            if (findManagedNote(newPath) != null) {
                KardLeafLog.w(LOG_TAG, "task Markdown move rejected target already exists path=$newPath")
                return@withLock false
            }
            val moved = noteRepository.moveNotesWithResult(
                notes = listOf(current),
                targetFolder = normalizedFolder,
                allowNameConflict = false,
                createTargetFolder = false,
                rewriteRelativeImages = false,
            ).singleOrNull()
            if (moved == null || moved.oldPath != oldPath || moved.newPath != newPath) {
                // Recover the cache if the provider moved the file but failed while reporting the move.
                val recovered = findManagedNote(newPath)
                val oldStillPresent = findManagedNote(oldPath) != null
                if (recovered?.content == current.content && recovered.content.contains(MANAGED_MARKER) && !oldStillPresent) {
                    prefsManager.saveTaskFolderPath(normalizedFolder)
                    KardLeafLog.w(
                        LOG_TAG,
                        "task Markdown move result missing but physical move recovered oldPath=$oldPath newPath=$newPath",
                    )
                    return@withLock true
                }
                KardLeafLog.w(
                    LOG_TAG,
                    "task Markdown move rejected oldPath=$oldPath newPath=$newPath " +
                        "reported=${moved?.oldPath}->${moved?.newPath}",
                )
                return@withLock false
            }
            prefsManager.saveTaskFolderPath(normalizedFolder)
            KardLeafLog.d(LOG_TAG, "task Markdown moved oldPath=$oldPath newPath=$newPath")
            true
        }

    private suspend fun findManagedNote(path: String): Note? {
        return noteRepository.refreshSingleNoteByPath(path, bypassCache = true)
    }

    companion object {
        const val TASK_FOLDER_NAME = ".KardLeaf"
        const val MANAGED_NOTE_TITLE = "任务清单"
        const val MANAGED_NOTE_PATH = "$TASK_FOLDER_NAME/$MANAGED_NOTE_TITLE.md"
        const val TASK_ID_PREFIX = "kardleaf-"
        private const val MANAGED_MARKER = "<!-- kardleaf-task-store:v1 -->"
        private const val LOG_TAG = "KardLeafTaskMarkdown"
        private val operationSequence = AtomicLong(0L)

        private fun nextOperationId(): Long = operationSequence.incrementAndGet()

        fun managedNotePath(prefsManager: PrefsManager): String =
            prefsManager.getTaskFolderPath().trim('/').let { folder ->
                if (folder.isBlank()) "$MANAGED_NOTE_TITLE.md" else "$folder/$MANAGED_NOTE_TITLE.md"
            }

        internal fun renameGroupPaths(
            groups: List<TaskGroupEntity>,
            oldPath: String,
            newPath: String,
        ): List<TaskGroupEntity> {
            if (oldPath.isBlank() || oldPath == newPath) return groups
            val descendantPrefix = "$oldPath/"
            return groups.map { group ->
                when {
                    group.name == oldPath -> group.copy(name = newPath)
                    group.name.startsWith(descendantPrefix) ->
                        group.copy(name = newPath + group.name.removePrefix(oldPath))
                    else -> group
                }
            }
        }

        /* 分组路径在 Markdown 中以 `## 路径` 标题存在，同名（精确匹配）会让 synchronize 的名字→ID 匹配产生歧义。 */
        internal fun firstDuplicatedGroupName(groups: List<TaskGroupEntity>): String? =
            groups.groupingBy { it.name }.eachCount().entries.firstOrNull { it.value > 1 }?.key

        // ponytail: One process-wide lock is enough while there is only one managed task file.
        private val writeMutex = Mutex()
        private val repositoryMutex = Mutex()

        @Volatile
        private var cachedRepositoryRootUri: String? = null

        @Volatile
        private var cachedRepository: RoomNoteRepository? = null

        suspend fun create(context: Context): TaskMarkdownStore? {
            val appContext = context.applicationContext
            val prefs = PrefsManager(appContext)
            val rootUri = prefs.getRootUri() ?: return null
            val repository =
                repositoryMutex.withLock {
                    if (cachedRepositoryRootUri == rootUri) {
                        cachedRepository?.let { return@withLock it }
                    }
                    val candidate = RoomNoteRepository(appContext, MetadataManager(appContext), prefs)
                    if (!candidate.setRootFolderForQuickSave(rootUri)) return@withLock null
                    cachedRepositoryRootUri = rootUri
                    cachedRepository = candidate
                    candidate
                } ?: return null
            return TaskMarkdownStore(appContext, repository)
        }

        internal fun renderTaskMarkdown(
            tasks: List<TaskEntity>,
            groups: List<TaskGroupEntity>,
            previousItems: List<MarkdownTaskItem> = emptyList(),
            preservedExternalTasks: Map<String?, List<String>> = emptyMap(),
            newline: String = "\n",
        ): String {
            val previousById =
                previousItems.mapNotNull { item -> item.managedTaskId()?.let { id -> id to item } }.toMap()
            val lines = mutableListOf(MANAGED_MARKER, "# 任务")
            val validGroupIds = groups.mapTo(hashSetOf()) { it.id }
            val orderedGroups = listOf<TaskGroupEntity?>(null) + groups.sortedBy { it.sortOrder }
            orderedGroups.forEach { group ->
                val groupTasks =
                    tasks.filter { task ->
                        !task.isTrashed &&
                            if (group == null) {
                                task.groupId == null || task.groupId !in validGroupIds
                            } else {
                                task.groupId == group.id
                            }
                    }
                val externalTasks = preservedExternalTasks[group?.name].orEmpty()
                if (group != null && groupTasks.isEmpty() && externalTasks.isEmpty()) return@forEach
                lines += ""
                lines += "## ${group?.name ?: "未分组"}"
                val orderedTasks = groupTasks.sortedWith(compareBy<TaskEntity> { it.manualOrder }.thenBy { it.id })
                val currentIds = orderedTasks.mapTo(hashSetOf()) { it.id }
                val parentIdsByTaskId = orderedTasks.mapNotNull { task ->
                    task.parentTaskId?.takeIf { it in currentIds }?.let { task.id to it }
                }.toMap()
                val flattenedTasks = TaskHierarchy.flatten(orderedTasks, parentIdsByTaskId, currentIds)
                val indentLevels = TaskHierarchy.depths(orderedTasks, parentIdsByTaskId)
                val renderedIds = hashSetOf<Long>()

                fun appendTask(
                    task: TaskEntity,
                    indentLevel: Int,
                ) {
                    if (!renderedIds.add(task.id)) return
                    val previous = previousById[task.id]
                    lines +=
                        renderTaskBlock(
                            task = task,
                            previous = previous,
                            excludedChildLineIndexes = nestedManagedTaskLines(previous, previousItems),
                            indentLevel = indentLevel,
                        )
                }
                flattenedTasks.forEach { task ->
                    if (task.id !in renderedIds) appendTask(task, indentLevels[task.id] ?: 0)
                }
                externalTasks.forEach { block -> lines += block.split('\n') }
            }
            return lines.dropLastWhile(String::isEmpty).joinToString(newline) + newline
        }

        internal fun renderTaskBlock(
            task: TaskEntity,
            previous: MarkdownTaskItem? = null,
            excludedChildLineIndexes: Set<Int> = emptySet(),
            indentLevel: Int = 0,
        ): List<String> =
            buildList {
                val indent = "  ".repeat(indentLevel)
                add(renderTaskLine(task, previous, indentLevel))
                task.notes.lineSequence()
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .forEach { add("$indent  - $it") }
                previous?.childLines?.forEachIndexed { index, line ->
                    if (index !in previous.noteLineIndexes && index !in excludedChildLineIndexes) add(line)
                }
            }

        private fun nestedManagedTaskLines(
            parent: MarkdownTaskItem?,
            items: List<MarkdownTaskItem>,
        ): Set<Int> {
            if (parent == null) return emptySet()
            val parentLastLine = parent.lineNumber + parent.childLines.size
            return buildSet {
                items.filter { child ->
                    child.lineNumber > parent.lineNumber &&
                        child.lineNumber <= parentLastLine &&
                        child.managedTaskId() != null
                }.forEach { child ->
                    val start = child.lineNumber - parent.lineNumber - 1
                    addAll(start..start + child.childLines.size)
                }
            }
        }

        internal fun preservedExternalTaskBlocks(
            items: List<MarkdownTaskItem>,
            groupNamesByLine: Map<Int, String?>,
        ): Map<String?, List<String>> =
            items
                .filter { item ->
                    item.managedTaskId() == null &&
                        items.none { parent ->
                            parent.lineNumber < item.lineNumber &&
                                item.lineNumber <= parent.lineNumber + parent.childLines.size
                        }
                }
                .groupBy { item -> groupNamesByLine[item.lineNumber] }
                .mapValues { (_, rootItems) ->
                    rootItems.map { item -> (listOf(item.rawLine) + item.childLines).joinToString("\n") }
                }

        internal fun renderTaskLine(
            task: TaskEntity,
            previous: MarkdownTaskItem? = null,
            indentLevel: Int = 0,
        ): String {
            val status = if (task.done) "x" else previous?.statusMarker?.takeUnless { it.equals("x", true) } ?: " "
            val metadata = mutableListOf<String>()
            val previousRaw = previous?.rawLine.orEmpty()
            val previousPriority = previous?.priorityMarker
            if (previousPriority != null && previousPriority.toTaskPriority(-1) == task.priority) {
                metadata += previousPriority
            } else {
                task.priority.toPriorityMarker()?.let(metadata::add)
            }
            task.repeatRule.toRecurrence()?.let { metadata += "🔁 $it" }
                ?: previous?.recurrenceRule?.let { metadata += "🔁 $it" }
            Regex("""🏁\s+(?:keep|delete)""", RegexOption.IGNORE_CASE).find(previousRaw)?.value?.let(metadata::add)
            metadata += "➕ ${previous?.createdDate ?: formatTaskDate(task.createdAt)}"
            previous?.startDate?.let { metadata += "🛫 $it" }
            task.reminderAt?.let { metadata += "⏳ ${formatTaskDate(it)}" }
            task.dueAt?.let { metadata += "📅 ${formatTaskDate(it)}" }
            if (task.done) metadata += "✅ ${previous?.doneDate ?: formatTaskDate(task.updatedAt)}"
            previous?.cancelledDate?.let { metadata += "❌ $it" }
            metadata += "🆔 $TASK_ID_PREFIX${task.id}"
            Regex("""⛔\s+[A-Za-z0-9_-]+(?:,[A-Za-z0-9_-]+)*""").find(previousRaw)?.value?.let(metadata::add)
            val text = task.taskText.replace(Regex("""\s+"""), " ").trim().ifBlank { "未命名任务" }
            return "${"  ".repeat(indentLevel)}- [$status] $text ${metadata.joinToString(" ")}".trimEnd()
        }

        internal fun addManagedTaskIds(
            content: String,
            tasks: List<MarkdownTaskItem>,
            firstId: Long,
        ): String {
            val newline = if (content.contains("\r\n")) "\r\n" else "\n"
            val lines = content.split(newline).toMutableList()
            var nextId = firstId
            tasks.filter { it.taskId == null }.forEach { task ->
                val index = task.lineNumber - 1
                val line = lines.getOrNull(index) ?: return@forEach
                if (line != task.rawLine) return@forEach
                val trailing = Regex("""\s+(?=⛔)""").find(line)
                val insertAt = trailing?.range?.first ?: line.length
                lines[index] = line.substring(0, insertAt).trimEnd() +
                    " 🆔 $TASK_ID_PREFIX${nextId++}" + line.substring(insertAt)
            }
            return lines.joinToString(newline)
        }

        private fun managedGroupNames(content: String): List<String> =
            content.lineSequence()
                .filter { it.startsWith("## ") }
                .map { it.removePrefix("## ").trim() }
                .filter { it.isNotBlank() && it != "未分组" }
                .distinct()
                .toList()

        private fun taskGroupNames(content: String): Map<Int, String?> {
            var currentGroup: String? = null
            return buildMap {
                content.lineSequence().forEachIndexed { index, line ->
                    if (line.startsWith("## ")) {
                        currentGroup = line.removePrefix("## ").trim().takeUnless { it == "未分组" }
                    }
                    put(index + 1, currentGroup)
                }
            }
        }

        private fun managedTaskGroupIds(
            content: String,
            groups: List<TaskGroupEntity>,
        ): Map<Int, Long?> {
            val groupByName = groups.associateBy { it.name }
            var currentGroup: Long? = null
            val result = mutableMapOf<Int, Long?>()
            content.lineSequence().forEachIndexed { index, line ->
                if (line.startsWith("## ")) {
                    currentGroup = line.removePrefix("## ").trim().takeUnless { it == "未分组" }?.let { groupByName[it]?.id }
                } else if (line.contains("🆔 $TASK_ID_PREFIX")) {
                    result[index + 1] = currentGroup
                }
            }
            return result
        }

        private fun taskMatchesMarkdown(
            task: TaskEntity,
            item: MarkdownTaskItem,
            groupId: Long?,
        ): Boolean =
            task.taskText == item.taskText &&
                task.done == item.done &&
                task.groupId == groupId &&
                task.priority == item.priorityMarker.toTaskPriority(task.priority) &&
                task.dueAt.sameTaskDate(item.dueDate) &&
                task.reminderAt.sameTaskDate(item.scheduledDate) &&
                task.repeatRule == item.recurrenceRule.toTaskRepeat(task.repeatRule) &&
                task.notes == item.notes

        private fun formatTaskDate(timestamp: Long): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))
    }
}

private fun buildParentIds(items: List<MarkdownTaskItem>): Map<Long, Long> {
    val itemsByLine = items.associateBy { it.lineNumber }
    return items.mapNotNull { item ->
        val taskId = item.managedTaskId() ?: return@mapNotNull null
        val parentId = item.parentLineNumber?.let(itemsByLine::get)?.managedTaskId() ?: return@mapNotNull null
        taskId to parentId
    }.toMap()
}

private fun normalizeManagedTaskId(value: String): String? =
    value.trim()
        .removePrefix(TaskMarkdownStore.TASK_ID_PREFIX)
        .toLongOrNull()
        ?.takeIf { it > 0L }
        ?.let { "${TaskMarkdownStore.TASK_ID_PREFIX}$it" }

private fun MarkdownTaskItem.managedTaskId(): Long? =
    taskId
        ?.takeIf { it.startsWith(TaskMarkdownStore.TASK_ID_PREFIX) }
        ?.removePrefix(TaskMarkdownStore.TASK_ID_PREFIX)
        ?.toLongOrNull()

private fun Int.toPriorityMarker(): String? =
    when (this) {
        3 -> "⏫"
        2 -> "🔼"
        1 -> "🔽"
        else -> null
    }

private fun String?.toTaskPriority(fallback: Int): Int =
    when (this) {
        "🔺", "⏫" -> 3
        "🔼" -> 2
        "🔽", "⏬" -> 1
        null -> 0
        else -> fallback
    }

private fun String.toRecurrence(): String? =
    when (this) {
        "DAILY" -> "every day"
        "WEEKLY" -> "every week"
        "MONTHLY" -> "every month"
        else -> null
    }

private fun String?.toTaskRepeat(fallback: String): String =
    when (this?.lowercase(Locale.US)) {
        "every day", "daily" -> "DAILY"
        "every week", "weekly" -> "WEEKLY"
        "every month", "monthly" -> "MONTHLY"
        null -> "NONE"
        else -> fallback
    }

private fun String?.toTaskTimestamp(
    existing: Long?,
    defaultHour: Int,
    defaultMinute: Int,
): Long? {
    if (this == null) return null
    val parsed =
        runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(this) }.getOrNull()
            ?: return existing
    val source = Calendar.getInstance().apply { time = parsed }
    val time =
        Calendar.getInstance().apply {
            timeInMillis = existing ?: System.currentTimeMillis()
            set(Calendar.YEAR, source.get(Calendar.YEAR))
            set(Calendar.MONTH, source.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, source.get(Calendar.DAY_OF_MONTH))
            if (existing == null) {
                set(Calendar.HOUR_OF_DAY, defaultHour)
                set(Calendar.MINUTE, defaultMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
    return time.timeInMillis
}

private fun Long?.sameTaskDate(date: String?): Boolean =
    when {
        this == null || date == null -> this == null && date == null
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(this)) == date
    }
