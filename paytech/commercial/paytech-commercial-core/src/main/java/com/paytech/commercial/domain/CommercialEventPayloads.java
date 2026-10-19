package com.paytech.commercial.domain;

import java.time.Instant;

import com.paytech.commercial.api.event.AgreementStatusChangedPayload;
import com.paytech.commercial.api.event.CommercialTermVersionActivatedPayload;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.foundation.common.enums.CodeEnum;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 本库行到两个事件载荷的转换与事件 JSON
 * <li>事件 JSON 字段顺序固定为载荷类字段顺序；所有标量写成字符串（ID 与版本为十进制，时间为 UTC RFC 3339），缺省为 JSON null，
 * 符合 Foundation JSON Object Profile。</li>
 */
public final class CommercialEventPayloads {
    /** 纯静态工具。 */
    private CommercialEventPayloads() {
    }

    /**
     * 合同状态变更载荷；occurredAt 取合同行更新时间（该版本的状态变更提交时间）
     *
     * @param agreement 合同行
     * @param changeVersion AGREEMENT 根修订版本
     * @return 载荷
     */
    public static AgreementStatusChangedPayload agreementStatusChanged(Agreement agreement, long changeVersion) {
        AgreementStatusChangedPayload payload = new AgreementStatusChangedPayload();
        payload.setAgreementId(agreement.getAgreementId());
        payload.setAgreementNo(agreement.getAgreementNo());
        payload.setAgreementType(CodeEnum.defaultCode(agreement.getAgreementType()));
        payload.setClientId(agreement.getClientId());
        payload.setLocalPartnerId(agreement.getLocalPartnerId());
        payload.setEntityNo(agreement.getEntityNo());
        payload.setStatus(CodeEnum.defaultCode(agreement.getStatus()));
        payload.setEffectiveFrom(agreement.getEffectiveFrom());
        payload.setEffectiveTo(agreement.getEffectiveTo());
        payload.setChangeVersion(changeVersion);
        payload.setOccurredAt(agreement.getUpdateTime());
        return payload;
    }

    /**
     * 商业条款版本生效载荷；occurredAt 取新生效版本行更新时间（激活提交时间）
     *
     * @param plan 方案行
     * @param activated 新 ACTIVE 版本
     * @param superseded 被替代版本，可空
     * @param changeVersion COMMERCIAL_TERM_PLAN 根修订版本
     * @return 载荷
     */
    public static CommercialTermVersionActivatedPayload termVersionActivated(CommercialTermPlan plan,
        CommercialTermVersion activated, CommercialTermVersion superseded, long changeVersion) {
        CommercialTermVersionActivatedPayload payload = new CommercialTermVersionActivatedPayload();
        payload.setPlanId(plan.getTermPlanId());
        payload.setTermType(plan.getTermType());
        payload.setAgreementId(plan.getAgreementId());
        payload.setVersionId(activated.getTermVersionId());
        payload.setVersionNo(activated.getVersionNo());
        payload.setEffectiveFrom(activated.getEffectiveFrom());
        payload.setSupersededVersionId(superseded == null ? null : superseded.getTermVersionId());
        payload.setChangeVersion(changeVersion);
        payload.setOccurredAt(activated.getUpdateTime());
        return payload;
    }

    /**
     * AgreementStatusChanged Schema v1 Payload
     *
     * @param payload 载荷
     * @return JSON Object
     */
    public static ObjectNode json(AgreementStatusChangedPayload payload) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("agreementId", text(payload.getAgreementId()));
        node.put("agreementNo", payload.getAgreementNo());
        node.put("agreementType", payload.getAgreementType());
        node.put("clientId", text(payload.getClientId()));
        node.put("localPartnerId", text(payload.getLocalPartnerId()));
        node.put("entityNo", payload.getEntityNo());
        node.put("status", payload.getStatus());
        node.put("effectiveFrom", text(payload.getEffectiveFrom()));
        node.put("effectiveTo", text(payload.getEffectiveTo()));
        node.put("changeVersion", text(payload.getChangeVersion()));
        node.put("occurredAt", text(payload.getOccurredAt()));
        return node;
    }

    /**
     * CommercialTermVersionActivated Schema v1 Payload
     *
     * @param payload 载荷
     * @return JSON Object
     */
    public static ObjectNode json(CommercialTermVersionActivatedPayload payload) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("planId", text(payload.getPlanId()));
        node.put("termType", payload.getTermType());
        node.put("agreementId", text(payload.getAgreementId()));
        node.put("versionId", text(payload.getVersionId()));
        node.put("versionNo", text(payload.getVersionNo()));
        node.put("effectiveFrom", text(payload.getEffectiveFrom()));
        node.put("supersededVersionId", text(payload.getSupersededVersionId()));
        node.put("changeVersion", text(payload.getChangeVersion()));
        node.put("occurredAt", text(payload.getOccurredAt()));
        return node;
    }

    /** 十进制或时间文本；为空时为空。 */
    static String text(Object value) {
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        return value == null ? null : value.toString();
    }
}
