package com.paytech.platform.support.localization.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次翻译保存或导入的上下文：已校验的写入与本次时间。 */
@Getter
@AllArgsConstructor
public class TranslationWriteContext {

    /** 事务前读取的应用。 */
    private final I18nApplication application;

    /** 已通过 ICU 与版本校验的写入，按请求顺序。 */
    private final List<TranslationWrite> writes;

    /** 本次写入时间。 */
    private final Instant now;
}
