package com.paytech.billing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import com.paytech.billing.api.fact.EconomicFactLinePayload;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.billing.core.dal.dataobject.EconomicFact;
import com.paytech.billing.core.dal.dataobject.EconomicFactLine;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 经济事实头与明细行到 {@link EconomicFactPayload} 的转换、EconomicFactRecorded 事件 JSON 与提交内容摘要
 * <li>事件 JSON 的字段顺序固定；所有标量写成字符串（ID 与版本为十进制，金额按币种精度补齐，时间为 UTC RFC 3339），缺省为 JSON
 * null，符合 Foundation JSON Object Profile。</li>
 * <li>提交内容摘要 {@value #SUBMISSION_SCHEMA} 覆盖提交的全部业务输入（不含生成的主键与时间），金额去掉末尾零；先写 JSON 再经
 * RFC 8785 规范化后计算 SHA-256 小写十六进制。</li>
 */
public final class EconomicFactPayloads {
    /** 提交内容摘要的输入版本标识，变更输入结构时必须新增版本。 */
    public static final String SUBMISSION_SCHEMA = "CODE-MOR:BILLING:ECONOMIC_FACT_SUBMISSION:1";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private EconomicFactPayloads() {
    }

    /**
     * 组装载荷
     *
     * @param fact 事实头
     * @param lines 全部明细行，按行号升序
     * @param minorUnit 事实币种精度，金额按它补齐
     * @return 载荷；数据库金额超出币种精度时为系统错误
     */
    public static EconomicFactPayload of(EconomicFact fact, List<EconomicFactLine> lines, int minorUnit) {
        EconomicFactPayload payload = new EconomicFactPayload();
        payload.setFactId(fact.getId());
        payload.setFactVersion(fact.getFactVersion());
        payload.setSupersedesFactId(fact.getSupersedesFactId());
        payload.setParentFactId(fact.getParentEconomicsId());
        payload.setFactType(fact.getFactType().getCode());
        payload.setSourceSystem(fact.getSourceSystem());
        payload.setSourceEventKey(fact.getSourceEventKey());
        payload.setSourceVersion(fact.getSourceVersion());
        payload.setSourcePayloadHash(fact.getSourcePayloadHash());
        payload.setRecordedTime(fact.getApprovedTime());
        payload.setPaymentSucceededTime(fact.getPaymentSucceededTime());
        payload.setClientId(fact.getClientId());
        payload.setEntityNo(fact.getEntityNo());
        payload.setLocalPartnerId(fact.getLocalPartnerId());
        payload.setPartnerAgreementOeId(fact.getPartnerAgreementOeId());
        payload.setFinanceEntityId(fact.getFinanceEntityId());
        payload.setCurrency(fact.getCurrency());
        payload.setGrossAmount(scaled(fact.getGrossAmount(), minorUnit));
        payload.setChannelAccountId(fact.getChannelAccountId());
        payload.setLines(lines.stream().map(line -> line(line, minorUnit)).toList());
        return payload;
    }

    /** 明细行载荷。 */
    static EconomicFactLinePayload line(EconomicFactLine line, int minorUnit) {
        EconomicFactLinePayload payload = new EconomicFactLinePayload();
        payload.setLineNo(line.getComponentNo());
        payload.setComponentType(line.getComponentType());
        payload.setPartyType(CodeEnum.defaultCode(line.getPartyType()));
        payload.setPartyId(line.getPartyId());
        payload.setPayerRole(CodeEnum.defaultCode(line.getPayerRole()));
        payload.setPayerId(line.getPayerId());
        payload.setPayeeRole(CodeEnum.defaultCode(line.getPayeeRole()));
        payload.setPayeeId(line.getPayeeId());
        payload.setDirection(CodeEnum.defaultCode(line.getEffectDirection()));
        payload.setPostedAmount(scaled(line.getPostedAmount(), minorUnit));
        return payload;
    }

    /**
     * EconomicFactRecorded Schema v1 Payload：{@link EconomicFactPayload} 的全部字段，固定顺序
     *
     * @param payload 载荷
     * @return JSON Object
     */
    public static ObjectNode json(EconomicFactPayload payload) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("factId", text(payload.getFactId()));
        node.put("factVersion", text(payload.getFactVersion()));
        node.put("supersedesFactId", text(payload.getSupersedesFactId()));
        node.put("parentFactId", text(payload.getParentFactId()));
        node.put("factType", payload.getFactType());
        node.put("sourceSystem", payload.getSourceSystem());
        node.put("sourceEventKey", payload.getSourceEventKey());
        node.put("sourceVersion", text(payload.getSourceVersion()));
        node.put("sourcePayloadHash", payload.getSourcePayloadHash());
        node.put("recordedTime", text(payload.getRecordedTime()));
        node.put("paymentSucceededTime", text(payload.getPaymentSucceededTime()));
        node.put("clientId", text(payload.getClientId()));
        node.put("entityNo", payload.getEntityNo());
        node.put("localPartnerId", text(payload.getLocalPartnerId()));
        node.put("partnerAgreementOeId", text(payload.getPartnerAgreementOeId()));
        node.put("financeEntityId", text(payload.getFinanceEntityId()));
        node.put("currency", payload.getCurrency());
        node.put("grossAmount", text(payload.getGrossAmount()));
        node.put("channelAccountId", text(payload.getChannelAccountId()));
        ArrayNode lines = node.putArray("lines");
        payload.getLines().forEach(line -> lines.add(json(line)));
        return node;
    }

    /** 明细行 JSON。 */
    static ObjectNode json(EconomicFactLinePayload line) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("lineNo", text(line.getLineNo()));
        node.put("componentType", line.getComponentType());
        node.put("partyType", line.getPartyType());
        node.put("partyId", line.getPartyId());
        node.put("payerRole", line.getPayerRole());
        node.put("payerId", line.getPayerId());
        node.put("payeeRole", line.getPayeeRole());
        node.put("payeeId", line.getPayeeId());
        node.put("direction", line.getDirection());
        node.put("postedAmount", text(line.getPostedAmount()));
        return node;
    }

    /**
     * 提交内容摘要：同一来源引用的重复提交以它判断内容是否相同
     *
     * @param fact 尚未落库的 DRAFT 事实头
     * @param lines 尚未落库的明细行，按行号升序
     * @return SHA-256 小写十六进制
     */
    public static String submissionHash(EconomicFact fact, List<EconomicFactLine> lines) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("schema", SUBMISSION_SCHEMA);
        node.put("factType", fact.getFactType().getCode());
        node.put("sourceSystem", fact.getSourceSystem());
        node.put("sourceReference", fact.getSourceEventKey());
        node.put("parentFactId", text(fact.getParentEconomicsId()));
        node.put("supersedesFactId", text(fact.getSupersedesFactId()));
        node.put("clientId", text(fact.getClientId()));
        node.put("entityNo", fact.getEntityNo());
        node.put("localPartnerId", text(fact.getLocalPartnerId()));
        node.put("partnerAgreementOeId", text(fact.getPartnerAgreementOeId()));
        node.put("financeEntityId", text(fact.getFinanceEntityId()));
        node.put("currency", fact.getCurrency());
        node.put("channelAccountId", text(fact.getChannelAccountId()));
        node.put("makerId", text(fact.getMakerId()));
        ArrayNode array = node.putArray("lines");
        for (EconomicFactLine line : lines) {
            ObjectNode item = array.addObject();
            item.put("feeTypeCode", line.getComponentType());
            item.put("payerRole", line.getPayerRole().getCode());
            item.put("payerId", line.getPayerId());
            item.put("payeeRole", line.getPayeeRole().getCode());
            item.put("payeeId", line.getPayeeId());
            item.put("amount", line.getPostedAmount().stripTrailingZeros().toPlainString());
            item.put("direction", line.getEffectDirection().getCode());
        }
        byte[] canonical = Rfc8785JsonCanonicalizer.canonicalizeObject(JSON.writeValueAsBytes(node));
        return ChannelCostContentHash.sha256(canonical);
    }

    /** 金额按币种精度补齐；数据库金额已在登记时校验精度，超出即为数据不变量被破坏。 */
    static BigDecimal scaled(BigDecimal amount, int minorUnit) {
        try {
            return amount.setScale(minorUnit, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exceeded) {
            throw new ErrorException("经济事实金额超出币种精度: " + amount.toPlainString() + ", 精度 " + minorUnit, exceeded);
        }
    }

    /** 十进制或时间文本；为空时为空。 */
    static String text(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        return value == null ? null : value.toString();
    }
}
