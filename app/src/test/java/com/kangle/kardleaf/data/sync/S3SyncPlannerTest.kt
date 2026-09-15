package com.kangle.kardleaf.data.sync

import org.junit.Assert.*
import org.junit.Test

class S3SyncPlannerTest {
    private val local = S3FileState(3, 1000, hash = "old")
    private val remote = S3FileState(3, 1000, etag = "opaque-multipart-etag")
    private val baseline = mapOf("note.md" to S3Baseline(local.localVersion(), remote.remoteVersion()))
    private fun action(l: S3FileState?, r: S3FileState?, known: Boolean = true): S3SyncAction =
        S3SyncPlanner.plan(l?.let { mapOf("note.md" to it) }.orEmpty(), r?.let { mapOf("note.md" to it) }.orEmpty(),
            if (known) baseline else emptyMap()).single().action

    @Test fun threeWayMatrix() {
        assertEquals(S3SyncAction.UPLOAD, action(local, null, false))
        assertEquals(S3SyncAction.DOWNLOAD, action(null, remote, false))
        assertEquals(S3SyncAction.CONFLICT, action(local, remote, false))
        assertEquals(S3SyncAction.MATCH, action(local, remote))
        assertEquals(S3SyncAction.UPLOAD, action(local.copy(hash = "new"), remote))
        assertEquals(S3SyncAction.DOWNLOAD, action(local, remote.copy(etag = "new")))
        assertEquals(S3SyncAction.CONFLICT, action(local.copy(hash = "new"), remote.copy(etag = "new")))
        assertEquals(S3SyncAction.DELETE_REMOTE, action(null, remote))
        assertEquals(S3SyncAction.DELETE_LOCAL, action(local, null))
        assertEquals(S3SyncAction.CONFLICT, action(null, remote.copy(etag = "new")))
        assertEquals(S3SyncAction.CONFLICT, action(local.copy(hash = "new"), null))
        assertEquals(S3SyncAction.MATCH, action(null, null))
        assertEquals(S3SyncAction.MATCH, action(local.copy(hash = "same"), remote.copy(hash = "same", etag = "new")))
        assertEquals(S3SyncAction.MATCH, action(local.copy(modifiedMs = 9000), remote))
    }

    @Test fun missingSideConflictChoiceMeansDeletion() {
        val item = S3SyncItem("note.md", S3SyncAction.CONFLICT)
        assertEquals(S3SyncAction.DELETE_REMOTE, S3SyncPlanner.resolve(item, S3ConflictChoice.KEEP_LOCAL, false, true))
        assertEquals(S3SyncAction.DOWNLOAD, S3SyncPlanner.resolve(item, S3ConflictChoice.KEEP_REMOTE, false, true))
        assertNull(S3SyncPlanner.resolve(item, S3ConflictChoice.SKIP, true, true))
    }

    @Test fun privacyRandomFilenamesStillConflictAsOneGroup() {
        val prefix = ".KardLeaf/Vault/notes/"
        val plan = S3SyncPlanner.plan(mapOf("${prefix}local.klv" to local), mapOf("${prefix}remote.klv" to remote),
            mapOf("${prefix}old.klv" to S3Baseline(local.localVersion(), remote.remoteVersion())))
        assertEquals(2, plan.count { it.action == S3SyncAction.CONFLICT })
        assertTrue(plan.filter { it.action == S3SyncAction.CONFLICT }.all { it.reason.startsWith("隐私库") })
    }

    @Test fun scopeAndExactKeyMapping() {
        listOf("NOTE.md" to "note.md", "a" to "a/").forEach { (a, b) ->
            assertThrows(IllegalArgumentException::class.java) {
                S3SyncPlanner.plan(mapOf(a to local), mapOf(b to remote), emptyMap())
            }
        }
        assertEquals("笔记/今天 +%.md", S3Paths.key("", "笔记/今天 +%.md"))
        assertEquals("notes/笔记.md", S3Paths.key("notes", "笔记.md"))
        listOf(".KardLeaf/history/note.json", ".KardLeaf/remarks/note.json", ".KardLeaf/Vault/vault.meta", ".KardLeaf/Vault/notes/x.klv").forEach {
            assertTrue(it, S3Paths.included(it, false))
        }
        listOf(".KardLeaf/x.bak", ".KardLeaf/a/x.BAK", ".KardLeaf/Vault/vault.meta.bak", ".KardLeaf/Vault/notes/.x.tmp", ".KardLeaf/history/.x.kardleaf-record-bak",
            ".KardLeaf/history/.x.kardleaf-sync-tmp", ".obsidian/plugins/remotely-save/data.json", ".git/config", "notes/.secret", "_draft/note.md").forEach {
            assertFalse(it, S3Paths.included(it, false))
        }
        assertTrue(S3Paths.included("attachments/user.bak", false))
        assertTrue(S3Paths.included("_draft/note.md", true))
        listOf("../x", "/x", "x/../y", "x//y", "x\\y", "x\u0000y").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) { S3Paths.validate(path) }
        }
    }
}
