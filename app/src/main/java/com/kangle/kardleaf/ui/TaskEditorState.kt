package com.kangle.kardleaf.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.kangle.kardleaf.data.database.TaskEntity
import com.kangle.kardleaf.data.task.TaskRepeat

internal class TaskEditorState(
    textState: MutableState<String>,
    doneState: MutableState<Boolean>,
    groupIdState: MutableState<Long?>,
    parentTaskIdState: MutableState<Long?>,
    priorityState: MutableState<Int>,
    dueAtState: MutableState<Long?>,
    reminderAtState: MutableState<Long?>,
    reminderPopupState: MutableState<Boolean>,
    reminderRingState: MutableState<Boolean>,
    reminderVibrateState: MutableState<Boolean>,
    repeatRuleState: MutableState<String>,
    notesState: MutableState<String>,
    errorState: MutableState<String?>,
    showGroupMenuState: MutableState<Boolean>,
    parentTaskSelectionChangedState: MutableState<Boolean>,
    showInlineChildInputState: MutableState<Boolean>,
    childTaskTextsState: MutableState<List<String>>,
    focusedChildIndexState: MutableState<Int?>,
    showParentMenuState: MutableState<Boolean>,
    showPriorityMenuState: MutableState<Boolean>,
    showDatePickerState: MutableState<Boolean>,
) {
    var text by textState
    var done by doneState
    var groupId by groupIdState
    var parentTaskId by parentTaskIdState
    var priority by priorityState
    var dueAt by dueAtState
    var reminderAt by reminderAtState
    var reminderPopup by reminderPopupState
    var reminderRing by reminderRingState
    var reminderVibrate by reminderVibrateState
    var repeatRule by repeatRuleState
    var notes by notesState
    var error by errorState
    var showGroupMenu by showGroupMenuState
    var parentTaskSelectionChanged by parentTaskSelectionChangedState
    var showInlineChildInput by showInlineChildInputState
    var childTaskTexts by childTaskTextsState
    var focusedChildIndex by focusedChildIndexState
    var showParentMenu by showParentMenuState
    var showPriorityMenu by showPriorityMenuState
    var showDatePicker by showDatePickerState
}

internal fun appendChildDraftLine(
    drafts: List<String>,
    index: Int,
): Pair<List<String>, Int?> {
    if (drafts.getOrNull(index)?.isBlank() != false) return drafts to null
    return if (index == drafts.lastIndex) {
        (drafts + "") to index + 1
    } else {
        drafts to index + 1
    }
}

internal fun removeEmptyChildDraftLine(
    drafts: List<String>,
    index: Int,
): Pair<List<String>, Int>? {
    if (index <= 0 || drafts.getOrNull(index)?.isNotBlank() != false) return null
    return drafts.toMutableList().apply { removeAt(index) } to index - 1
}

@Composable
internal fun rememberTaskEditorState(
    task: TaskEntity?,
    initialGroupId: Long?,
    initialParentTaskId: Long?,
    parentTaskIds: Map<Long, Long>,
): TaskEditorState {
    val text = rememberSaveable(task?.id) { mutableStateOf(task?.taskText.orEmpty()) }
    val done = rememberSaveable(task?.id) { mutableStateOf(task?.done ?: false) }
    val groupId = rememberSaveable(task?.id, initialGroupId) { mutableStateOf(task?.groupId ?: initialGroupId) }
    val parentTaskId = rememberSaveable(task?.id, initialParentTaskId) {
        mutableStateOf(initialParentTaskId ?: task?.parentTaskId ?: task?.id?.let(parentTaskIds::get))
    }
    val priority = rememberSaveable(task?.id) { mutableStateOf(task?.priority ?: 0) }
    val dueAt = rememberSaveable(task?.id) { mutableStateOf(task?.dueAt) }
    val reminderAt = rememberSaveable(task?.id) { mutableStateOf(task?.reminderAt) }
    val reminderPopup = rememberSaveable(task?.id) {
        mutableStateOf((task?.reminderMode ?: TaskEntity.REMINDER_MODE_POPUP) != TaskEntity.REMINDER_MODE_NOTIFICATION)
    }
    val reminderRing = rememberSaveable(task?.id) { mutableStateOf(task?.reminderRing ?: true) }
    val reminderVibrate = rememberSaveable(task?.id) { mutableStateOf(task?.reminderVibrate ?: true) }
    val repeatRule = rememberSaveable(task?.id) { mutableStateOf(task?.repeatRule ?: TaskRepeat.NONE.value) }
    val notes = rememberSaveable(task?.id) { mutableStateOf(task?.notes.orEmpty()) }
    val error = rememberSaveable(task?.id) { mutableStateOf<String?>(null) }
    val showGroupMenu = rememberSaveable(task?.id) { mutableStateOf(false) }
    val parentTaskSelectionChanged = rememberSaveable(task?.id, initialParentTaskId) { mutableStateOf(false) }
    val showInlineChildInput = rememberSaveable(task?.id) { mutableStateOf(false) }
    val childTaskTexts = rememberSaveable(task?.id) { mutableStateOf(emptyList<String>()) }
    val focusedChildIndex = rememberSaveable(task?.id) { mutableStateOf<Int?>(null) }
    val showParentMenu = rememberSaveable(task?.id) { mutableStateOf(false) }
    val showPriorityMenu = rememberSaveable(task?.id) { mutableStateOf(false) }
    val showDatePicker = rememberSaveable(task?.id) { mutableStateOf(false) }

    return remember(task?.id, initialGroupId, initialParentTaskId) {
        TaskEditorState(
            textState = text,
            doneState = done,
            groupIdState = groupId,
            parentTaskIdState = parentTaskId,
            priorityState = priority,
            dueAtState = dueAt,
            reminderAtState = reminderAt,
            reminderPopupState = reminderPopup,
            reminderRingState = reminderRing,
            reminderVibrateState = reminderVibrate,
            repeatRuleState = repeatRule,
            notesState = notes,
            errorState = error,
            showGroupMenuState = showGroupMenu,
            parentTaskSelectionChangedState = parentTaskSelectionChanged,
            showInlineChildInputState = showInlineChildInput,
            childTaskTextsState = childTaskTexts,
            focusedChildIndexState = focusedChildIndex,
            showParentMenuState = showParentMenu,
            showPriorityMenuState = showPriorityMenu,
            showDatePickerState = showDatePicker,
        )
    }
}
