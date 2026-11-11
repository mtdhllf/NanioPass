package cc.nanio.pass.autofill;

import android.content.IntentSender;
import android.service.autofill.SaveCallback;

/**
 * SaveCallback.onSuccess 在不同 Android 版本对 null IntentSender 的处理不一致：
 * Android 17（SDK 37）会 Objects.requireNonNull 抛 NPE，因此这里优先使用无参重载；
 * 若目标 API 没有无参重载则回退到传入 null（旧版本行为）。
 */
public final class AutofillCompat {

    private AutofillCompat() {
    }

    public static void success(SaveCallback callback) {
        try {
            // no-arg 重载在部分 API 存在且允许不启动额外界面
            callback.onSuccess();
        } catch (Throwable t) {
            try {
                callback.onSuccess((IntentSender) null);
            } catch (Throwable ignored) {
                // 平台仍拒绝 null 时忽略：数据已保存，不影响功能
            }
        }
    }
}
