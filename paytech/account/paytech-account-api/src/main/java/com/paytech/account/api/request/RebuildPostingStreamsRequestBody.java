package com.paytech.account.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 按过账流重建主体与币种下的 Owner 账户余额。 */
@Getter
@Setter
public class RebuildPostingStreamsRequestBody {
    /** 主体类型：OPERATING_ENTITY 或 FINANCE_ENTITY。 */
    @NotBlank
    @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY", message = "主体类型非法")
    private String entityType;

    /** 主体标识：OE 为 entityNo，Finance Entity 为十进制主键。 */
    @NotBlank
    @Pattern(regexp = "[A-Z0-9]{1,19}", message = "主体标识非法")
    private String entityId;

    /** 大写 ISO 4217 币种。 */
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "币种非法")
    private String currency;

    /** 发起重建的内部操作人，规范十进制标识；只用于日志审计。 */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "操作人标识必须是正十进制整数字符串")
    private String operatorId;
}
