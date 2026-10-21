package com.paytech.reconciliation.domain;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.reconciliation.api.enums.AccountPurpose;
import com.paytech.reconciliation.api.enums.CashAccountStatus;
import com.paytech.reconciliation.api.enums.ReconEntityType;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;

/** 清算账户新建、改配置与挂 MID 共用的纯校验（UC-R01）；只依据调用方已读取的事实。 */
public final class ClearingAccountRules {
    /** channel-connector MID 状态：已激活。 */
    static final String MID_ACTIVE = "ACTIVE";

    private ClearingAccountRules() {
    }

    /**
     * 到账银行账户必须是同经营主体、同币种、用途 CHANNEL_SETTLEMENT 的 ACTIVE 银行账户
     *
     * @param cashAccountId 请求的到账银行账户主键
     * @param cashAccount 读取到的银行账户，可为空
     * @param entityNo 清算账户经营主体
     * @param currency 清算账户币种
     */
    public static void checkSettlementCashAccount(Long cashAccountId, CashAccount cashAccount, String entityNo,
        String currency) {
        String context = "到账银行账户 " + cashAccountId + ", 经营主体 " + entityNo + ", 币种 " + currency;
        if (cashAccount == null || cashAccount.getStatus() != CashAccountStatus.ACTIVE) {
            throw new BizCheckFailException("到账银行账户不存在或未生效: " + context);
        }
        boolean sameScope = cashAccount.getEntityType() == ReconEntityType.OPERATING_ENTITY
            && entityNo.equals(cashAccount.getEntityId()) && currency.equals(cashAccount.getCurrency());
        if (!sameScope || cashAccount.getAccountPurpose() != AccountPurpose.CHANNEL_SETTLEMENT) {
            throw new BizCheckFailException("到账银行账户必须是同经营主体、同币种、渠道结算用途的账户: " + context
                + ", 实际 " + cashAccount.getEntityId() + "/" + cashAccount.getCurrency() + "/"
                + cashAccount.getAccountPurpose().getCode());
        }
    }

    /**
     * 挂入的 MID 必须存在、ACTIVE，渠道与经营主体和清算账户一致，能力组含清算币种
     *
     * @param midId MID 主键
     * @param mid channel-connector 返回的 MID 事实，可为空（不存在）
     * @param channelCode 清算账户渠道
     * @param entityNo 清算账户经营主体
     * @param currency 清算账户币种
     */
    public static void checkMid(long midId, MidProfile mid, String channelCode, String entityNo, String currency) {
        String context = "MID " + midId + ", 清算账户渠道 " + channelCode + ", 经营主体 " + entityNo + ", 币种 " + currency;
        if (mid == null) {
            throw new BizCheckFailException("MID 不存在: " + context);
        }
        if (!MID_ACTIVE.equals(mid.getStatus())) {
            throw new BizCheckFailException("MID 未激活: " + context + ", MID 状态 " + mid.getStatus());
        }
        if (!channelCode.equals(mid.getChannelCode())) {
            throw new BizCheckFailException("MID 渠道与清算账户不一致: " + context + ", MID 渠道 " + mid.getChannelCode());
        }
        if (!entityNo.equals(mid.getEntityNo())) {
            throw new BizCheckFailException("MID 经营主体与清算账户不一致: " + context + ", MID 经营主体 " + mid.getEntityNo());
        }
        if (!mid.getCurrencies().contains(currency)) {
            throw new BizCheckFailException("MID 能力组不含清算币种: " + context + ", 能力组币种 " + mid.getCurrencies());
        }
    }
}
