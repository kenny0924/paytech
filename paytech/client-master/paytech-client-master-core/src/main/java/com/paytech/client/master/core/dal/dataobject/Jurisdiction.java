package com.paytech.client.master.core.dal.dataobject;

import com.paytech.client.master.domain.enums.CatalogStatus;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 辖区包（t_jurisdiction）：只有身份与展示，规则在 t_rule_setting。 */
@Getter
@Setter
public class Jurisdiction {
    /** 辖区代码。 */
    private String jurisdictionCode;
    /** ISO 国家代码。 */
    private String countryCode;
    /** 展示名称文案键。 */
    private String labelKey;
    /** 本地地址格式代码。 */
    private String addressFormatCode;
    /** 状态。 */
    private CatalogStatus status;
    /** 目录并发版本。 */
    private Long changeVersion;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
