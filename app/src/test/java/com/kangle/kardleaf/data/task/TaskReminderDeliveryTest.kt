package com.kangle.kardleaf.data.task

import com.kangle.kardleaf.data.database.TaskEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskReminderDeliveryTest {
    @Test
    fun visibleAppLaunchesPopupDirectlyWithoutOverlayPermission() {
        assertTrue(shouldLaunchReminderPopupDirectly("FOREGROUND", canDrawOverlays = false))
        assertTrue(shouldLaunchReminderPopupDirectly("VISIBLE", canDrawOverlays = false))
        assertFalse(shouldLaunchReminderPopupDirectly("BACKGROUND", canDrawOverlays = false))
        assertFalse(shouldLaunchReminderPopupDirectly("UNKNOWN", canDrawOverlays = false))
    }

    @Test
    fun overlayPermissionAllowsPopupFromBackground() {
        assertTrue(shouldLaunchReminderPopupDirectly("BACKGROUND", canDrawOverlays = true))
        assertTrue(shouldLaunchReminderPopupDirectly("UNKNOWN", canDrawOverlays = true))
    }

    @Test
    fun taskDefaultsToPopupWithRingAndVibration() {
        val task = TaskEntity(taskText = "默认任务", createdAt = 0L, updatedAt = 0L)
        assertTrue(task.reminderMode == TaskEntity.REMINDER_MODE_POPUP)
        assertTrue(task.reminderRing)
        assertTrue(task.reminderVibrate)
    }

    @Test
    fun notificationModeIsPreservedOnCopy() {
        val task = TaskEntity(
            taskText = "仅通知任务",
            createdAt = 0L,
            updatedAt = 0L,
            reminderMode = TaskEntity.REMINDER_MODE_NOTIFICATION,
            reminderRing = false,
            reminderVibrate = false,
        )
        val copied = task.copy(done = true)
        assertTrue(copied.reminderMode == TaskEntity.REMINDER_MODE_NOTIFICATION)
        assertFalse(copied.reminderRing)
        assertFalse(copied.reminderVibrate)
    }
}
