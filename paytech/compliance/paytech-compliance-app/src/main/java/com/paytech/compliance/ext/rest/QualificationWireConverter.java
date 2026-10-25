package com.paytech.compliance.ext.rest;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import com.paytech.compliance.api.response.QualificationEvaluationResponseBody;
import com.paytech.compliance.api.response.QualificationEvaluationResultBody;
import com.paytech.compliance.api.response.QualificationHistoryItemBody;
import com.paytech.compliance.api.response.QualificationHistoryResponseBody;
import com.paytech.compliance.api.response.QualificationResponseBody;
import com.paytech.compliance.core.enums.QualificationEvaluationStatus;
import com.paytech.compliance.service.processor.response.QualificationEvaluationResponse;
import com.paytech.compliance.service.processor.response.QualificationHistoryResponse;
import com.paytech.compliance.service.processor.response.QualificationResponse;
import com.paytech.foundation.common.error.InvalidParameterException;

/** 角色资格 Owner 结果与 Wire 之间的纯转换；ID 与版本一律十进制字符串，时间为 UTC 六位微秒。 */
final class QualificationWireConverter {
    private QualificationWireConverter() {
    }

    /** 批量判定结果。 */
    static QualificationEvaluationResponseBody evaluation(QualificationEvaluationResponse source) {
        var body = new QualificationEvaluationResponseBody();
        body.setEvaluatedAt(ClientReviewWireConverter.time(source.getEvaluatedAt()));
        body.setResults(source.getResults().stream().map(QualificationWireConverter::result).toList());
        return body;
    }

    /** 一个主体的判定结论；eligible 仅在 ELIGIBLE 时为 true。 */
    static QualificationEvaluationResultBody result(QualificationEvaluationResponse.Result source) {
        var body = new QualificationEvaluationResultBody();
        body.setRoleType(source.getRoleType());
        body.setRoleId(source.getRoleId());
        body.setEligible(QualificationEvaluationStatus.ELIGIBLE.getCode().equals(source.getStatus()));
        body.setStatus(source.getStatus());
        body.setQualificationType(source.getQualificationType());
        body.setQualificationId(source.getQualificationId() == null ? null : Long.toString(source.getQualificationId()));
        body.setValidFrom(ClientReviewWireConverter.time(source.getValidFrom()));
        body.setValidTo(ClientReviewWireConverter.time(source.getValidTo()));
        return body;
    }

    /** 一条资格。 */
    static QualificationResponseBody qualification(QualificationResponse source) {
        var body = new QualificationResponseBody();
        body.setQualificationId(Long.toString(source.getQualificationId()));
        body.setRoleType(source.getRoleType());
        body.setRoleId(source.getRoleId());
        body.setQualificationType(source.getQualificationType());
        body.setStatus(source.getStatus());
        body.setEffectiveStatus(source.getEffectiveStatus());
        body.setValidFrom(ClientReviewWireConverter.time(source.getValidFrom()));
        body.setValidTo(ClientReviewWireConverter.time(source.getValidTo()));
        body.setCurrentDecisionId(Long.toString(source.getCurrentDecisionId()));
        body.setDecisionSource(source.getDecisionSource());
        body.setReviewStatus(source.getReviewStatus());
        body.setVersion(Long.toString(source.getVersion()));
        body.setUpdatedAt(ClientReviewWireConverter.time(source.getUpdatedAt()));
        return body;
    }

    /** 状态变更历史。 */
    static QualificationHistoryResponseBody history(QualificationHistoryResponse source) {
        var body = new QualificationHistoryResponseBody();
        body.setQualificationId(Long.toString(source.getQualificationId()));
        body.setItems(source.getItems().stream().map(QualificationWireConverter::historyItem).toList());
        return body;
    }

    /** 一次状态变更及其决定。 */
    static QualificationHistoryItemBody historyItem(QualificationHistoryResponse.Item source) {
        var body = new QualificationHistoryItemBody();
        body.setChangeId(Long.toString(source.getChangeId()));
        body.setAggregateVersion(Long.toString(source.getAggregateVersion()));
        body.setFromStatus(source.getFromStatus());
        body.setToStatus(source.getToStatus());
        body.setTriggerType(source.getTriggerType());
        body.setReasonCode(source.getReasonCode());
        body.setActorId(source.getActorId() == null ? null : Long.toString(source.getActorId()));
        body.setOccurredAt(ClientReviewWireConverter.time(source.getOccurredAt()));
        body.setDecisionId(Long.toString(source.getDecisionId()));
        body.setDecisionSource(source.getDecisionSource());
        body.setOutcome(source.getOutcome());
        body.setDecidedBy(source.getDecidedBy() == null ? null : Long.toString(source.getDecidedBy()));
        body.setBasisNote(source.getBasisNote());
        body.setValidFrom(ClientReviewWireConverter.time(source.getValidFrom()));
        body.setValidTo(ClientReviewWireConverter.time(source.getValidTo()));
        return body;
    }

    /**
     * 请求中的 UTC ISO-8601 时间；最多六位小数
     *
     * @param value 原始文本，可空
     * @param field 字段名，仅用于诊断
     * @return 时间；输入为空返回 null
     */
    static Instant instant(String value, String field) {
        if (value == null) {
            return null;
        }
        Instant parsed;
        try {
            parsed = Instant.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new InvalidParameterException(field + " 时间格式无效");
        }
        if (parsed.getNano() % 1000 != 0) {
            throw new InvalidParameterException(field + " 最多六位小数");
        }
        return parsed;
    }

    /** 请求中的十进制版本；格式已由 Bean Validation 校验，超出 long 范围按参数错误处理。 */
    static Long version(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException overflow) {
            throw new InvalidParameterException("expectedVersion 超出范围");
        }
    }
}
