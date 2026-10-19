package com.paytech.ledger.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * ledger 统一账户的核算对象类型
 * <li>只有平台类对象；Client、Local Partner 的明细在 account（account-api {@code OwnerType}），ledger 以控制科目记录，Owner 为账簿主体。</li>
 * <li>控制科目（CLIENT_PAYABLE、CLIENT_SECURITY_DEPOSIT、CLIENT_RECOVERY_RECEIVABLE、PARTNER_PAYABLE、PARTNER_RECOVERY_RECEIVABLE）只允许
 * OPERATING_ENTITY / FINANCE_ENTITY 且 ownerId 等于账簿主体；所有账户余额桶一律 NONE。</li>
 */
public enum LedgerOwnerType implements CodeEnum {
    /** CCT 经营主体，ownerId 为 entityNo */
    OPERATING_ENTITY,

    /** Finance 内部财务主体，ownerId 为 ledger 本地主键十进制字符串 */
    FINANCE_ENTITY,

    /** 渠道技术账户，ownerId 为 billing 给出的渠道清算账户主键 */
    CHANNEL_ACCOUNT,

    /** 平台或经营主体银行账户 */
    CASH_ACCOUNT,

    /** 税务机关 */
    TAX_AUTHORITY;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
