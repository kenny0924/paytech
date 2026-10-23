package com.paytech.platform.support.localization.domainservice.service;

import com.paytech.platform.support.localization.domain.KeyImportContext;

/**
 * key 导入的本地保存（技术方案 3.5）；不访问外部能力。
 * <li>须在调用方的本地事务内调用：锁应用行，核对 key 集合修订号与命名空间后整体写入，key 集合与文案修订号各加一。</li>
 */
public interface KeyImportService {

    /**
     * 确认导入。
     *
     * @param context 已校验可导入的上下文
     * @throws com.paytech.foundation.common.error.FailException 修订号已变（I18N_IMPORT_PREVIEW_STALE）或命名空间已不存在
     *         （I18N_NAMESPACE_NOT_FOUND）
     */
    void importKeys(KeyImportContext context);
}
