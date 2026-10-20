package com.paytech.channel.connector.service.processor.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

/** 能力组内一项待写入的能力明细；值类型由能力目录决定。 */
@Getter
@Setter
public class MidCapabilityDetailItem {
    /** 能力代码 */
    @NotBlank
    private String capabilityCode;

    /** BOOLEAN 值，可空 */
    private Boolean booleanValue;

    /** DECIMAL 值，可空 */
    private BigDecimal decimalValue;

    /** CODE 值，可空 */
    private String codeValue;
}
