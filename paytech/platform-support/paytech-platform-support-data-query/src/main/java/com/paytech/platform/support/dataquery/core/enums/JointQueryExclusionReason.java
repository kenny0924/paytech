package com.paytech.platform.support.dataquery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 配置了联合查询却不参与的原因；只用于提示与可达预览，不持久化。 */
@Getter
@RequiredArgsConstructor
public enum JointQueryExclusionReason implements CodeEnum {

    /** 键参数不是必填（或未声明）。 */
    KEY_PARAMETER_NOT_REQUIRED("KEY_PARAMETER_NOT_REQUIRED"),

    /** 除键参数外还有必填且没有默认值的参数。 */
    OTHER_REQUIRED_PARAMETER("OTHER_REQUIRED_PARAMETER"),

    /** 模板未启用。 */
    TEMPLATE_INACTIVE("TEMPLATE_INACTIVE"),

    /** 当前版本的数据源不存在或未启用。 */
    DATA_SOURCE_INACTIVE("DATA_SOURCE_INACTIVE"),

    /** 引用的键类型不存在或已停用；只在可达预览与执行时出现（保存与启用会拒绝这种配置）。 */
    KEY_TYPE_INACTIVE("KEY_TYPE_INACTIVE");

    private final String code;
}
