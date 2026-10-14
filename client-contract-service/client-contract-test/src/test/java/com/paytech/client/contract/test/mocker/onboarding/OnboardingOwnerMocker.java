package com.paytech.client.contract.test.mocker.onboarding;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.api.authentication.response.AccountContextResponseBody;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileModels.VerificationResponseBody;
import com.paytech.risk.api.compliance.MaterialRequirementsModels.RequirementBody;
import com.paytech.risk.api.compliance.MaterialRequirementsModels.RequirementsResponseBody;
import com.paytech.platform.support.api.tenantprojection.request.StatusRequestBody;
import com.paytech.platform.support.api.tenantprojection.response.ProjectionResponseBody;
import com.paytech.risk.api.compliance.request.ClientReviewClassificationsRequestBody;
import com.paytech.risk.api.compliance.request.ClientReviewRegistrationRequestBody;
import com.paytech.risk.api.compliance.response.ClientReviewCommandResponseBody;
import com.paytech.risk.api.compliance.response.ClientReviewRegistrationResponseBody;

/**
 * Platform 与 Risk 的外部 DTO 配置及调用记录；未配置的请求返回 null，由 Mock 控制器转成意外调用。
 * <li>只保存稳定标识与次数，不保存证件号等敏感原文以外的完整请求图；I01 请求体按测试需要保留。</li>
 */
@TestComponent
public class OnboardingOwnerMocker implements Clear {
    /** 一次 HTTP 故障或断连。 */
    public record Fault(int status, ApiProblemResponse problem, boolean disconnect) {
    }

    private final Map<String, String> sessions = new HashMap<>();
    private final Map<String, Object> requirements = new HashMap<>();
    private final Map<String, Object> files = new HashMap<>();
    private Object review;
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final List<ClientReviewRegistrationRequestBody> reviewRequests = new CopyOnWriteArrayList<>();
    private Object classificationReply;
    private final List<ClientReviewClassificationsRequestBody> classificationRequests = new CopyOnWriteArrayList<>();
    private final Map<String, ProjectionResponseBody> projections = new HashMap<>();
    private final List<StatusRequestBody> projectionRequests = new CopyOnWriteArrayList<>();

    /** 当前账号会话有效。 */
    public synchronized void mockSession(long accountId, long sessionId) {
        sessions.put(Long.toString(accountId), Long.toString(sessionId));
    }

    /** 某资格类型的通用材料要求。 */
    public synchronized void mockRequirements(String qualificationType, RequirementBody... items) {
        requirements.put(qualificationType, new RequirementsResponseBody("900", "901", List.of(items)));
    }

    /** 某资格类型的材料查询断连。 */
    public synchronized void mockRequirementsUnavailable(String qualificationType) {
        requirements.put(qualificationType, new Fault(0, null, true));
    }

    /** 文件当前可用，内容摘要固定。 */
    public synchronized void mockFile(long fileId, String contentHash, String scanStatus) {
        files.put(Long.toString(fileId), new VerificationResponseBody(Long.toString(fileId), "77" + fileId, contentHash, "1024",
            "application/pdf", scanStatus, "ACTIVE", null));
    }

    /** Risk 登记成功并返回主审核任务。 */
    public synchronized void mockReviewRegistered(long caseId) {
        review = caseId;
    }

    /** Risk 明确拒绝登记。 */
    public synchronized void mockReviewRejected(int status, String code) {
        review = new Fault(status, problem(status, code), false);
    }

    /** Risk 登记时断连。 */
    public synchronized void mockReviewUnavailable() {
        review = new Fault(0, null, true);
    }

    /** Risk I02 接收分类成功。 */
    public synchronized void mockClassificationsReceived() {
        classificationReply = Boolean.TRUE;
    }

    /** Risk I02 明确拒绝。 */
    public synchronized void mockClassificationsRejected(int status, String code) {
        classificationReply = new Fault(status, problem(status, code), false);
    }

    /** Platform 已有某 Client 的工作区投影位置（来源第一版）。 */
    public synchronized void mockTenantProjection(long clientId, long applicantId, String payloadHash) {
        projections.put(Long.toString(clientId), new ProjectionResponseBody("CLIENT", Long.toString(clientId), Long.toString(applicantId),
            "880" + clientId % 1_000_000, "881" + clientId % 1_000_000, "PENDING", 1, payloadHash));
    }

    /** I02；返回 DTO、Fault 或 null。 */
    public synchronized Object classifications(String caseId, ClientReviewClassificationsRequestBody request) {
        calls.add("classifications:" + caseId);
        classificationRequests.add(request);
        if (Boolean.TRUE.equals(classificationReply)) {
            var body = new ClientReviewCommandResponseBody();
            body.setCaseId(caseId);
            body.setStatus("SUBMITTED");
            body.setAccepted(true);
            body.setProcessing(false);
            return body;
        }
        return classificationReply;
    }

    /** PLT-I17 查询当前位置；未配置返回 null。 */
    public synchronized ProjectionResponseBody projection(String ownerRefId) {
        calls.add("projection:" + ownerRefId);
        return projections.get(ownerRefId);
    }

    /** PLT-I17 状态投影：按请求推进位置并返回；未配置 Client 返回 null。 */
    public synchronized ProjectionResponseBody projectStatus(StatusRequestBody request) {
        calls.add("projectStatus:" + request.getOwnerRefId());
        projectionRequests.add(request);
        var current = projections.get(request.getOwnerRefId());
        if (current == null) {
            return null;
        }
        var next = new ProjectionResponseBody("CLIENT", current.getOwnerRefId(), current.getRequestedByAccountId(), current.getTenantId(),
            current.getMemberId(), request.getOwnerStatus(), request.getSourceVersion(), request.getPayloadHash());
        projections.put(request.getOwnerRefId(), next);
        return next;
    }

    /** I02 请求快照。 */
    public List<ClientReviewClassificationsRequestBody> classificationRequests() {
        return new ArrayList<>(classificationRequests);
    }

    /** PLT-I17 状态投影请求快照。 */
    public List<StatusRequestBody> projectionRequests() {
        return new ArrayList<>(projectionRequests);
    }

    /** 会话查询。 */
    public synchronized AccountContextResponseBody context(String accountId, String sessionId) {
        calls.add("context");
        if (!sessionId.equals(sessions.get(accountId))) {
            return null;
        }
        return new AccountContextResponseBody(accountId, sessionId, "f***@example.test", true, "ACCOUNT", null, null, List.of());
    }

    /** 材料要求查询；返回 DTO、Fault 或 null。 */
    public synchronized Object requirements(String qualificationType) {
        calls.add("requirements:" + qualificationType);
        return requirements.get(qualificationType);
    }

    /** 文件核验；checkedAt 使用当前时间以满足 CCT 新鲜度检查。 */
    public synchronized VerificationResponseBody file(String fileId) {
        calls.add("file:" + fileId);
        var proof = (VerificationResponseBody)files.get(fileId);
        return proof == null ? null : new VerificationResponseBody(proof.fileId(), proof.uploadId(), proof.contentHash(),
            proof.sizeBytes(), proof.mimeType(), proof.scanStatus(), proof.objectStatus(), Instant.now());
    }

    /** I01 审核登记；返回 DTO、Fault 或 null。 */
    public synchronized Object review(ClientReviewRegistrationRequestBody request) {
        calls.add("review:" + request.getSourceSubmissionId());
        reviewRequests.add(request);
        if (review instanceof Long caseId) {
            var body = new ClientReviewRegistrationResponseBody();
            body.setSourceSubmissionId(request.getSourceSubmissionId());
            body.setCaseId(Long.toString(caseId));
            return body;
        }
        return review;
    }

    /** 调用次数。 */
    public long count(String prefix) {
        return calls.stream().filter(call -> call.startsWith(prefix)).count();
    }

    /** I01 请求快照。 */
    public List<ClientReviewRegistrationRequestBody> reviewRequests() {
        return new ArrayList<>(reviewRequests);
    }

    /** 构造项目 Problem。 */
    static ApiProblemResponse problem(int status, String code) {
        return new ApiProblemResponse(ApiProblemProfile.TYPE, HttpStatus.valueOf(status).getReasonPhrase(), "测试依赖拒绝", code,
            "cct-onboarding-test", "2026-10-13T00:00:00.000000Z", null);
    }

    /** 每例清空配置与记录。 */
    @Override
    public synchronized void clear() {
        sessions.clear();
        requirements.clear();
        files.clear();
        review = null;
        calls.clear();
        reviewRequests.clear();
        classificationReply = null;
        classificationRequests.clear();
        projections.clear();
        projectionRequests.clear();
    }
}
