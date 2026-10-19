package com.paytech.account.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * Owner 账户状态（t_owner_account.account_status）
 * <li>只约束桶移动等主动命令；计费事实照常入账，CLOSED 账户入账时记 WARN（设计 8.7）。本期没有改状态接口。</li>
 */
public enum AccountStatus implements CodeEnum {
    /** 正常 */
    ACTIVE,

    /** 冻结：拒绝桶移动命令 */
    FROZEN,

    /** 关闭：拒绝桶移动命令，事实入账告警 */
    CLOSED;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
