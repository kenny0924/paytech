package com.paytech.commercial.test.mocker.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;
import com.paytech.compliance.api.enums.QualificationEvaluationStatus;
import com.paytech.compliance.api.enums.QualificationRoleType;
import com.paytech.compliance.api.request.QualificationEvaluationRequestBody;
import com.paytech.compliance.api.request.QualificationSubjectBody;
import com.paytech.compliance.api.response.QualificationEvaluationResponseBody;
import com.paytech.compliance.api.response.QualificationEvaluationResultBody;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;

/**
 * compliance 资格判定接口的 DTO 配置与调用记录。
 * <li>按（角色类型, 角色标识）配置每个主体的实时状态；请求中出现未配置的主体记为意外调用并使请求失败。</li>
 * <li>也可配置整次请求的 HTTP 故障或断连；每次请求记录主体键，供用例断言一次批量往返。</li>
 */
@TestComponent
public class QualificationEvaluationMocker implements Clear {
    /** 一次 HTTP 故障或断连。 */
    public record Fault(int status, ApiProblemResponse problem, boolean disconnect) {
    }

    /** Mock 响应与 Problem 的固定 Request ID。 */
    public static final String REQUEST_ID = "commercial-compliance-test";

    private final Map<String, QualificationEvaluationStatus> statuses = new LinkedHashMap<>();
    private Fault fault;
    private final List<List<String>> calls = new CopyOnWriteArrayList<>();

    /** 夹具全部主体（Client、AE991 / AE992 及其提供方）合格。 */
    public synchronized void mockAllEligible() {
        mockStatus(QualificationRoleType.CLIENT, Long.toString(CheckoutFixture.CLIENT_ID), QualificationEvaluationStatus.ELIGIBLE);
        mockStatus(QualificationRoleType.OPERATING_ENTITY, "AE991", QualificationEvaluationStatus.ELIGIBLE);
        mockStatus(QualificationRoleType.OPERATING_ENTITY, "AE992", QualificationEvaluationStatus.ELIGIBLE);
        mockStatus(QualificationRoleType.LOCAL_PARTNER, Long.toString(CheckoutFixture.FIRST_PARTNER_ID),
            QualificationEvaluationStatus.ELIGIBLE);
        mockStatus(QualificationRoleType.LOCAL_PARTNER, Long.toString(CheckoutFixture.SECOND_PARTNER_ID),
            QualificationEvaluationStatus.ELIGIBLE);
    }

    /** 指定一个主体的实时状态，覆盖已有配置。 */
    public synchronized void mockStatus(QualificationRoleType roleType, String roleId, QualificationEvaluationStatus status) {
        statuses.put(key(roleType.getCode(), roleId), status);
    }

    /** compliance 返回第一方 Problem。 */
    public synchronized void mockProblem(int status, String code) {
        fault = new Fault(status, new ApiProblemResponse(ApiProblemProfile.TYPE, HttpStatus.valueOf(status).getReasonPhrase(),
            "测试依赖拒绝", code, REQUEST_ID, "2026-10-24T00:00:00.000000Z", null), false);
    }

    /** compliance 断连。 */
    public synchronized void mockUnavailable() {
        fault = new Fault(0, null, true);
    }

    /**
     * 由 Mock Controller 调用：记录主体并返回配置的故障、DTO 或 null（存在未配置主体）
     *
     * @param request 实际请求
     * @return Fault、响应 DTO 或 null
     */
    public synchronized Object evaluate(QualificationEvaluationRequestBody request) {
        List<String> keys = request.getSubjects().stream().map(s -> key(s.getRoleType(), s.getRoleId())).toList();
        calls.add(keys);
        if (fault != null) {
            return fault;
        }
        List<QualificationEvaluationResultBody> results = new ArrayList<>();
        for (QualificationSubjectBody subject : request.getSubjects()) {
            QualificationEvaluationStatus status = statuses.get(key(subject.getRoleType(), subject.getRoleId()));
            if (status == null) {
                return null;
            }
            results.add(result(subject, status));
        }
        var response = new QualificationEvaluationResponseBody();
        response.setEvaluatedAt("2026-09-11T12:00:00.000000Z");
        response.setResults(results);
        return response;
    }

    /** 判定请求次数。 */
    public int count() {
        return calls.size();
    }

    /** 唯一一次请求的主体键（{@code ROLE_TYPE:roleId}），按请求顺序。 */
    public List<String> onlyRequestSubjects() {
        assertEquals(1, calls.size(), "应只发起一次资格判定");
        return calls.getFirst();
    }

    /** 主体键。 */
    public static String key(String roleType, String roleId) {
        return roleType + ":" + roleId;
    }

    /** 一条结论；非 NOT_REGISTERED 时带资格类型与有效期。 */
    private static QualificationEvaluationResultBody result(QualificationSubjectBody subject,
        QualificationEvaluationStatus status) {
        var result = new QualificationEvaluationResultBody();
        result.setRoleType(subject.getRoleType());
        result.setRoleId(subject.getRoleId());
        result.setStatus(status.getCode());
        result.setEligible(status == QualificationEvaluationStatus.ELIGIBLE);
        if (status != QualificationEvaluationStatus.NOT_REGISTERED) {
            result.setQualificationId("910000000000990");
            result.setValidFrom("2026-09-01T00:00:00.000000Z");
        }
        return result;
    }

    /** 每例清空配置与记录。 */
    @Override
    public synchronized void clear() {
        statuses.clear();
        fault = null;
        calls.clear();
    }
}
