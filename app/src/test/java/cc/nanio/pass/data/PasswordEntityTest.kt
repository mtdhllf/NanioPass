package cc.nanio.pass.data

import cc.nanio.pass.data.model.PasswordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PasswordEntity] 的纯 JVM 单测（无 Android framework 依赖）。
 *
 * 仓库/数据库/Keystore 的实现需要 Android runtime，这里只覆盖实体的派生属性与契约默认值，
 * 保证 UI 依赖的 `initial` / `hasTotp` / `isBoundApp` / `isDeleted` 语义不被破坏。
 */
class PasswordEntityTest {

    private fun entity(
        label: String = "GitHub",
        account: String = "octocat",
        password: String = "s3cret",
        packageName: String? = null,
        totpSecret: String? = null,
        deletedAt: Long? = null,
    ) = PasswordEntity(
        label = label,
        account = account,
        password = password,
        packageName = packageName,
        totpSecret = totpSecret,
        deletedAt = deletedAt,
    )

    @Test
    fun initialUppercasesFirstCharacter() {
        assertEquals("G", entity(label = "github").initial)
    }

    @Test
    fun initialTrimsLeadingWhitespace() {
        assertEquals("G", entity(label = "   github").initial)
    }

    @Test
    fun initialKeepsCjkCharacter() {
        assertEquals("微", entity(label = "微信").initial)
    }

    @Test
    fun initialFallsBackToHashForBlankLabel() {
        assertEquals("#", entity(label = "").initial)
        assertEquals("#", entity(label = "   ").initial)
    }

    @Test
    fun defaultsMatchContract() {
        val e = entity()
        assertEquals(0L, e.id)
        assertEquals("SHA1", e.totpAlgorithm)
        assertEquals(6, e.totpDigits)
        assertEquals(-1L, e.lastUsedAt)
        assertNull(e.deletedAt)
        assertNull(e.domain)
        assertNull(e.note)
        assertNull(e.packageName)
        assertNull(e.appLabel)
        assertNull(e.totpSecret)
        assertNull(e.totpIssuer)
        assertNull(e.totpAccount)
        assertTrue(e.createdAt > 0L)
        assertTrue(e.updatedAt > 0L)
        assertFalse(e.hasTotp)
        assertFalse(e.isBoundApp)
        assertFalse(e.isDeleted)
    }

    @Test
    fun hasTotpRequiresNonEmptySecret() {
        assertTrue(entity(totpSecret = "JBSWY3DPEHPK3PXP").hasTotp)
        assertFalse(entity(totpSecret = null).hasTotp)
        assertFalse(entity(totpSecret = "").hasTotp)
    }

    @Test
    fun isBoundAppRequiresNonEmptyPackageName() {
        assertTrue(entity(packageName = "com.tencent.mm").isBoundApp)
        assertFalse(entity(packageName = null).isBoundApp)
        assertFalse(entity(packageName = "").isBoundApp)
    }

    @Test
    fun isDeletedReflectsDeletedAt() {
        assertTrue(entity(deletedAt = 1L).isDeleted)
        assertFalse(entity(deletedAt = null).isDeleted)
    }
}
