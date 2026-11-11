package cc.nanio.pass.security

import cc.nanio.pass.data.model.PasswordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityChecksTest {

    private fun entry(
        id: Long,
        label: String = "site$id",
        account: String = "user$id",
        password: String,
    ) = PasswordEntity(id = id, label = label, account = account, password = password)

    @Test
    fun `单一字符类型提示安全强度低`() {
        assertEquals(SecurityChecks.WeakReason.WEAK_PASSWORD, SecurityChecks.weakReason("abcdefgh"))
        assertEquals(SecurityChecks.WeakReason.WEAK_PASSWORD, SecurityChecks.weakReason("12345678"))
        assertEquals(SecurityChecks.WeakReason.WEAK_PASSWORD, SecurityChecks.weakReason("ABCDEFGH"))
        assertEquals(SecurityChecks.WeakReason.EASILY_GUESSED, SecurityChecks.weakReason("!!!!!!!!"))
    }

    @Test
    fun `短且单一类型提示安全强度低`() {
        assertEquals(SecurityChecks.WeakReason.WEAK_PASSWORD, SecurityChecks.weakReason("abc"))
        assertEquals(SecurityChecks.WeakReason.WEAK_PASSWORD, SecurityChecks.weakReason("123"))
    }

    @Test
    fun `两三种以内字符组合提示容易被猜到`() {
        assertEquals(SecurityChecks.WeakReason.EASILY_GUESSED, SecurityChecks.weakReason("66668888"))
        assertEquals(SecurityChecks.WeakReason.EASILY_GUESSED, SecurityChecks.weakReason("77777777"))
        assertEquals(SecurityChecks.WeakReason.EASILY_GUESSED, SecurityChecks.weakReason("abababab"))
    }

    @Test
    fun `长短混合字符组合不提示`() {
        assertNull(SecurityChecks.weakReason("Abc12345"))
        assertNull(SecurityChecks.weakReason("Xk9#mQ2vL7pR4tWz"))
        assertFalse(SecurityChecks.isWeak("Xk9#mQ2vL7pR4tWz"))
    }

    @Test
    fun `字符类型与字符个数统计`() {
        assertEquals(1, SecurityChecks.characterClassCount("12345678"))
        assertEquals(2, SecurityChecks.characterClassCount("abc12345"))
        assertEquals(4, SecurityChecks.characterClassCount("Abc12345!"))
        assertEquals(2, SecurityChecks.distinctCharacterCount("66668888"))
        assertEquals(1, SecurityChecks.distinctCharacterCount("77777777"))
    }

    @Test
    fun `空密码不算弱密码风险条目`() {
        val entries = listOf(entry(1, password = ""))
        assertTrue(SecurityChecks.weakEntries(entries).isEmpty())
        assertNull(SecurityChecks.weakReason(""))
    }
}
