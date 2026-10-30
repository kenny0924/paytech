package com.paytech.payout.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 收款账户操作人类型（submitted_by_type、事件 actor_type）
 * <li>提交人只能是 CLIENT_MEMBER 或 OPERATIONS；SYSTEM 只出现在事件中。</li>
 */
public enum BeneficiaryActorType implements CodeEnum {
    /** Client 门户成员 */
    CLIENT_MEMBER,

    /** 运营人员 */
    OPERATIONS,

    /** 系统 */
    SYSTEM;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
