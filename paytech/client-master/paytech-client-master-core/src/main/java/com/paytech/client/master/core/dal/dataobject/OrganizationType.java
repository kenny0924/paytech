package com.paytech.client.master.core.dal.dataobject;

import com.paytech.client.master.domain.enums.CatalogStatus;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 组织类型目录行（t_organization_type）。 */
@Getter
@Setter
public class OrganizationType {
    /** 目录行 ID。 */
    private Long organizationTypeId;
    /** 所属辖区。 */
    private String jurisdictionCode;
    /** 组织类型代码。 */
    private String typeCode;
    /** 展示名称文案键。 */
    private String labelKey;
    /** 展示顺序。 */
    private Integer displayOrder;
    /** 状态。 */
    private CatalogStatus status;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
