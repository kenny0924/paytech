package com.paytech.tax.core.dal.dataobject;

import java.time.Instant;

import com.paytech.tax.core.enums.TaxCategoryStatus;

import lombok.Getter;
import lombok.Setter;

/** t_tax_category：税类目录；初始化数据固定 ID 1–999。 */
@Getter
@Setter
public class TaxCategory {
    /** Snowflake 主键 */
    private Long id;

    /** 税类代码，例如 SAAS_AUTOMATED */
    private String categoryCode;

    /** 名称 */
    private String name;

    /** 边界说明；可空 */
    private String description;

    /** 状态 */
    private TaxCategoryStatus status;

    /** 创建时间 */
    private Instant createTime;

    /** 更新时间 */
    private Instant updateTime;
}
