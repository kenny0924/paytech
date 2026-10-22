package com.paytech.card.domain.binlibrary;

/** BIN 库写入口共用的资源锁：创建版本与激活 / 回切都改变“版本号序列与唯一生效版本”这一全局资源，须串行。 */
public final class BinLibraryLock {
    /** 资源锁 Key（Foundation 自动加 lock: 前缀与服务 Namespace）。 */
    public static final String KEY = "card-bin-library";

    private BinLibraryLock() {
    }
}
