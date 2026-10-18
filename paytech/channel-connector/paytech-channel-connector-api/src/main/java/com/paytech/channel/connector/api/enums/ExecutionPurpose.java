package com.paytech.channel.connector.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 配置读取用途；原交易及恢复只接受可信调用方冻结的三元组。 */
@Getter
@AllArgsConstructor
public enum ExecutionPurpose implements CodeEnum {
    /** NEW_PAYMENT 对应的稳定业务代码。 */
    NEW_PAYMENT("NEW_PAYMENT"),

    /** ORIGINAL_TRANSACTION 对应的稳定业务代码。 */
    ORIGINAL_TRANSACTION("ORIGINAL_TRANSACTION"),

    /** RECOVERY 对应的稳定业务代码。 */
    RECOVERY("RECOVERY");

    /** 数据库存储及业务比较使用的稳定代码。 */
    private final String code;
}
