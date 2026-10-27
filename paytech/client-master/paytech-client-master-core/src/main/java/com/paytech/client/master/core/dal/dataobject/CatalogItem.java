package com.paytech.client.master.core.dal.dataobject;

import com.paytech.client.master.domain.enums.CatalogStatus;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 通用目录项（t_catalog_item）。 */
@Getter
@Setter
public class CatalogItem {
    /** 目录项 ID。 */
    private Long catalogItemId;
    /** 目录代码。 */
    private String catalogCode;
    /** 目录项代码。 */
    private String itemCode;
    /** 适用辖区；* 为全局。 */
    private String jurisdictionCode;
    /** 展示名称文案键。 */
    private String labelKey;
    /** 适用 Client 类型，逗号分隔；为空表示都适用。 */
    private String clientTypes;
    /** 展示顺序。 */
    private Integer displayOrder;
    /** 状态。 */
    private CatalogStatus status;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
