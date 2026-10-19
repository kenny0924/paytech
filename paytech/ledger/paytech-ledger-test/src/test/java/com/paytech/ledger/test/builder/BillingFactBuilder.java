package com.paytech.ledger.test.builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.UUID;

import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactOriginType;
import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.billing.api.fact.BillingFactRefPayload;

/**
 * 计费事实载荷的合法模板：PAYMENT、来源 payment、唯一事件键，头部必填字段给出稳定默认值
 * <li>作为 billing 事实详情 DTO 的来源（版本 1、批准时间为当前时刻），不写 billing 表；PAYMENT 的业务时间（支付成功时间）默认为一分钟前，
 * 落在 LedgerMocker 准备的当月 OPEN 期间内；改为人工 / 成本调整时业务时间与 billing 一致取批准时间。</li>
 * <li>渠道清算账户以外部引用 {@link FactRefType#CLEARING_ACCOUNT} 给出。</li>
 * <li>只构造对象，不访问数据库；事实主键由用例分配，明细行由 {@link BillingFactLineBuilder} 构造后按顺序编号。</li>
 */
public final class BillingFactBuilder {
    /** 计费事实的来源系统。 */
    public static final String PAYMENT_SOURCE_SYSTEM = "payment";

    private final BillingFactPayload fact = new BillingFactPayload();

    private BillingFactBuilder() {
    }

    /** 无 Partner 的 PAYMENT 模板；渠道清算账户主键以 CLEARING_ACCOUNT 引用给出。 */
    public static BillingFactBuilder template(String entityNo, long clientId, String currency,
        String grossAmount, long channelAccountId) {
        BillingFactBuilder builder = new BillingFactBuilder();
        BillingFactPayload fact = builder.fact;
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        fact.setFactVersion(1L);
        fact.setRecordedTime(now);
        fact.setFactType(BillingFactType.PAYMENT.getCode());
        fact.setOriginType(FactOriginType.AUTO.getCode());
        fact.setStatus(BillingFactStatus.APPROVED.getCode());
        fact.setSourceSystem(PAYMENT_SOURCE_SYSTEM);
        fact.setSourceEventKey("payment-succeeded-" + UUID.randomUUID());
        fact.setSourceVersion(1L);
        fact.setSourcePayloadHash(hex64());
        fact.setBusinessTime(now.minus(1, ChronoUnit.MINUTES));
        fact.setClientId(clientId);
        fact.setEntityNo(entityNo);
        fact.setCurrency(currency);
        fact.setGrossAmount(new BigDecimal(grossAmount));
        fact.setRefs(new ArrayList<>());
        fact.setLines(new ArrayList<>());
        return builder.channelAccountId(channelAccountId);
    }

    /** 设置 Partner 二元组：Local Partner 与平台服务费收取方 Finance Entity。 */
    public BillingFactBuilder partner(long localPartnerId, long financeEntityId) {
        fact.setLocalPartnerId(localPartnerId);
        fact.setFinanceEntityId(financeEntityId);
        return this;
    }

    /** 单独覆盖 Local Partner。 */
    public BillingFactBuilder localPartnerId(Long localPartnerId) {
        fact.setLocalPartnerId(localPartnerId);
        return this;
    }

    /** 单独覆盖平台服务费 Finance Entity。 */
    public BillingFactBuilder financeEntityId(Long financeEntityId) {
        fact.setFinanceEntityId(financeEntityId);
        return this;
    }

    /** 覆盖渠道清算账户：替换 CLEARING_ACCOUNT 引用，null 表示不带该引用（载荷缺失场景）。 */
    public BillingFactBuilder channelAccountId(Long channelAccountId) {
        fact.getRefs().removeIf(ref -> FactRefType.CLEARING_ACCOUNT.getCode().equals(ref.getRefType()));
        if (channelAccountId != null) {
            BillingFactRefPayload ref = new BillingFactRefPayload();
            ref.setRefType(FactRefType.CLEARING_ACCOUNT.getCode());
            ref.setRefId(channelAccountId.toString());
            fact.getRefs().add(ref);
        }
        return this;
    }

    /** 覆盖业务时间，可为 null（载荷缺失场景）。 */
    public BillingFactBuilder businessTime(Instant businessTime) {
        fact.setBusinessTime(businessTime);
        return this;
    }

    /** 替代给定事实：记录被替代事实主键，版本为其版本 + 1。 */
    public BillingFactBuilder supersedes(BillingFactPayload previous) {
        fact.setSupersedesFactId(previous.getFactId());
        fact.setFactVersion(previous.getFactVersion() + 1);
        return this;
    }

    /** 覆盖计费事实类型；非 PAYMENT 为人工登记，业务时间取批准时间（同 billing 批准时写入）。 */
    public BillingFactBuilder factType(BillingFactType factType) {
        fact.setFactType(factType.getCode());
        if (factType != BillingFactType.PAYMENT) {
            fact.setOriginType(FactOriginType.MANUAL.getCode());
            fact.setBusinessTime(fact.getRecordedTime());
        }
        return this;
    }

    /** 分配事实主键并按入参顺序追加明细行（行号从 1 开始），返回构造好的载荷。 */
    public BillingFactPayload build(long factId, BillingFactLineBuilder... components) {
        fact.setFactId(factId);
        int lineNo = fact.getLines().size() + 1;
        for (BillingFactLineBuilder component : components) {
            fact.getLines().add(component.build(lineNo++, fact));
        }
        return fact;
    }

    /** 64 位小写十六进制占位摘要。 */
    static String hex64() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }
}
