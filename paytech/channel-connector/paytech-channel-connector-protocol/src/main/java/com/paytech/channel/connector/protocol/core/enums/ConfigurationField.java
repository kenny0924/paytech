package com.paytech.channel.connector.protocol.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 外部 API 配置变更摘要的受控字段名（沿用 Platform 审计的字段名）
 * <li>字段名不能由客户端提供；取值只记名称、编码与 SHA-256 摘要，不记录定义原文、参数或凭据。</li>
 */
@Getter
@AllArgsConstructor
public enum ConfigurationField implements CodeEnum {
    /** 对象编码。 */
    CODE("CODE"),

    /** 验证账号对应的渠道编码。 */
    CHANNEL("CHANNEL"),

    /** 验证用例使用的验证账号编码。 */
    TARGET("TARGET"),

    /** 显示名称。 */
    DISPLAY_NAME("DISPLAY_NAME"),

    /** 说明。 */
    DESCRIPTION("DESCRIPTION"),

    /** 请求字段组的规范化 SHA-256 摘要。 */
    REQUEST_FIELDS_DIGEST("REQUEST_FIELDS_DIGEST"),

    /** 响应字段组的规范化 SHA-256 摘要。 */
    RESPONSE_FIELDS_DIGEST("RESPONSE_FIELDS_DIGEST"),

    /** 接入方出站地址列表的规范化 SHA-256 摘要。 */
    ENDPOINTS_DIGEST("ENDPOINTS_DIGEST"),

    /** 原子 API 执行定义、流程定义或验证内容的规范化 SHA-256 摘要。 */
    DEFINITION_DIGEST("DEFINITION_DIGEST"),

    /** 公共脚本参数声明或验证账号参数的规范化 SHA-256 摘要。 */
    PARAMETERS_DIGEST("PARAMETERS_DIGEST"),

    /** 公共脚本源码的 SHA-256 摘要。 */
    SOURCE_DIGEST("SOURCE_DIGEST"),

    /** 公共脚本执行阶段。 */
    EXECUTION_STAGE("EXECUTION_STAGE");

    /** 变更摘要 JSON 中使用的稳定字段名。 */
    private final String code;
}
