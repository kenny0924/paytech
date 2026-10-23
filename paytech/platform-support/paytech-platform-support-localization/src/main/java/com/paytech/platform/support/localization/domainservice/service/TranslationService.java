package com.paytech.platform.support.localization.domainservice.service;

import com.paytech.platform.support.localization.domain.TranslationWriteContext;

/**
 * 翻译的本地保存；不访问外部能力。
 * <li>须在调用方的本地事务内调用：锁应用行，逐条按版本写入，文案修订号加一；任一条冲突整体回滚。</li>
 */
public interface TranslationService {

    /**
     * 保存翻译。
     *
     * @param context 已校验的写入；为空时不写入、不推进修订号
     * @return 保存后的文案修订号
     */
    int saveTranslations(TranslationWriteContext context);
}
