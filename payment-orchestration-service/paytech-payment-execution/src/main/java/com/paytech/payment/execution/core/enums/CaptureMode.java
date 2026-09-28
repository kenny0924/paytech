package com.paytech.payment.execution.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** CaptureMode 的数据库稳定代码。 */
@Getter
@AllArgsConstructor
public enum CaptureMode implements CodeEnum {
    AUTOMATIC("AUTOMATIC"),

    MANUAL("MANUAL");

    /** 稳定代码 */
    final String code;
}