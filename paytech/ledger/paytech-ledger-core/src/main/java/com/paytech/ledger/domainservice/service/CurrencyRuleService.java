package com.paytech.ledger.domainservice.service;

import com.paytech.ledger.domain.CurrencyRule;

/**
 * 币种规则读取：业务代码读取币种精度与状态的唯一入口
 * <li>只读参考数据客户端的进程内快照，从不发起远程调用，可在数据库事务内使用；查不到币种时以可重试的系统异常失败，快照按刷新周期回源后恢复。</li>
 */
public interface CurrencyRuleService {

    /**
     * 读取启用过的币种规则（ACTIVE 或 INACTIVE），用于已有账簿的过账、调整、试算、关账与查询
     *
     * @param currency ISO 4217 币种代码
     * @return 币种规则
     * @throws com.paytech.platform.support.referencedata.client.ReferenceDataNotFoundException 快照中没有该币种（系统异常，可重试，刷新后恢复）
     * @throws com.paytech.foundation.common.error.BizCheckFailException 币种仍为 DRAFT
     */
    CurrencyRule requireUsable(String currency);

    /**
     * 读取 ACTIVE 币种规则，用于新开账簿等新用途
     *
     * @param currency ISO 4217 币种代码
     * @return 币种规则
     * @throws com.paytech.platform.support.referencedata.client.ReferenceDataNotFoundException 快照中没有该币种（系统异常，可重试）
     * @throws com.paytech.foundation.common.error.BizCheckFailException 状态不是 ACTIVE
     */
    CurrencyRule requireActive(String currency);
}
