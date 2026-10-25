package com.paytech.commercial.domainservice.validator.agreement;

import java.util.List;
import java.util.Objects;

import com.paytech.commercial.core.dal.dataobject.AgreementParty;
import com.paytech.commercial.domain.enums.AgreementPartyPartyRole;
import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 平台服务费的法律主体一致校验（决定 D3 ②，technique.md §3.3.3 / AC-10）
 * <li>Partner 合同上每个 OPERATING_ENTITY 角色行的 Party 都必须等于 LOCAL_PARTNER 角色行的 Party：本期 OE 提供方与 OE 是同一法律主体。</li>
 * <li>写入时近似校验，只影响之后的保存平台服务费价目与激活合同；精确校验在 Checkout 解析时按候选进行。</li>
 * <li>保存价目与激活合同两个入口共用；纯校验，不访问数据库。</li>
 */
public final class PartnerEntityPartyValidator {
    /** 纯静态校验。 */
    private PartnerEntityPartyValidator() {
    }

    /**
     * 校验合同参与方的 OE Party 与 Local Partner Party 一致
     *
     * @param agreementId 合同 ID，用于错误信息
     * @param parties 该合同的全部参与方行
     */
    public static void check(long agreementId, List<AgreementParty> parties) {
        List<Long> partnerParties = parties.stream()
            .filter(party -> party.getPartyRole() == AgreementPartyPartyRole.LOCAL_PARTNER).map(AgreementParty::getPartyId)
            .distinct().toList();
        if (partnerParties.isEmpty()) {
            return;
        }
        for (AgreementParty party : parties) {
            if (party.getPartyRole() != AgreementPartyPartyRole.OPERATING_ENTITY) {
                continue;
            }
            if (partnerParties.size() != 1 || !Objects.equals(party.getPartyId(), partnerParties.getFirst())) {
                throw new BizCheckFailException("Partner 合同的 OE 法律主体与 Local Partner 法律主体不一致: 合同 " + agreementId
                    + ", OE Party " + party.getPartyId() + ", Local Partner Party " + partnerParties);
            }
        }
    }
}
