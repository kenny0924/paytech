package com.paytech.channel.connector.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 回调项映射结论（t_channel_callback_item.mapping_status）。 */
@Getter
@AllArgsConstructor
public enum CallbackMappingStatus implements CodeEnum {
    /** 已映射到目标。 */
    MAPPED("MAPPED"),

    /** Body 不是 JSON、缺少字段或字段格式不符，无法映射。 */
    UNMATCHED("UNMATCHED"),

    /** 匹配歧义（本期不写入）。 */
    AMBIGUOUS("AMBIGUOUS"),

    /** 无需处理（本期不写入）。 */
    IGNORED("IGNORED");

    /** 数据库存储及业务比较使用的稳定代码。 */
    private final String code;
}
