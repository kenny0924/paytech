package com.paytech.account.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * account 明细账 Owner 账户的核算对象类型
 * <li>只包含 account 拥有明细的对象；平台类对象（OE、Finance Entity、渠道、税务机关）的账户只在 ledger，见 ledger-api {@code LedgerOwnerType}。</li>
 */
public enum OwnerType implements CodeEnum {
    /** Client，ownerId 为正十进制 Client 标识 */
    CLIENT,

    /** Local Partner，ownerId 为正十进制 Partner 标识 */
    LOCAL_PARTNER,

    /** 平台或经营主体银行账户（BANK_CASH 的出款容量），ownerId 为正十进制银行账户标识 */
    CASH_ACCOUNT;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
