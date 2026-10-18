package com.paytech.billing.domainservice.validator.fact;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.fact.EconomicFactLinePayload;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.billing.core.enums.BillingCodes;
import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 经济事实登记不变量（原 TransactionPostingServiceImpl 的 Partner 一致性校验，任务卡 07 第 3.2 项、11a 第 3 项）
 * <li>local_partner_id、partner_agreement_oe_id、finance_entity_id 全有或全无。</li>
 * <li>交易事实（PAYMENT）：有 Partner 时必须存在 PARTNER_SHARE 与 PLATFORM_SERVICE_CHARGE 行，且服务费 party_id 等于
 * finance_entity_id；无 Partner 时两者都不得存在。</li>
 * <li>人工 / 成本调整：Partner 行只允许在 Partner 三元组存在时出现，平台服务费收方必须是头部 Finance Entity；付方与收方的
 * Client、Local Partner、Finance Entity、OE 标识必须等于头部对应主体。</li>
 * <li>只做纯内存校验，不访问数据库；ledger 只断言载荷完整性。</li>
 */
public final class EconomicFactInvariants {
    private static final Set<String> PARTNER_COMPONENTS =
        Set.of(AmountSource.PARTNER_SHARE.getCode(), AmountSource.PLATFORM_SERVICE_CHARGE.getCode());

    /** 必须与头部主体一致的付方 / 收方角色。 */
    private static final Set<FactPartyRole> HEADER_ROLES = Set.of(FactPartyRole.CLIENT, FactPartyRole.LOCAL_PARTNER,
        FactPartyRole.FINANCE_ENTITY, FactPartyRole.OPERATING_ENTITY);

    private static final Pattern DECIMAL_ID = Pattern.compile("[1-9][0-9]{0,18}");

    private EconomicFactInvariants() {
    }

    /** 校验交易事实的 Partner 三元组与 Partner 相关明细行一致；不满足时业务拒绝。 */
    public static void checkPartnerConsistency(EconomicFactPayload fact) {
        boolean hasPartner = checkPartnerTriple(fact);
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

    /**
     * 校验人工 / 成本调整事实：Partner 三元组、Partner 行前提、平台服务费收方与付方 / 收方主体一致性
     *
     * @param fact 由待登记事实头与明细行组装的载荷
     */
    public static void checkAdjustmentConsistency(EconomicFactPayload fact) {
        boolean hasPartner = checkPartnerTriple(fact);
        for (EconomicFactLinePayload line : fact.getLines()) {
            if (!hasPartner && PARTNER_COMPONENTS.contains(line.getComponentType())) {
                throw new BizCheckFailException("经济事实缺少合作方三元组，不能登记 " + line.getComponentType() + ": 行 "
                    + line.getLineNo() + ", 来源 " + fact.getSourceEventKey());
            }
            checkPartyIdentity(fact, line, line.getPayerRole(), line.getPayerId(), "付方");
            checkPartyIdentity(fact, line, line.getPayeeRole(), line.getPayeeId(), "收方");
        }
        if (hasPartner) {
            checkServiceChargePayee(fact);
            checkServiceChargeParty(fact);
        }
    }

    /**
     * Partner 三元组全有或全无
     *
     * @return 三元组是否存在
     */
    static boolean checkPartnerTriple(EconomicFactPayload fact) {
        long present = Stream.of(fact.getLocalPartnerId(), fact.getPartnerAgreementOeId(), fact.getFinanceEntityId())
            .filter(Objects::nonNull).count();
        if (present != 0 && present != 3) {
            throw new BizCheckFailException("交易经济结果 Partner 三元组不完整: " + fact.getFactId());
        }
        return present == 3;
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

    /** 人工 / 成本调整的平台服务费收方角色必须是 FINANCE_ENTITY。 */
    static void checkServiceChargePayee(EconomicFactPayload fact) {
        boolean mismatch = fact.getLines().stream()
            .filter(line -> AmountSource.PLATFORM_SERVICE_CHARGE.getCode().equals(line.getComponentType()))
            .anyMatch(line -> !FactPartyRole.FINANCE_ENTITY.getCode().equals(line.getPayeeRole()));
        if (mismatch) {
            throw new BizCheckFailException("平台服务费收方必须是 Finance Entity " + fact.getFinanceEntityId() + ": 来源 "
                + fact.getSourceEventKey());
        }
    }

    /** 付方或收方是头部主体角色时标识必须等于头部主体，其余角色只校验十进制标识格式。 */
    static void checkPartyIdentity(EconomicFactPayload fact, EconomicFactLinePayload line, String role, String id,
        String side) {
        FactPartyRole partyRole = BillingCodes.parse(FactPartyRole.class, side + "角色", role);
        boolean valid = HEADER_ROLES.contains(partyRole) ? Objects.equals(headerIdentity(fact, partyRole), id)
            : DECIMAL_ID.matcher(id).matches();
        if (!valid) {
            throw new BizCheckFailException("经济事实" + side + "与头部主体不一致: 行 " + line.getLineNo() + ", " + side + " "
                + role + "/" + id + ", 来源 " + fact.getSourceEventKey());
        }
    }

    /** 头部给出的主体标识；头部为空时返回空。 */
    static String headerIdentity(EconomicFactPayload fact, FactPartyRole role) {
        Object identity = switch (role) {
            case CLIENT -> fact.getClientId();
            case LOCAL_PARTNER -> fact.getLocalPartnerId();
            case FINANCE_ENTITY -> fact.getFinanceEntityId();
            case OPERATING_ENTITY -> fact.getEntityNo();
            default -> null;
        };
        return identity == null ? null : identity.toString();
    }
}
