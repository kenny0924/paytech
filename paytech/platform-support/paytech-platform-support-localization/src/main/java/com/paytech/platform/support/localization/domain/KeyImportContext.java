package com.paytech.platform.support.localization.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.domain.source.KeyImportPlan;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次确认导入的上下文：事务前读取的应用、差异计划与已准备的新 key。 */
@Getter
@AllArgsConstructor
public class KeyImportContext {

    /** 事务前读取的应用；其 key 集合修订号即预览依据的修订号。 */
    private final I18nApplication application;

    /** 差异计划，已确认可导入。 */
    private final KeyImportPlan plan;

    /** 已分配主键、命名空间与初始值的新 key。 */
    private final List<I18nKey> newKeys;

    /** 新 key 所在命名空间的编码到事务前读取的命名空间 ID；事务内重证仍是同一行。 */
    private final Map<String, Long> namespaceIds;

    /** 操作人账号 ID。 */
    private final long actorId;

    /** 本次写入时间。 */
    private final Instant now;
}
