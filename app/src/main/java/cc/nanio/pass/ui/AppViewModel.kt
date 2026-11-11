package cc.nanio.pass.ui


import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.nanio.pass.Graph
import cc.nanio.pass.data.model.PasswordEntity
import cc.nanio.pass.totp.TotpConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全应用共享的 UI 状态与操作入口。
 * 当前是单用户单进程的本地密码管理器，状态量小，集中在一个 ViewModel 即可。
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    var passwords by mutableStateOf<List<PasswordEntity>>(emptyList())
        private set
    var deleted by mutableStateOf<List<PasswordEntity>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set

    /** 添加动态令牌流程中，待挂载到新建密码的令牌配置 */
    var pendingTotp by mutableStateOf<TotpConfig?>(null)
        private set

    fun requestPendingTotp(config: TotpConfig?) {
        pendingTotp = config
    }

    fun consumePendingTotp(): TotpConfig? {
        val pending = pendingTotp
        pendingTotp = null
        return pending
    }

    /** 设置：是否启用生物识别解锁 */
    var biometricEnabled by mutableStateOf(false)
        private set
    /** 设置：自动锁定分钟数（0 = 立即/关闭） */
    var autoLockMinutes by mutableStateOf(1)
        private set

    private val prefs = app.getSharedPreferences("settings", 0)

    init {
        biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC, false)
        autoLockMinutes = prefs.getInt(KEY_AUTO_LOCK, 1)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val repo = Graph.repository
            val active = withContext(Dispatchers.IO) { runCatching { repo.listActive() }.getOrDefault(emptyList()) }
            val recycle = withContext(Dispatchers.IO) { runCatching { repo.listDeleted() }.getOrDefault(emptyList()) }
            passwords = active
            deleted = recycle
            loaded = true
        }
    }

    fun save(entity: PasswordEntity, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val repo = Graph.repository
            val id = withContext(Dispatchers.IO) {
                runCatching {
                    if (entity.id == 0L) repo.insert(entity) else {
                        repo.update(entity); entity.id
                    }
                }.getOrDefault(0L)
            }
            refresh()
            if (id != 0L) onDone(id)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { Graph.repository.softDelete(id) } }
            refresh()
        }
    }

    fun restore(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { Graph.repository.restore(id) } }
            refresh()
        }
    }

    fun purge(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { Graph.repository.purge(id) } }
            refresh()
        }
    }

    fun purgeAllDeleted() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { Graph.repository.purgeAllDeleted() } }
            refresh()
        }
    }

    fun attachTotp(
        id: Long,
        config: TotpConfig,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    Graph.repository.attachTotp(
                        id = id,
                        secret = config.secret,
                        issuer = config.issuer,
                        totpAccount = config.account,
                        algorithm = config.algorithm,
                        digits = config.digits,
                    )
                }.isSuccess
            }
            refresh()
            if (ok) onDone()
        }
    }

    fun detachTotp(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { Graph.repository.detachTotp(id) } }
            refresh()
        }
    }

    /** 清空所有数据（设置页「清空所有数据」） */
    fun clearAllData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { Graph.repository.clearAll() } }
            refresh()
        }
    }

    fun touchLastUsed(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { Graph.repository.touchLastUsed(id) } }
        }
    }

    fun findById(id: Long): PasswordEntity? = passwords.firstOrNull { it.id == id }

    fun updateBiometricEnabled(enabled: Boolean) {
        biometricEnabled = enabled
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    }

    fun updateAutoLockMinutes(minutes: Int) {
        autoLockMinutes = minutes
        prefs.edit().putInt(KEY_AUTO_LOCK, minutes).apply()
    }

    // ---------------------------------------------------------------- P1：加密导入导出

    fun exportBackup(passphrase: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    cc.nanio.pass.data.ExportManager.export(passwords, passphrase)
                }
            }
            onResult(result)
        }
    }

    fun importBackup(
        bytes: ByteArray,
        passphrase: String,
        onResult: (Result<cc.nanio.pass.data.ExportManager.ImportSummary>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val entries = cc.nanio.pass.data.ExportManager.import(bytes, passphrase)
                    val existing = Graph.repository.listActive() + Graph.repository.listDeleted()
                    val keys = existing.map { importKey(it) }.toMutableSet()
                    var imported = 0
                    var skipped = 0
                    entries.forEach { entry ->
                        if (!keys.add(importKey(entry))) {
                            skipped++
                        } else {
                            Graph.repository.insert(entry)
                            imported++
                        }
                    }
                    cc.nanio.pass.data.ExportManager.ImportSummary(imported, skipped)
                }
            }
            if (result.isSuccess) refresh()
            onResult(result)
        }
    }

    /** 导入去重键：名称 + 账号 + 密码 + 绑定包名 + TOTP 密钥。 */
    private fun importKey(entry: PasswordEntity): String =
        listOf(
            entry.label.trim(),
            entry.account.trim(),
            entry.password,
            entry.packageName.orEmpty(),
            entry.totpSecret.orEmpty(),
        ).joinToString("\u0001")

    private companion object {
        const val KEY_BIOMETRIC = "biometric_enabled"
        const val KEY_AUTO_LOCK = "auto_lock_minutes"
    }
}
