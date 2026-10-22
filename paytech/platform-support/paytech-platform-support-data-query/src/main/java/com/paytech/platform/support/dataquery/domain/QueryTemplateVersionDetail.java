package com.paytech.platform.support.dataquery.domain;

import java.time.Instant;

import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;

import lombok.Builder;
import lombok.Getter;

/** 模板某个版本的不可变内容，附该版本数据源的编码、名称与当前状态。 */
@Getter
@Builder
public final class QueryTemplateVersionDetail {

    /** 模板 ID。 */
    private final long templateId;

    /** 版本号。 */
    private final int versionNo;

    /** 数据源 ID。 */
    private final long dataSourceId;

    /** 数据源编码；数据源记录缺失时为 null。 */
    private final String dataSourceCode;

    /** 数据源名称；数据源记录缺失时为 null。 */
    private final String dataSourceName;

    /** 数据源当前状态；数据源记录缺失时为 null。 */
    private final DataSourceStatus dataSourceStatus;

    /** 版本内容。 */
    private final QueryTemplateDraft content;

    /** 恢复来源版本号；不是恢复生成时为 null。 */
    private final Integer restoredFromVersionNo;

    /** 变更说明。 */
    private final String changeNote;

    /** 保存人账号 ID。 */
    private final long createdBy;

    /** 保存时间。 */
    private final Instant createTime;
}
