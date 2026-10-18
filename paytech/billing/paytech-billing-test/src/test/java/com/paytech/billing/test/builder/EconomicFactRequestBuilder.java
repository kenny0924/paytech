package com.paytech.billing.test.builder;

import java.util.ArrayList;
import java.util.UUID;

import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.enums.EconomicsType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.request.EconomicFactLineRequestBody;
import com.paytech.billing.api.request.SubmitEconomicFactRequestBody;

/**
 * 经济事实提交请求构建器
 * <li>template() 给出合法的人工调整：OE 向 Client 付 CLIENT_PRINCIPAL 10.00 USD，来源引用每次随机；负例按需覆盖字段。</li>
 */
public final class EconomicFactRequestBuilder {
    /** 标准来源系统。 */
    public static final String SOURCE_SYSTEM = "OPERATIONS";

    /** 标准 Client。 */
    public static final String CLIENT_ID = "300001";

    /** 标准 Operating Entity。 */
    public static final String ENTITY_NO = "AE001";

    /** 标准币种（初始化 DML 中 ACTIVE，精度 2）。 */
    public static final String CURRENCY = "USD";

    /** 编制人。 */
    public static final String MAKER_ID = "81001";

    /** 合作方三元组。 */
    public static final String LOCAL_PARTNER_ID = "400001";

    /** Partner Agreement 与 OE 的关联 ID。 */
    public static final String PARTNER_AGREEMENT_OE_ID = "410001";

    /** 平台 Finance Entity。 */
    public static final String FINANCE_ENTITY_ID = "520001";

    private final SubmitEconomicFactRequestBody body = new SubmitEconomicFactRequestBody();

    private EconomicFactRequestBuilder() {
    }

    /** 合法的单行人工调整模板。 */
    public static EconomicFactRequestBuilder template() {
        EconomicFactRequestBuilder builder = new EconomicFactRequestBuilder();
        builder.body.setFactType(EconomicsType.MANUAL_ADJUSTMENT.getCode());
        builder.body.setSourceSystem(SOURCE_SYSTEM);
        builder.body.setSourceReference("ADJ-" + UUID.randomUUID().toString().substring(0, 8));
        builder.body.setClientId(CLIENT_ID);
        builder.body.setEntityNo(ENTITY_NO);
        builder.body.setCurrency(CURRENCY);
        builder.body.setMakerId(MAKER_ID);
        builder.body.setLines(new ArrayList<>());
        return builder.addLine(AmountSource.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, ENTITY_NO,
            FactPartyRole.CLIENT, CLIENT_ID, "10.00", FactLineDirection.CHARGE);
    }

    /** 经济事实类型。 */
    public EconomicFactRequestBuilder factType(EconomicsType factType) {
        body.setFactType(factType.getCode());
        return this;
    }

    /** 来源引用。 */
    public EconomicFactRequestBuilder sourceReference(String sourceReference) {
        body.setSourceReference(sourceReference);
        return this;
    }

    /** 来源事实。 */
    public EconomicFactRequestBuilder parentFact(long parentFactId) {
        body.setParentFactId(Long.toString(parentFactId));
        return this;
    }

    /** 被替代事实。 */
    public EconomicFactRequestBuilder supersedes(long supersedesFactId) {
        body.setSupersedesFactId(Long.toString(supersedesFactId));
        return this;
    }

    /** 合作方三元组，参数可为空以构造不完整组合。 */
    public EconomicFactRequestBuilder partner(String localPartnerId, String partnerAgreementOeId,
        String financeEntityId) {
        body.setLocalPartnerId(localPartnerId);
        body.setPartnerAgreementOeId(partnerAgreementOeId);
        body.setFinanceEntityId(financeEntityId);
        return this;
    }

    /** 标准合作方三元组。 */
    public EconomicFactRequestBuilder partner() {
        return partner(LOCAL_PARTNER_ID, PARTNER_AGREEMENT_OE_ID, FINANCE_ENTITY_ID);
    }

    /** Client。 */
    public EconomicFactRequestBuilder client(String clientId) {
        body.setClientId(clientId);
        return this;
    }

    /** Operating Entity。 */
    public EconomicFactRequestBuilder entityNo(String entityNo) {
        body.setEntityNo(entityNo);
        return this;
    }

    /** 币种。 */
    public EconomicFactRequestBuilder currency(String currency) {
        body.setCurrency(currency);
        return this;
    }

    /** 渠道清算账户。 */
    public EconomicFactRequestBuilder channelAccount(String channelAccountId) {
        body.setChannelAccountId(channelAccountId);
        return this;
    }

    /** 编制人。 */
    public EconomicFactRequestBuilder maker(String makerId) {
        body.setMakerId(makerId);
        return this;
    }

    /** 清空明细行。 */
    public EconomicFactRequestBuilder clearLines() {
        body.getLines().clear();
        return this;
    }

    /** 追加一行；代码均取自 billing-api 枚举。 */
    public EconomicFactRequestBuilder addLine(AmountSource feeType, FactPartyRole payerRole, String payerId,
        FactPartyRole payeeRole, String payeeId, String amount, FactLineDirection direction) {
        return addRawLine(feeType.getCode(), payerRole.getCode(), payerId, payeeRole.getCode(), payeeId, amount,
            direction.getCode());
    }

    /** 追加一行原始代码，供目录外代码的负例使用。 */
    public EconomicFactRequestBuilder addRawLine(String feeTypeCode, String payerRole, String payerId, String payeeRole,
        String payeeId, String amount, String direction) {
        EconomicFactLineRequestBody line = new EconomicFactLineRequestBody();
        line.setFeeTypeCode(feeTypeCode);
        line.setPayerRole(payerRole);
        line.setPayerId(payerId);
        line.setPayeeRole(payeeRole);
        line.setPayeeId(payeeId);
        line.setAmount(amount);
        line.setDirection(direction);
        body.getLines().add(line);
        return this;
    }

    /** 本次构造的来源引用。 */
    public String sourceReference() {
        return body.getSourceReference();
    }

    /** 构造请求体。 */
    public SubmitEconomicFactRequestBody build() {
        return body;
    }
}
