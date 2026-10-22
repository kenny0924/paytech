package com.paytech.platform.support.dataquery.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 版本列表的一项。 */
@Getter
@AllArgsConstructor
public final class QueryTemplateVersionSummary {

    /** 版本号。 */
    private final int versionNo;

    /** 该版本的数据源 ID。 */
    private final long dataSourceId;

    /** 该版本的数据源编码；数据源记录缺失时为 null。 */
    private final String dataSourceCode;

    /** 恢复来源版本号。 */
    private final Integer restoredFromVersionNo;

    /** 变更说明。 */
    private final String changeNote;

    /** 保存人账号 ID。 */
    private final long createdBy;

    /** 保存时间。 */
    private final Instant createTime;
}
