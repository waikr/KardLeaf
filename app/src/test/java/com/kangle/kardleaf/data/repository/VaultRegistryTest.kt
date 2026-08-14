package com.kangle.kardleaf.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultRegistryTest {
    @Test
    fun keepsVaultDatabasesStableAndRejectsInvalidRegistryEntries() {
        val firstUri = "content://notes/tree/primary%3AVaultA"
        val secondUri = "content://notes/tree/primary%3AVaultB"
        val secondDatabase = databaseNameForVault(secondUri)
        val vaults =
            listOf(
                VaultInfo(firstUri, "Vault A", DEFAULT_VAULT_DATABASE_NAME),
                VaultInfo(secondUri, "Vault B", secondDatabase),
            )

        assertEquals(vaults, VaultRegistryCodec.decode(VaultRegistryCodec.encode(vaults)))
        assertEquals(secondDatabase, databaseNameForVault(secondUri))
        assertNotEquals(databaseNameForVault(firstUri), secondDatabase)
        assertTrue(VaultRegistryCodec.decode("not json").isEmpty())
        assertTrue(
            VaultRegistryCodec.decode(
                """[{"uri":"content://bad","displayName":"Bad","databaseName":"../outside"}]""",
            ).isEmpty(),
        )
    }
}
