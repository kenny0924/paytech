package com.paytech.client.master.core.dal.dataobject;

import com.paytech.client.master.domain.enums.CatalogStatus;
import com.paytech.client.master.domain.enums.ClientType;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 入驻采集 Schema 版本（t_onboarding_schema）。 */
@Getter
@Setter
public class OnboardingSchema {
    /** Schema 版本 ID。 */
    private Long schemaId;
    /** 辖区代码。 */
    private String jurisdictionCode;
    /** 主主体类型。 */
    private ClientType partyType;
    /** 业务角色，本期为 CLIENT。 */
    private String roleType;
    /** 专用组织类型；* 为默认。 */
    private String organizationTypeCode;
    /** 版本号。 */
    private Integer versionNo;
    /** 状态。 */
    private CatalogStatus status;
    /** ACTIVE 为 1，其他为空。 */
    private Integer activeSlot;
    /** 发布人 Operations Account，可空。 */
    private Long approvedBy;
    /** 发布 UTC 时间，可空。 */
    private Instant approvedTime;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
