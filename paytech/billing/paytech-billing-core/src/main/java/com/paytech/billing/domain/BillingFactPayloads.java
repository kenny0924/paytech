package com.paytech.billing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import com.paytech.billing.api.fact.BillingFactLinePayload;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.billing.api.fact.BillingFactRefPayload;
import com.paytech.billing.core.dal.dataobject.BillingFact;
import com.paytech.billing.core.dal.dataobject.BillingFactLine;
import com.paytech.billing.core.dal.dataobject.BillingFactRef;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 计费事实头、引用与明细行到 {@link BillingFactPayload} 的转换、BillingFactRecorded 事件 JSON 与提交内容摘要
 * <li>事件 JSON 的字段顺序固定；所有标量写成字符串（ID 与版本为十进制，金额按币种精度补齐，时间为 UTC RFC 3339），缺省为 JSON
 * null，符合 Foundation JSON Object Profile。</li>
 * <li>提交内容摘要 {@value #SUBMISSION_SCHEMA} 覆盖提交的全部业务输入（不含生成的主键与时间），引用按类型升序，金额去掉末尾零；先写
 * JSON 再经 RFC 8785 规范化后计算 SHA-256 小写十六进制。</li>
 */
public final class BillingFactPayloads {
    /** 提交内容摘要的输入版本标识，变更输入结构时必须新增版本。 */
    public static final String SUBMISSION_SCHEMA = "CODE-MOR:BILLING:BILLING_FACT_SUBMISSION:1";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 纯静态工具。 */
    private BillingFactPayloads() {
    }

    /**
     * 组装载荷
     *
     * @param fact 事实头
     * @param refs 外部引用，按引用类型升序
     * @param lines 全部明细行，按行号升序
     * @param minorUnit 事实币种精度，金额按它补齐
     * @return 载荷；数据库金额超出币种精度时为系统错误
     */
    public static BillingFactPayload of(BillingFact fact, List<BillingFactRef> refs, List<BillingFactLine> lines,
        int minorUnit) {
        BillingFactPayload payload = new BillingFactPayload();
        payload.setFactId(fact.getId());
        payload.setFactVersion(fact.getFactVersion());
        payload.setSupersedesFactId(fact.getSupersedesFactId());
        payload.setParentFactId(fact.getParentFactId());
        payload.setFactType(fact.getFactType().getCode());
        payload.setOriginType(fact.getOriginType().getCode());
        payload.setStatus(fact.getStatus().getCode());
        payload.setSourceSystem(fact.getSourceSystem());
        payload.setSourceEventKey(fact.getSourceEventKey());
        payload.setSourceVersion(fact.getSourceVersion());
        payload.setSourcePayloadHash(fact.getSourcePayloadHash());
        payload.setBusinessTime(fact.getBusinessTime());
        payload.setRecordedTime(fact.getApprovedTime());
        payload.setBalanceAvailableTime(fact.getBalanceAvailableTime());
        payload.setClientId(fact.getClientId());
        payload.setEntityNo(fact.getEntityNo());
        payload.setLocalPartnerId(fact.getLocalPartnerId());
        payload.setFinanceEntityId(fact.getFinanceEntityId());
        payload.setCurrency(fact.getCurrency());
        payload.setGrossAmount(scaled(fact.getGrossAmount(), minorUnit));
        payload.setRefs(refs.stream().map(BillingFactPayloads::ref).toList());
        payload.setLines(lines.stream().map(line -> line(line, minorUnit)).toList());
        return payload;
    }

    /** 引用载荷。 */
    static BillingFactRefPayload ref(BillingFactRef ref) {
        BillingFactRefPayload payload = new BillingFactRefPayload();
        payload.setRefType(ref.getRefType().getCode());
        payload.setRefId(ref.getRefId());
        payload.setExternalReferenceType(ref.getExternalReferenceType());
        payload.setExternalTransactionId(ref.getExternalTransactionId());
        return payload;
    }

    /** 明细行载荷。 */
    static BillingFactLinePayload line(BillingFactLine line, int minorUnit) {
        BillingFactLinePayload payload = new BillingFactLinePayload();
        payload.setLineNo(line.getLineNo());
        payload.setComponentType(line.getComponentType().getCode());
        payload.setPayerRole(line.getPayerRole().getCode());
        payload.setPayerId(line.getPayerId());
        payload.setPayeeRole(line.getPayeeRole().getCode());
        payload.setPayeeId(line.getPayeeId());
        payload.setDirection(line.getEffectDirection().getCode());
        payload.setPostedAmount(scaled(line.getPostedAmount(), minorUnit));
        payload.setTargetBucket(CodeEnum.defaultCode(line.getTargetBucket()));
        return payload;
    }

    /**
     * BillingFactRecorded Schema v1 Payload：{@link BillingFactPayload} 的全部字段，固定顺序
     *
     * @param payload 载荷
     * @return JSON Object
     */
    public static ObjectNode json(BillingFactPayload payload) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("factId", text(payload.getFactId()));
        node.put("factVersion", text(payload.getFactVersion()));
        node.put("supersedesFactId", text(payload.getSupersedesFactId()));
        node.put("parentFactId", text(payload.getParentFactId()));
        node.put("factType", payload.getFactType());
        node.put("originType", payload.getOriginType());
        node.put("status", payload.getStatus());
        node.put("sourceSystem", payload.getSourceSystem());
        node.put("sourceEventKey", payload.getSourceEventKey());
        node.put("sourceVersion", text(payload.getSourceVersion()));
        node.put("sourcePayloadHash", payload.getSourcePayloadHash());
        node.put("businessTime", text(payload.getBusinessTime()));
        node.put("recordedTime", text(payload.getRecordedTime()));
        node.put("balanceAvailableTime", text(payload.getBalanceAvailableTime()));
        node.put("clientId", text(payload.getClientId()));
        node.put("entityNo", payload.getEntityNo());
        node.put("localPartnerId", text(payload.getLocalPartnerId()));
        node.put("financeEntityId", text(payload.getFinanceEntityId()));
        node.put("currency", payload.getCurrency());
        node.put("grossAmount", text(payload.getGrossAmount()));
        ArrayNode refs = node.putArray("refs");
        payload.getRefs().forEach(ref -> refs.addObject().put("refType", ref.getRefType()).put("refId", ref.getRefId())
            .put("externalReferenceType", ref.getExternalReferenceType())
            .put("externalTransactionId", ref.getExternalTransactionId()));
        ArrayNode lines = node.putArray("lines");
        payload.getLines().forEach(line -> lines.add(json(line)));
        return node;
    }

    /** 明细行 JSON。 */
    static ObjectNode json(BillingFactLinePayload line) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("lineNo", text(line.getLineNo()));
        node.put("componentType", line.getComponentType());
        node.put("payerRole", line.getPayerRole());
        node.put("payerId", line.getPayerId());
        node.put("payeeRole", line.getPayeeRole());
        node.put("payeeId", line.getPayeeId());
        node.put("direction", line.getDirection());
        node.put("postedAmount", text(line.getPostedAmount()));
        node.put("targetBucket", line.getTargetBucket());
        return node;
    }

    /**
     * 提交内容摘要：同一来源引用的重复提交以它判断内容是否相同
     *
     * @param fact 尚未落库、按提交原样填写的 DRAFT 事实头（成本调整的 Client 尚未取自来源事实）
     * @param refs 尚未落库的引用，按引用类型升序
     * @param lines 尚未落库的明细行，按行号升序
     * @return SHA-256 小写十六进制
     */
    public static String submissionHash(BillingFact fact, List<BillingFactRef> refs, List<BillingFactLine> lines) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("schema", SUBMISSION_SCHEMA);
        node.put("factType", fact.getFactType().getCode());
        node.put("sourceSystem", fact.getSourceSystem());
        node.put("sourceReference", fact.getSourceEventKey());
        node.put("parentFactId", text(fact.getParentFactId()));
        node.put("supersedesFactId", text(fact.getSupersedesFactId()));
        node.put("clientId", text(fact.getClientId()));
        node.put("entityNo", fact.getEntityNo());
        node.put("localPartnerId", text(fact.getLocalPartnerId()));
        node.put("financeEntityId", text(fact.getFinanceEntityId()));
        node.put("currency", fact.getCurrency());
        node.put("makerId", text(fact.getMakerId()));
        ArrayNode refArray = node.putArray("refs");
        refs.forEach(ref -> refItem(refArray.addObject(), ref));
        ArrayNode lineArray = node.putArray("lines");
        for (BillingFactLine line : lines) {
            ObjectNode item = lineArray.addObject();
            item.put("componentType", line.getComponentType().getCode());
            item.put("payerRole", line.getPayerRole().getCode());
            item.put("payerId", line.getPayerId());
            item.put("payeeRole", line.getPayeeRole().getCode());
            item.put("payeeId", line.getPayeeId());
            item.put("amount", line.getPostedAmount().stripTrailingZeros().toPlainString());
            item.put("direction", line.getEffectDirection().getCode());
            item.put("targetBucket", CodeEnum.defaultCode(line.getTargetBucket()));
        }
        return sha256(Rfc8785JsonCanonicalizer.canonicalizeObject(JSON.writeValueAsBytes(node)));
    }

    /** 摘要中的一条引用；渠道外部交易号两项只在非空时写入，未携带它们的既有提交摘要保持不变。 */
    static void refItem(ObjectNode item, BillingFactRef ref) {
        item.put("refType", ref.getRefType().getCode()).put("refId", ref.getRefId());
        if (ref.getExternalReferenceType() != null) {
            item.put("externalReferenceType", ref.getExternalReferenceType());
        }
        if (ref.getExternalTransactionId() != null) {
            item.put("externalTransactionId", ref.getExternalTransactionId());
        }
    }

    /** 金额按币种精度补齐；数据库金额已在登记时校验精度，超出即为数据不变量被破坏。 */
    public static BigDecimal scaled(BigDecimal amount, int minorUnit) {
        try {
            return amount.setScale(minorUnit, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exceeded) {
            throw new ErrorException("计费事实金额超出币种精度: " + amount.toPlainString() + ", 精度 " + minorUnit, exceeded);
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

    /** SHA-256 小写十六进制。 */
    static String sha256(byte[] canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException missing) {
            throw new ErrorException("运行环境缺少 SHA-256", missing);
        }
    }
}
