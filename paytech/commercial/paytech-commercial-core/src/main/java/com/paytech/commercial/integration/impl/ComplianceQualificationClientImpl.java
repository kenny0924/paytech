package com.paytech.commercial.integration.impl;

import static com.paytech.commercial.api.enums.CheckoutErrorCode.OWNER_UNAVAILABLE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paytech.commercial.api.enums.RelationshipSubjectType;
import com.paytech.commercial.domain.relationship.QualificationSubject;
import com.paytech.commercial.integration.CheckoutQualificationClient;
import com.paytech.compliance.api.QualificationEvaluationApi;
import com.paytech.compliance.api.enums.QualificationEvaluationStatus;
import com.paytech.compliance.api.enums.QualificationRoleType;
import com.paytech.compliance.api.request.QualificationEvaluationRequestBody;
import com.paytech.compliance.api.request.QualificationSubjectBody;
import com.paytech.compliance.api.response.QualificationEvaluationResponseBody;
import com.paytech.compliance.api.response.QualificationEvaluationResultBody;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/**
 * 经 compliance-api 批量判定角色资格（technique.md §3.3.3）
 * <li>失败关闭：任何 4xx / 5xx、超时、无实例或响应不符合合同一律 OWNER_UNAVAILABLE，不降级、不重试。</li>
 * <li>每次请求至多 50 个主体，超出时按顺序分批；一次解析通常一个批次。</li>
 * <li>未知状态 Code 按不合格处理并记录日志；eligible 与状态不一致视为合同不符。</li>
 */
@AllArgsConstructor
public class ComplianceQualificationClientImpl implements CheckoutQualificationClient {
    private static final Logger LOG = LoggerFactory.getLogger(ComplianceQualificationClientImpl.class);

    /** 私网调用方标识。 */
    static final String CALLER = "commercial-service";

    /** compliance 单次请求的主体上限。 */
    static final int BATCH_LIMIT = 50;

    private final QualificationEvaluationApi api;

    /** 去重分批调用并合并结论。 */
    @Override
    public Map<QualificationSubject, Boolean> evaluate(Collection<QualificationSubject> subjects) {
        List<QualificationSubject> distinct = List.copyOf(new LinkedHashSet<>(subjects));
        Map<QualificationSubject, Boolean> result = new LinkedHashMap<>();
        for (int from = 0; from < distinct.size(); from += BATCH_LIMIT) {
            List<QualificationSubject> batch = distinct.subList(from, Math.min(distinct.size(), from + BATCH_LIMIT));
            result.putAll(evaluateBatch(batch));
        }
        return Map.copyOf(result);
    }

    /** 一个批次的远程调用与解析。 */
    private Map<QualificationSubject, Boolean> evaluateBatch(List<QualificationSubject> batch) {
        QualificationEvaluationResponseBody response;
        try {
            response = api.evaluate(CALLER, request(batch));
        } catch (RuntimeException failure) {
            throw new ErrorException(OWNER_UNAVAILABLE, "compliance 资格判定不可用", failure);
        }
        try {
            return parse(response, batch);
        } catch (RuntimeException invalid) {
            throw new ErrorException(OWNER_UNAVAILABLE, "compliance 资格判定响应不符合合同", invalid);
        }
    }

    /** 主体按 compliance 角色类型逐个传递。 */
    private static QualificationEvaluationRequestBody request(List<QualificationSubject> batch) {
        List<QualificationSubjectBody> subjects = new ArrayList<>();
        for (QualificationSubject subject : batch) {
            var body = new QualificationSubjectBody();
            body.setRoleType(roleType(subject.subjectType()).getCode());
            body.setRoleId(subject.subjectId());
            subjects.add(body);
        }
        var request = new QualificationEvaluationRequestBody();
        request.setSubjects(subjects);
        return request;
    }

    /** 快照主体类型到 compliance 角色类型；PLATFORM 不需要资格。 */
    private static QualificationRoleType roleType(RelationshipSubjectType type) {
        return switch (type) {
            case CLIENT -> QualificationRoleType.CLIENT;
            case OPERATING_ENTITY -> QualificationRoleType.OPERATING_ENTITY;
            case LOCAL_PARTNER -> QualificationRoleType.LOCAL_PARTNER;
            case PLATFORM -> throw new IllegalArgumentException("PLATFORM 不需要角色资格");
        };
    }

    /**
     * 响应必须恰好覆盖请求主体，每条 eligible 与状态一致
     *
     * @param response compliance 响应
     * @param batch 本批请求主体
     * @return 每个主体的结论
     */
    private static Map<QualificationSubject, Boolean> parse(QualificationEvaluationResponseBody response,
        List<QualificationSubject> batch) {
        Map<QualificationSubject, Boolean> result = new LinkedHashMap<>();
        for (QualificationEvaluationResultBody item : Objects.requireNonNull(response.getResults())) {
            QualificationSubject subject = subject(item);
            if (!batch.contains(subject) || result.put(subject, eligible(item)) != null) {
                throw new IllegalArgumentException("资格结论与请求主体不一致");
            }
        }
        if (result.size() != batch.size()) {
            throw new IllegalArgumentException("资格结论缺少请求主体");
        }
        return result;
    }

    /** 结论对应的主体。 */
    private static QualificationSubject subject(QualificationEvaluationResultBody item) {
        for (RelationshipSubjectType type : RelationshipSubjectType.values()) {
            if (type != RelationshipSubjectType.PLATFORM && roleType(type).getCode().equals(item.getRoleType())) {
                return new QualificationSubject(type, Objects.requireNonNull(item.getRoleId()));
            }
        }
        throw new IllegalArgumentException("未知角色类型");
    }

    /** 只有 ELIGIBLE 合格；未知状态按不合格处理。 */
    private static boolean eligible(QualificationEvaluationResultBody item) {
        boolean eligible = Objects.requireNonNull(item.getEligible());
        QualificationEvaluationStatus status = QualificationEvaluationStatus.ofCode(item.getStatus());
        if (eligible != (status == QualificationEvaluationStatus.ELIGIBLE)) {
            throw new IllegalArgumentException("eligible 与状态不一致");
        }
        if (status == null) {
            LOG.warn("compliance 返回未知资格状态，按不合格处理 roleType={} roleId={} status={}", item.getRoleType(),
                item.getRoleId(), item.getStatus());
        }
        return eligible;
    }
}
