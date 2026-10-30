package com.paytech.platform.support.dataquery.domain;

import java.time.Instant;

import com.paytech.platform.support.dataquery.core.enums.QueryKeyRole;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyTypeStatus;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyValueType;

import lombok.Builder;
import lombok.Getter;

/** data-query 对外发布的键类型事实；列表另附 ACTIVE 模板当前版本的引用数。 */
@Getter
@Builder(toBuilder = true)
public final class QueryKeyTypeDetail {

    /** 键类型 ID。 */
    private final long keyTypeId;

    /** 编码。 */
    private final String keyTypeCode;

    /** 显示名称。 */
    private final String keyTypeName;

    /** 业务分组显示名。 */
    private final String groupName;

    /** 值类型。 */
    private final QueryKeyValueType valueType;

    /** 默认角色。 */
    private final QueryKeyRole defaultRole;

    /** 是否可作为入口。 */
    private final boolean entryAllowed;

    /** 入口值校验正则；为 null 时不校验。 */
    private final String valuePattern;

    /** 入口输入提示。 */
    private final String inputHint;

    /** 顺序号。 */
    private final int sortNo;

    /** 说明。 */
    private final String description;

    /** 状态。 */
    private final QueryKeyTypeStatus status;

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

    /** ACTIVE 模板当前版本中引用该键类型的模板数；写入结果中为 0。 */
    private final long referencedTemplateCount;

    /**
     * 是否 ACTIVE。
     *
     * @return ACTIVE 为 true
     */
    public boolean active() {
        return status == QueryKeyTypeStatus.ACTIVE;
    }
}
