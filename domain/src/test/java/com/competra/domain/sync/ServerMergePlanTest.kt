package com.competra.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerMergePlanTest {

    private data class Local(
        val id: String,
        val remoteId: String?,
        val isSynced: Boolean,
        val isDeleted: Boolean = false
    )

    private data class Server(val id: String, val title: String = "")

    private fun plan(local: List<Local>, server: List<Server>) = planServerMerge(
        local = local,
        server = server,
        localKey = { it.id },
        serverKey = { it.id },
        isLocalSynced = { it.isSynced },
        isLocalOnServer = { it.remoteId != null }
    )

    @Test
    fun `synced local record is updated from server`() {
        val local = Local(id = "a", remoteId = "a", isSynced = true)
        val server = Server(id = "a", title = "new")

        val result = plan(listOf(local), listOf(server))

        assertEquals(listOf(local to server), result.toUpdate)
        assertTrue(result.toInsert.isEmpty())
        assertTrue(result.toDelete.isEmpty())
    }

    @Test
    fun `local record with unsent edits is not overwritten`() {
        val local = Local(id = "a", remoteId = "a", isSynced = false)

        val result = plan(listOf(local), listOf(Server(id = "a")))

        assertTrue(result.toUpdate.isEmpty())
        assertTrue(result.toInsert.isEmpty())
        assertTrue(result.toDelete.isEmpty())
    }

    @Test
    fun `record marked for deletion locally is not resurrected`() {
        val local = Local(id = "a", remoteId = "a", isSynced = false, isDeleted = true)

        val result = plan(listOf(local), listOf(Server(id = "a")))

        assertTrue(result.toUpdate.isEmpty())
        assertTrue(result.toInsert.isEmpty())
        assertTrue(result.toDelete.isEmpty())
    }

    @Test
    fun `new server record is inserted`() {
        val server = Server(id = "b")

        val result = plan(emptyList(), listOf(server))

        assertEquals(listOf(server), result.toInsert)
    }

    @Test
    fun `synced record missing on server is deleted`() {
        val local = Local(id = "a", remoteId = "a", isSynced = true)

        val result = plan(listOf(local), emptyList())

        assertEquals(listOf(local), result.toDelete)
    }

    @Test
    fun `record with unsent edits missing on server is kept`() {
        val local = Local(id = "a", remoteId = "a", isSynced = false)

        val result = plan(listOf(local), emptyList())

        assertTrue(result.toDelete.isEmpty())
    }

    @Test
    fun `record never pushed to server is kept`() {
        val local = Local(id = "a", remoteId = null, isSynced = true)

        val result = plan(listOf(local), emptyList())

        assertTrue(result.toDelete.isEmpty())
    }

    @Test
    fun `pushed record without remoteId yet is matched by key and not reinserted`() {
        // push прошёл, но ответ потерялся: remoteId ещё не проставлен, правки не отправлены
        val local = Local(id = "a", remoteId = null, isSynced = false)

        val result = plan(listOf(local), listOf(Server(id = "a")))

        assertTrue(result.toInsert.isEmpty())
        assertTrue(result.toUpdate.isEmpty())
    }
}
