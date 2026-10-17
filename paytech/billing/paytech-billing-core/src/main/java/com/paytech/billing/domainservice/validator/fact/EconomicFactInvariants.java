package com.paytech.billing.domainservice.validator.fact;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.fact.EconomicFactLinePayload;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 经济事实登记不变量（原 TransactionPostingServiceImpl 的 Partner 一致性校验，任务卡 07 第 3.2 项）
 * <li>local_partner_id、partner_agreement_oe_id、finance_entity_id 全有或全无。</li>
 * <li>有 Partner 时必须存在 PARTNER_SHARE 与 PLATFORM_SERVICE_CHARGE 行，且服务费 party_id 等于 finance_entity_id；无 Partner 时两者都不得存在。</li>
 * <li>本步只提供静态校验；第 11a 步由 billing 登记经济事实时调用，ledger 只断言载荷完整性。</li>
 */
public final class EconomicFactInvariants {

    private EconomicFactInvariants() {
    }

    /** 校验 Partner 三元组与 Partner 相关明细行一致；不满足时业务拒绝。 */
    public static void checkPartnerConsistency(EconomicFactPayload fact) {
        long present = Stream.of(fact.getLocalPartnerId(), fact.getPartnerAgreementOeId(), fact.getFinanceEntityId())
            .filter(Objects::nonNull).count();
        if (present != 0 && present != 3) {
            throw new BizCheckFailException("交易经济结果 Partner 三元组不完整: " + fact.getFactId());
        }
        boolean hasPartner = present == 3;
        List<EconomicFactLinePayload> lines = fact.getLines();
        for (AmountSource source : List.of(AmountSource.PARTNER_SHARE, AmountSource.PLATFORM_SERVICE_CHARGE)) {
            boolean exists = lines.stream().anyMatch(line -> source.getCode().equals(line.getComponentType()));
            if (exists != hasPartner) {
                throw new BizCheckFailException("交易经济结果 Partner 组件不一致: " + fact.getFactId() + ", 组件 "
                    + source.getCode() + (hasPartner ? " 缺失" : " 不应存在"));
            }
        }
        if (hasPartner) {
            checkServiceChargeParty(fact);
        }
    }

    /** 平台服务费行的 party_id 必须逐值等于头 finance_entity_id。 */
    static void checkServiceChargeParty(EconomicFactPayload fact) {
        String financeEntityId = String.valueOf(fact.getFinanceEntityId());
        boolean mismatch = fact.getLines().stream()
            .filter(line -> AmountSource.PLATFORM_SERVICE_CHARGE.getCode().equals(line.getComponentType()))
            .anyMatch(line -> !financeEntityId.equals(line.getPartyId()));
        if (mismatch) {
            throw new BizCheckFailException("交易经济结果平台服务费归属与 Finance Entity 不一致: " + fact.getFactId()
                + ", Finance Entity " + financeEntityId);
        }
    }
}
