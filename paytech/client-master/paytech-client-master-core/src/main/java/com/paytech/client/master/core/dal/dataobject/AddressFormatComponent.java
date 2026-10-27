package com.paytech.client.master.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 地址格式组件（t_address_format_component）。 */
@Getter
@Setter
public class AddressFormatComponent {
    /** 组件行 ID。 */
    private Long componentId;
    /** 地址格式代码。 */
    private String addressFormatCode;
    /** 地址组件，等于草稿 address 分区字段名。 */
    private String component;
    /** 提交时是否必填。 */
    private Boolean requiredFlag;
    /** 取值目录代码；为空表示自由文本。 */
    private String catalogCode;
    /** 组件标题文案键。 */
    private String labelKey;
    /** 展示顺序。 */
    private Integer displayOrder;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
