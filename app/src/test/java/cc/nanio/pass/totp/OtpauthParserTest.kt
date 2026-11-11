package cc.nanio.pass.totp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import cc.nanio.pass.R
import org.junit.Test

/**
 * otpauth:// 解析行为（含对历史数据的兼容规则）。
 */
class OtpauthParserTest {

    private val validSecret = "JBSWY3DPEHPK3PXP"
    private val validSecretUri = "otpauth://totp/Google:user%40example.com?secret=JBSWY3DPEHPK3PXP"

    private fun success(raw: String?): TotpConfig {
        val result = OtpauthParser.parse(raw)
        assertTrue("应解析成功，实际结果：$result", result is TotpParseResult.Success)
        return (result as TotpParseResult.Success).config
    }

    private fun failure(raw: String?): TotpParseResult.Failure {
        val result = OtpauthParser.parse(raw)
        assertTrue("应解析失败，实际结果：$result", result is TotpParseResult.Failure)
        return result as TotpParseResult.Failure
    }

    // -------------------------------------------------------------- 正常解析

    @Test
    fun parsesStandardTotpUri() {
        val config = success(
            "otpauth://totp/Google:user%40example.com" +
                "?secret=JBSWY3DPEHPK3PXP&issuer=Google&algorithm=SHA1&digits=6&period=30",
        )
        assertEquals(validSecret, config.secret)
        assertEquals("Google", config.issuer)
        assertEquals("user@example.com", config.account)
        assertEquals("SHA1", config.algorithm)
        assertEquals(6, config.digits)
        assertEquals(30, config.period)
    }

    @Test
    fun issuerQueryParameterOverridesLabelIssuer() {
        val config = success("otpauth://totp/Old:account?secret=$validSecret&issuer=New")
        assertEquals("New", config.issuer)
        assertEquals("account", config.account)
    }

    @Test
    fun issuerFallsBackToLabelPrefixWhenQueryMissing() {
        val config = success("otpauth://totp/Google:account?secret=$validSecret")
        assertEquals("Google", config.issuer)
        assertEquals("account", config.account)
    }

    @Test
    fun blankIssuerQueryParameterFallsBackToLabelPrefix() {
        val config = success("otpauth://totp/Google:account?secret=$validSecret&issuer=")
        assertEquals("Google", config.issuer)
    }

    @Test
    fun accountWithoutColonHasNullIssuer() {
        val config = success("otpauth://totp/account%40example.com?secret=$validSecret")
        assertNull(config.issuer)
        assertEquals("account@example.com", config.account)
    }

    @Test
    fun labelIsSplitAtFirstColonOnly() {
        val config = success("otpauth://totp/Google:user:name?secret=$validSecret")
        assertEquals("Google", config.issuer)
        assertEquals("user:name", config.account)
    }

    @Test
    fun decodesUrlEncodedColonInLabel() {
        val config = success("otpauth://totp/Google%3Auser%40example.com?secret=$validSecret")
        assertEquals("Google", config.issuer)
        assertEquals("user@example.com", config.account)
    }

    @Test
    fun schemeIsCaseInsensitive() {
        val config = success("OTPAUTH://totp/Google:account?secret=$validSecret")
        assertEquals("Google", config.issuer)
    }

    @Test
    fun trimsSurroundingWhitespace() {
        val config = success("  otpauth://totp/Google:account?secret=$validSecret  ")
        assertEquals("Google", config.issuer)
        assertEquals("account", config.account)
    }

    @Test
    fun ignoresFragment() {
        val config = success("otpauth://totp/Google:account?secret=$validSecret#fragment")
        assertEquals("account", config.account)
    }

    @Test
    fun decodesPercentEncodedSecret() {
        // secret 里的 %3D 应解码成 '='，Base32 解码前会被剥掉尾部 padding。
        val config = success("otpauth://totp/Google:account?secret=GEZDGNBVGY3TQOJQ%3D")
        assertEquals("GEZDGNBVGY3TQOJQ=", config.secret)
        assertEquals(6, TotpEngine.generate(config, 59_000L).length)
    }

    @Test
    fun queryPlusIsDecodedAsSpaceButPathPlusIsLiteral() {
        val config = success("otpauth://totp/Issuer:a+b?secret=$validSecret&issuer=My+Service")
        assertEquals("My Service", config.issuer)
        assertEquals("a+b", config.account)
    }

    // ---------------------------------------------------------------- 裸密钥

    @Test
    fun parsesBareBase32Secret() {
        val config = success(validSecret)
        assertEquals(validSecret, config.secret)
        assertNull(config.issuer)
        assertNull(config.account)
        assertEquals("SHA1", config.algorithm)
        assertEquals(6, config.digits)
        assertEquals(30, config.period)
        // 裸密钥也要能真正生成验证码。
        assertEquals(6, TotpEngine.generate(config, 59_000L).length)
    }

    @Test
    fun bareSecretToleratesSpacesHyphensAndLowercase() {
        val raw = "jbsw y3dp-ehpk3pxp"
        val config = success(raw)
        assertEquals(raw, config.secret)
        assertNull(config.issuer)
        assertNull(config.account)
        assertEquals(6, TotpEngine.generate(config, 59_000L).length)
    }

    @Test
    fun bareSecretWithPaddingIsAccepted() {
        val config = success("MZXW6YTBOI======")
        assertEquals("MZXW6YTBOI======", config.secret)
    }

    // ------------------------------------------------------------ 非法输入

    @Test
    fun rejectsEmptyInput() {
        assertEquals(R.string.s_empty_secret_key, failure(null).reasonRes)
        assertEquals(R.string.s_empty_secret_key, failure("").reasonRes)
        assertEquals(R.string.s_empty_secret_key, failure("   \n\t ").reasonRes)
    }

    @Test
    fun rejectsHotpUri() {
        assertEquals(R.string.s_only_otpauth_totp_is_supported_unsupported_s, failure("otpauth://hotp/Google:account?secret=$validSecret&counter=1").reasonRes)
    }

    @Test
    fun rejectsMissingSecret() {
        assertEquals(R.string.s_missing_secret_parameter, failure("otpauth://totp/Google:account").reasonRes)
        assertEquals(R.string.s_missing_secret_parameter, failure("otpauth://totp/Google:account?issuer=Google").reasonRes)
        assertEquals(R.string.s_missing_secret_parameter, failure("otpauth://totp/Google:account?secret=").reasonRes)
    }

    @Test
    fun rejectsMissingPath() {
        assertEquals(R.string.s_qr_code_is_missing_account_information, failure("otpauth://totp").reasonRes)
        assertEquals(R.string.s_qr_code_is_missing_account_information, failure("otpauth://totp?secret=$validSecret").reasonRes)
    }

    @Test
    fun rejectsEmptyAccount() {
        assertEquals(R.string.s_qr_code_is_missing_account_information, failure("otpauth://totp/").reasonRes)
        assertEquals(R.string.s_qr_code_is_missing_account_information, failure("otpauth://totp/?secret=$validSecret").reasonRes)
        assertEquals(R.string.s_qr_code_is_missing_account_information, failure("otpauth://totp/Google:?secret=$validSecret").reasonRes)
    }

    @Test
    fun rejectsMissingAuthority() {
        assertEquals(R.string.s_otpauth_link_is_missing_an_authority, failure("otpauth:///Google:account?secret=$validSecret").reasonRes)
    }

    @Test
    fun rejectsUnsupportedSchemeLikeBareSecret() {
        // 非 otpauth scheme 走裸密钥分支，':'、'/' 不是合法 Base32 字符 → 失败。
        assertEquals(R.string.s_invalid_secret_key_s, failure("foo:bar").reasonRes)
        assertEquals(R.string.s_invalid_secret_key_s, failure("otpauthx://totp/Google:account?secret=$validSecret").reasonRes)
    }

    @Test
    fun rejectsInvalidSecretCharacters() {
        val failure = failure("otpauth://totp/Google:account?secret=ABC!DEF")
        assertEquals(R.string.s_invalid_secret_key_s, failure.reasonRes)
        assertTrue(failure.args.any { it.toString().contains("!") })
    }

    @Test
    fun rejectsUnsupportedAlgorithm() {
        val failure = failure("otpauth://totp/Google:account?secret=$validSecret&algorithm=MD5")
        assertEquals(R.string.s_unsupported_algorithm_s, failure.reasonRes)
        assertTrue(failure.args.any { it.toString().contains("MD5") })
    }

    // ------------------------------------------------- 兼容历史数据：digits/period

    @Test
    fun discardsDigitsParameter() {
        // digits 解析结果被丢弃，落库恒为 6。
        assertEquals(6, success("otpauth://totp/Google:account?secret=$validSecret&digits=8").digits)
        assertEquals(6, success("otpauth://totp/Google:account?secret=$validSecret&digits=6").digits)
    }

    @Test
    fun ignoresInvalidDigitsParameter() {
        // 为容错忽略非法 digits，仍按 6 导入。
        assertEquals(6, success("otpauth://totp/Google:account?secret=$validSecret&digits=abc").digits)
    }

    @Test
    fun ignoresPeriodParameter() {
        // 不读取 period，固定 30s。
        assertEquals(30, success("otpauth://totp/Google:account?secret=$validSecret&period=60").period)
    }

    @Test
    fun parsesAlgorithmCaseInsensitively() {
        val config = success("otpauth://totp/Google:account?secret=$validSecret&algorithm=sha256")
        assertEquals("SHA256", config.algorithm)
        assertEquals(6, config.digits)
    }

    @Test
    fun missingAlgorithmDefaultsToSha1() {
        assertEquals("SHA1", success(validSecretUri).algorithm)
    }

    @Test
    fun parsesRealWorldGoogleAuthenticatorUri() {
        val config = success(
            "otpauth://totp/ACME%20Co:john.doe@email.com" +
                "?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ&issuer=ACME%20Co" +
                "&algorithm=SHA1&digits=6&period=30",
        )
        assertEquals("ACME Co", config.issuer)
        assertEquals("john.doe@email.com", config.account)
        assertEquals("HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ", config.secret)
        assertEquals("ACME Co", config.displayName)
    }

    @Test
    fun decodesUtf8IssuerFromLabel() {
        val config = success("otpauth://totp/%E5%BE%AE%E8%BD%AF:user%40example.com?secret=$validSecret")
        assertEquals("微软", config.issuer)
        assertEquals("user@example.com", config.account)
    }

    @Test
    fun successfulParseProducesUsableConfig() {
        val config = success(validSecretUri)
        val code = TotpEngine.generate(config, 59_000L)
        assertEquals(6, code.length)
        assertTrue(code.all { it in '0'..'9' })
        assertNotNull(config.displayName)
        assertEquals("Google", config.displayName)
    }
}
