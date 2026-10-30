package com.paytech.account.test.builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactOriginType;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.api.fact.BillingFactLinePayload;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.billing.api.fact.BillingFactRefPayload;

/**
 * 计费事实（BillingFactRecorded Payload）的合法模板：交易事实（PAYMENT）、人工调整（MANUAL_ADJUSTMENT）与成本调整（COST_ADJUSTMENT）
 * <li>每条明细行都带付方 / 收方；只构造对象，不访问数据库；事实主键由用例分配，行号按追加顺序从 1 起。</li>
 * <li>渠道清算账户以 {@link FactRefType#CLEARING_ACCOUNT} 外部引用表达；业务时间取登记时间。</li>
 */
public final class BillingFactBuilder {
    /** 交易事实明细行中渠道角色的测试标识。 */
    public static final String CHANNEL_ID = "77";

    private final BillingFactPayload fact = new BillingFactPayload();

    /** 渠道清算账户：PAYMENT 行目录中渠道角色的付方 / 收方标识。 */
    private Long clearingAccountId;

    /** 待由 {@link #paymentLines} 生成的 SELLER_TAX 行（税务机关标识，税额），按登记顺序。 */
    private final List<Map.Entry<String, String>> sellerTaxes = new ArrayList<>();

    private BillingFactBuilder() {
    }

    /** PAYMENT 交易事实：Client、OE、币种、总额与渠道清算账户引用；版本 1。 */
    public static BillingFactBuilder payment(long factId, long clientId, String entityNo, String currency,
        String grossAmount, long clearingAccountId) {
        BillingFactBuilder builder = base(factId, BillingFactType.PAYMENT, FactOriginType.AUTO, clientId, entityNo,
            currency);
        builder.fact.setGrossAmount(new BigDecimal(grossAmount));
        builder.clearingAccountId = clearingAccountId;
        return builder.ref(FactRefType.CLEARING_ACCOUNT, String.valueOf(clearingAccountId));
    }

    /** MANUAL_ADJUSTMENT 人工调整事实；总额为明细行金额之和。 */
    public static BillingFactBuilder adjustment(long factId, long clientId, String entityNo, String currency) {
        BillingFactBuilder builder = base(factId, BillingFactType.MANUAL_ADJUSTMENT, FactOriginType.MANUAL, clientId,
            entityNo, currency);
        builder.fact.setGrossAmount(BigDecimal.ZERO);
        return builder;
    }

    /** COST_ADJUSTMENT 成本调整事实：渠道清算账户引用必填。 */
    public static BillingFactBuilder costAdjustment(long factId, long clientId, String entityNo, String currency,
        long clearingAccountId) {
        BillingFactBuilder builder = base(factId, BillingFactType.COST_ADJUSTMENT, FactOriginType.MANUAL, clientId,
            entityNo, currency);
        builder.fact.setGrossAmount(BigDecimal.ZERO);
        return builder.ref(FactRefType.CLEARING_ACCOUNT, String.valueOf(clearingAccountId));
    }

    /** 公共头字段。 */
    static BillingFactBuilder base(long factId, BillingFactType type, FactOriginType origin, long clientId,
        String entityNo, String currency) {
        BillingFactBuilder builder = new BillingFactBuilder();
        BillingFactPayload fact = builder.fact;
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        fact.setFactId(factId);
        fact.setFactVersion(1L);
        fact.setFactType(type.getCode());
        fact.setOriginType(origin.getCode());
        fact.setStatus(BillingFactStatus.APPROVED.getCode());
        fact.setSourceSystem("billing-test");
        fact.setSourceEventKey("fact-" + UUID.randomUUID());
        fact.setSourceVersion(1L);
        fact.setSourcePayloadHash((UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", ""));
        fact.setBusinessTime(now);
        fact.setRecordedTime(now);
        fact.setClientId(clientId);
        fact.setEntityNo(entityNo);
        fact.setCurrency(currency);
        fact.setRefs(new ArrayList<>());
        fact.setLines(new ArrayList<>());
        return builder;
    }

    /** 设置合作方二元组（Local Partner 与平台服务费 Finance Entity）。 */
    public BillingFactBuilder partner(long localPartnerId, long financeEntityId) {
        fact.setLocalPartnerId(localPartnerId);
        fact.setFinanceEntityId(financeEntityId);
        return this;
    }

    /** 追加一条外部引用。 */
    public BillingFactBuilder ref(FactRefType refType, String refId) {
        BillingFactRefPayload ref = new BillingFactRefPayload();
        ref.setRefType(refType.getCode());
        ref.setRefId(refId);
        fact.getRefs().add(ref);
        return this;
    }

    /** 本事实替代前一事实：版本为被替代事实版本 + 1。 */
    public BillingFactBuilder supersedes(long supersededFactId, long version) {
        fact.setSupersedesFactId(supersededFactId);
        fact.setFactVersion(version);
        return this;
    }

    /** 应付余额到期转可用时间（PAYMENT 由 billing 计算）。 */
    public BillingFactBuilder balanceAvailableTime(Instant time) {
        fact.setBalanceAvailableTime(time);
        return this;
    }

    /**
     * 交易事实明细行：付方 / 收方标识取事实头（OE 编号、Client、Local Partner、Finance Entity）或测试渠道；规则计算产生的行没有方向。
     */
    public BillingFactBuilder component(FactComponentType componentType, FactPartyRole payerRole,
        FactPartyRole payeeRole, String amount) {
        return line(componentType, payerRole, partyId(payerRole), payeeRole, partyId(payeeRole), null, amount);
    }

    /**
     * 登记一行卖方税，由 {@link #paymentLines} 在手续费之后生成（渠道清算账户 → 税务机关），并从本金中扣除
     *
     * @param taxAuthorityId 税务机关（纳税主体 Party）标识
     * @param amount 税额
     * @return 本构造器
     */
    public BillingFactBuilder sellerTax(String taxAuthorityId, String amount) {
        sellerTaxes.add(Map.entry(taxAuthorityId, amount));
        return this;
    }

    /**
     * 按 billing 自动登记的 PAYMENT 行目录（technique 4.6、tax 技术方案 11.2）追加明细行，全部 CHARGE，金额为零的组件不生成行
     * <li>顺序：CLIENT_FEE（渠道 → 经营主体）、已登记的 SELLER_TAX（渠道 → 税务机关）、CLIENT_PRINCIPAL（渠道 → Client，= 总额 − 手续费 − 税额）、
     * PARTNER_SHARE（经营主体 → Local Partner）、PLATFORM_SERVICE_CHARGE（经营主体 → Finance Entity）、CHANNEL_COST（经营主体 → 渠道）；
     * 渠道标识取清算账户。</li>
     * <li>合作方两行只在已设置合作方二元组时生成，须先调用 {@link #partner(long, long)}。</li>
     *
     * @param clientFee Client 手续费
     * @param partnerShare 合作方分润
     * @param serviceCharge 平台服务费
     * @param channelCost 渠道成本
     * @return 本构造器
     */
    public BillingFactBuilder paymentLines(String clientFee, String partnerShare, String serviceCharge,
        String channelCost) {
        String entityNo = fact.getEntityNo();
        String channel = String.valueOf(clearingAccountId);
        BigDecimal taxTotal = sellerTaxes.stream().map(tax -> new BigDecimal(tax.getValue())).reduce(BigDecimal.ZERO, BigDecimal::add);
        String principal = fact.getGrossAmount().subtract(new BigDecimal(clientFee)).subtract(taxTotal).toPlainString();
        chargeLine(FactComponentType.CLIENT_FEE, FactPartyRole.CHANNEL, channel, FactPartyRole.OPERATING_ENTITY, entityNo,
            clientFee);
        sellerTaxes.forEach(tax -> chargeLine(FactComponentType.SELLER_TAX, FactPartyRole.CHANNEL, channel,
            FactPartyRole.TAX_AUTHORITY, tax.getKey(), tax.getValue()));
        chargeLine(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.CHANNEL, channel, FactPartyRole.CLIENT,
            String.valueOf(fact.getClientId()), principal);
        if (fact.getLocalPartnerId() != null) {
            chargeLine(FactComponentType.PARTNER_SHARE, FactPartyRole.OPERATING_ENTITY, entityNo,
                FactPartyRole.LOCAL_PARTNER, String.valueOf(fact.getLocalPartnerId()), partnerShare);
            chargeLine(FactComponentType.PLATFORM_SERVICE_CHARGE, FactPartyRole.OPERATING_ENTITY, entityNo,
                FactPartyRole.FINANCE_ENTITY, String.valueOf(fact.getFinanceEntityId()), serviceCharge);
        }
        return chargeLine(FactComponentType.CHANNEL_COST, FactPartyRole.OPERATING_ENTITY, entityNo, FactPartyRole.CHANNEL,
            channel, channelCost);
    }

    /** 追加一条 CHARGE 行；金额为零不生成。 */
    BillingFactBuilder chargeLine(FactComponentType componentType, FactPartyRole payerRole, String payerId,
        FactPartyRole payeeRole, String payeeId, String amount) {
        if (new BigDecimal(amount).signum() == 0) {
            return this;
        }
        return line(componentType, payerRole, payerId, payeeRole, payeeId, FactLineDirection.CHARGE, amount);
    }

    /** 事实头中角色对应的标识；渠道取测试渠道。 */
    String partyId(FactPartyRole role) {
        return switch (role) {
            case OPERATING_ENTITY -> fact.getEntityNo();
            case CLIENT -> String.valueOf(fact.getClientId());
            case LOCAL_PARTNER -> String.valueOf(fact.getLocalPartnerId());
            case FINANCE_ENTITY -> String.valueOf(fact.getFinanceEntityId());
            case CHANNEL -> CHANNEL_ID;
            default -> throw new IllegalArgumentException("交易事实模板不支持角色 " + role.getCode());
        };
    }

    /** 调整明细行：金额组件、付方、收方与方向。 */
    public BillingFactBuilder line(FactComponentType componentType, FactPartyRole payerRole, String payerId,
        FactPartyRole payeeRole, String payeeId, FactLineDirection direction, String amount) {
        BillingFactLinePayload line = new BillingFactLinePayload();
        line.setLineNo(fact.getLines().size() + 1);
        line.setComponentType(componentType.getCode());
        line.setPayerRole(payerRole == null ? null : payerRole.getCode());
        line.setPayerId(payerId);
        line.setPayeeRole(payeeRole == null ? null : payeeRole.getCode());
        line.setPayeeId(payeeId);
        line.setDirection(direction == null ? null : direction.getCode());
        line.setPostedAmount(new BigDecimal(amount));
        fact.getLines().add(line);
        if (!BillingFactType.PAYMENT.getCode().equals(fact.getFactType())) {
            fact.setGrossAmount(fact.getGrossAmount().add(new BigDecimal(amount)));
        }
        return this;
    }

    /** 返回构造好的载荷。 */
    public BillingFactPayload build() {
        return fact;
    }
}
