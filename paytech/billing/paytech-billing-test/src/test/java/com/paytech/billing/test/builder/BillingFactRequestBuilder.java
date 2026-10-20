package com.paytech.billing.test.builder;

import java.util.ArrayList;
import java.util.UUID;

import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.api.request.BillingFactLineRequestBody;
import com.paytech.billing.api.request.BillingFactRefRequestBody;
import com.paytech.billing.api.request.SubmitBillingFactRequestBody;
import com.paytech.billing.core.enums.FactTargetBucket;

/**
 * 计费事实提交请求构建器
 * <li>template() 给出合法的人工调整：OE 向 Client 付 CLIENT_PRINCIPAL 10.00 USD，带一条 TICKET 引用，来源引用每次随机。</li>
 * <li>costAdjustment(parent) 给出合法的成本调整：OE 向渠道多付 0.40 USD CHANNEL_COST + ADJUSTMENT，引用 CLEARING_ACCOUNT。</li>
 * <li>编制人不在请求体中：由调用方以 makerId() 填入 X-Gateway-Actor-Subject；缺省为 MAKER_ID，maker(null) 表示不传该 Header。</li>
 * <li>负例按需覆盖字段，build() 不修复非法输入。</li>
 */
public final class BillingFactRequestBuilder {
    /** 标准来源系统。 */
    public static final String SOURCE_SYSTEM = "OPERATIONS";

    /** 标准 Client。 */
    public static final String CLIENT_ID = "300001";

    /** 另一个 Client。 */
    public static final String OTHER_CLIENT_ID = "300002";

    /** 标准 Operating Entity。 */
    public static final String ENTITY_NO = "AE001";

    /** 另一个 Operating Entity。 */
    public static final String OTHER_ENTITY_NO = "SA001";

    /** 标准币种（初始化 DML 中 ACTIVE，精度 2）。 */
    public static final String CURRENCY = "USD";

    /** 编制人。 */
    public static final String MAKER_ID = "81001";

    /** 合作方。 */
    public static final String LOCAL_PARTNER_ID = "400001";

    /** 平台 Finance Entity。 */
    public static final String FINANCE_ENTITY_ID = "520001";

    /** Finance 渠道清算账户。 */
    public static final String CLEARING_ACCOUNT_ID = "610001";

    /** 渠道。 */
    public static final String CHANNEL_ID = "710001";

    private final SubmitBillingFactRequestBody body = new SubmitBillingFactRequestBody();

    /** 编制人 Header 值；为空时不传。 */
    private String makerId = MAKER_ID;

    /** 只能从模板开始。 */
    private BillingFactRequestBuilder() {
    }

    /** 合法的单行人工调整模板（带 TICKET 引用）。 */
    public static BillingFactRequestBuilder template() {
        BillingFactRequestBuilder builder = new BillingFactRequestBuilder();
        builder.body.setFactType(BillingFactType.MANUAL_ADJUSTMENT.getCode());
        builder.body.setSourceSystem(SOURCE_SYSTEM);
        builder.body.setSourceReference("ADJ-" + UUID.randomUUID().toString().substring(0, 8));
        builder.body.setClientId(CLIENT_ID);
        builder.body.setEntityNo(ENTITY_NO);
        builder.body.setCurrency(CURRENCY);
        builder.body.setRefs(new ArrayList<>());
        builder.body.setLines(new ArrayList<>());
        return builder.ref(FactRefType.TICKET, "OPS-" + builder.body.getSourceReference())
            .addLine(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT,
                CLIENT_ID, "10.00", FactLineDirection.CHARGE);
    }

    /** 合法的成本调整模板：Client 取自来源支付事实，不在请求中给出。 */
    public static BillingFactRequestBuilder costAdjustment(long parentFactId) {
        return template().factType(BillingFactType.COST_ADJUSTMENT).parentFact(parentFactId).client(null).clearRefs()
            .ref(FactRefType.CLEARING_ACCOUNT, CLEARING_ACCOUNT_ID).clearLines()
            .addLine(FactComponentType.CHANNEL_COST, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CHANNEL,
                CHANNEL_ID, "0.40", FactLineDirection.ADJUSTMENT);
    }

    /** 计费事实类型。 */
    public BillingFactRequestBuilder factType(BillingFactType factType) {
        body.setFactType(factType.getCode());
        return this;
    }

    /** 来源引用。 */
    public BillingFactRequestBuilder sourceReference(String sourceReference) {
        body.setSourceReference(sourceReference);
        return this;
    }

    /** 来源事实。 */
    public BillingFactRequestBuilder parentFact(long parentFactId) {
        body.setParentFactId(Long.toString(parentFactId));
        return this;
    }

    /** 被替代事实。 */
    public BillingFactRequestBuilder supersedes(long supersedesFactId) {
        body.setSupersedesFactId(Long.toString(supersedesFactId));
        return this;
    }

    /** 合作方二元组，参数可为空以构造不完整组合。 */
    public BillingFactRequestBuilder partner(String localPartnerId, String financeEntityId) {
        body.setLocalPartnerId(localPartnerId);
        body.setFinanceEntityId(financeEntityId);
        return this;
    }

    /** 标准合作方二元组。 */
    public BillingFactRequestBuilder partner() {
        return partner(LOCAL_PARTNER_ID, FINANCE_ENTITY_ID);
    }

    /** Client；可为空。 */
    public BillingFactRequestBuilder client(String clientId) {
        body.setClientId(clientId);
        return this;
    }

    /** Operating Entity。 */
    public BillingFactRequestBuilder entityNo(String entityNo) {
        body.setEntityNo(entityNo);
        return this;
    }

    /** 币种。 */
    public BillingFactRequestBuilder currency(String currency) {
        body.setCurrency(currency);
        return this;
    }

    /** 编制人（X-Gateway-Actor-Subject）；为空表示不传。 */
    public BillingFactRequestBuilder maker(String makerId) {
        this.makerId = makerId;
        return this;
    }

    /** 清空引用。 */
    public BillingFactRequestBuilder clearRefs() {
        body.getRefs().clear();
        return this;
    }

    /** 追加一条引用。 */
    public BillingFactRequestBuilder ref(FactRefType refType, String refId) {
        return rawRef(refType.getCode(), refId);
    }

    /** 追加一条原始代码引用，供目录外代码的负例使用。 */
    public BillingFactRequestBuilder rawRef(String refType, String refId) {
        BillingFactRefRequestBody ref = new BillingFactRefRequestBody();
        ref.setRefType(refType);
        ref.setRefId(refId);
        body.getRefs().add(ref);
        return this;
    }

    /** 清空明细行。 */
    public BillingFactRequestBuilder clearLines() {
        body.getLines().clear();
        return this;
    }

    /** 追加一行（无目标余额桶）；代码均取自枚举。 */
    public BillingFactRequestBuilder addLine(FactComponentType componentType, FactPartyRole payerRole, String payerId,
        FactPartyRole payeeRole, String payeeId, String amount, FactLineDirection direction) {
        return addRawLine(componentType.getCode(), payerRole.getCode(), payerId, payeeRole.getCode(), payeeId, amount,
            direction.getCode(), null);
    }

    /** 追加一行并指定目标余额桶。 */
    public BillingFactRequestBuilder addLine(FactComponentType componentType, FactPartyRole payerRole, String payerId,
        FactPartyRole payeeRole, String payeeId, String amount, FactLineDirection direction, FactTargetBucket bucket) {
        return addRawLine(componentType.getCode(), payerRole.getCode(), payerId, payeeRole.getCode(), payeeId, amount,
            direction.getCode(), bucket.getCode());
    }

    /** 追加一行原始代码，供目录外代码的负例使用。 */
    public BillingFactRequestBuilder addRawLine(String componentType, String payerRole, String payerId, String payeeRole,
        String payeeId, String amount, String direction, String targetBucket) {
        BillingFactLineRequestBody line = new BillingFactLineRequestBody();
        line.setComponentType(componentType);
        line.setPayerRole(payerRole);
        line.setPayerId(payerId);
        line.setPayeeRole(payeeRole);
        line.setPayeeId(payeeId);
        line.setAmount(amount);
        line.setDirection(direction);
        line.setTargetBucket(targetBucket);
        body.getLines().add(line);
        return this;
    }

    /** 编制人 Header 值；为空表示不传。 */
    public String makerId() {
        return makerId;
    }

    /** 本次构造的来源引用。 */
    public String sourceReference() {
        return body.getSourceReference();
    }

    /** 构造请求体。 */
    public SubmitBillingFactRequestBody build() {
        return body;
    }
}
