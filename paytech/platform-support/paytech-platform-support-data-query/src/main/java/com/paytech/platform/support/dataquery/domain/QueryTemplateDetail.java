package com.paytech.platform.support.dataquery.domain;

import java.time.Instant;

import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;

import lombok.Builder;
import lombok.Getter;

/** data-query 对外发布的模板事实：模板头与当前版本内容。 */
@Getter
@Builder(toBuilder = true)
public final class QueryTemplateDetail {

    /** 模板 ID。 */
    private final long templateId;

    /** 模板编码。 */
    private final String templateCode;

    /** 模板名称。 */
    private final String templateName;

    /** 说明。 */
    private final String description;

    /** 状态。 */
    private final QueryTemplateStatus status;

    /** 当前版本号。 */
    private final int currentVersionNo;

    /** 乐观并发版本。 */
    private final int version;

    /** 创建人账号 ID。 */
    private final long createdBy;

    /** 最后修改人账号 ID。 */
    private final long updatedBy;

    /** 创建时间。 */
    private final Instant createTime;

    /** 最后修改时间。 */
    private final Instant updateTime;

    /** 当前版本内容。 */
    private final QueryTemplateVersionDetail currentVersion;
}
