package com.paytech.payout.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 收款账户所属 Owner 类型（t_beneficiary_account.owner_type）
 * <li>PAYEE 为代发预留，本期不开放，不在本枚举中。</li>
 */
public enum BeneficiaryOwnerType implements CodeEnum {
    /** Client */
    CLIENT,

    /** Local Partner（本期由运营代维护） */
    LOCAL_PARTNER;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
