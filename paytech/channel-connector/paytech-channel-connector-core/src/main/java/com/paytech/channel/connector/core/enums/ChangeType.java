package com.paytech.channel.connector.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 人工变更留痕的变更类型；只有 STATUS 与 CANCEL 记录变更前后状态。 */
@Getter
@AllArgsConstructor
public enum ChangeType implements CodeEnum {
    /** 创建 */
    CREATE("CREATE"),

    /** 普通字段更新 */
    UPDATE("UPDATE"),

    /** 状态变更 */
    STATUS("STATUS"),

    /** 账号参数与凭据配置写入 */
    CONFIGURATION("CONFIGURATION"),

    /** MID 改绑技术账号 */
    REBIND("REBIND"),

    /** 作废未到期的已批准成本版本 */
    CANCEL("CANCEL");

    /** 数据库存储及业务比较使用的稳定代码。 */
    private final String code;
}
