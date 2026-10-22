package com.paytech.client.master.test.mocker.onboarding;

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
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;
import com.paytech.compliance.api.MaterialRequirementsModels.RequirementBody;
import com.paytech.compliance.api.MaterialRequirementsModels.RequirementsResponseBody;
import com.paytech.platform.support.api.tenantprojection.request.StatusRequestBody;
import com.paytech.platform.support.api.tenantprojection.response.ProjectionResponseBody;
import com.paytech.compliance.api.request.ClientReviewClassificationsRequestBody;
import com.paytech.compliance.api.request.ClientReviewRegistrationRequestBody;
import com.paytech.compliance.api.response.ClientReviewCommandResponseBody;
import com.paytech.compliance.api.response.ClientReviewRegistrationResponseBody;

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
    private final Map<String, String[]> tenantSessions = new HashMap<>();
    private final Map<String, Object> requirements = new HashMap<>();
    private final Map<String, Object> files = new HashMap<>();
    private Object review;
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final List<String> fileCallers = new CopyOnWriteArrayList<>();
    private final List<ClientReviewRegistrationRequestBody> reviewRequests = new CopyOnWriteArrayList<>();
    private Object classificationReply;
    private final List<ClientReviewClassificationsRequestBody> classificationRequests = new CopyOnWriteArrayList<>();
    private final Map<String, ProjectionResponseBody> projections = new HashMap<>();
    private final List<StatusRequestBody> projectionRequests = new CopyOnWriteArrayList<>();

    /** 当前账号会话有效。 */
    public synchronized void mockSession(long accountId, long sessionId) {
        sessions.put(Long.toString(accountId), Long.toString(sessionId));
    }

    /** 当前账号的租户上下文会话有效；Platform 返回 TENANT 上下文。 */
    public synchronized void mockTenantSession(long accountId, long sessionId, long tenantId, long memberId) {
        sessions.put(Long.toString(accountId), Long.toString(sessionId));
        tenantSessions.put(Long.toString(accountId), new String[] {Long.toString(tenantId), Long.toString(memberId)});
    }

    /** 某资格类型的通用材料要求。 */
    public synchronized void mockRequirements(String qualificationType, RequirementBody... items) {
        requirements.put(qualificationType, new RequirementsResponseBody("900", "901", List.of(items)));
    }

    /** 某资格类型的材料查询断连。 */
    public synchronized void mockRequirementsUnavailable(String qualificationType) {
        requirements.put(qualificationType, new Fault(0, null, true));
    }

    /** 某账号上传的文件，对象 ACTIVE，扫描状态与内容摘要按本例指定。 */
    public synchronized void mockFile(long uploaderAccountId, long fileId, String contentHash, String scanStatus) {
        mockFile(uploaderAccountId, fileId, contentHash, scanStatus, "ACTIVE");
    }

    /** 某账号上传的文件，扫描与对象状态、内容摘要都按本例指定。 */
    public synchronized void mockFile(long uploaderAccountId, long fileId, String contentHash, String scanStatus, String objectStatus) {
        files.put(Long.toString(fileId), new StoredFileResponseBody(Long.toString(fileId), FileAddress.of(fileId).value(), "client-console-bff",
            "CLIENT_ONBOARDING_MATERIAL", "material.pdf", "application/pdf", "1024", contentHash, scanStatus, objectStatus,
            Long.toString(uploaderAccountId), Instant.parse("2026-10-22T00:00:00Z")));
    }

    /** 文件查询返回 Platform 的明确拒绝，例如 404 FILE_NOT_FOUND。 */
    public synchronized void mockFileRejected(long fileId, int status, String code) {
        files.put(Long.toString(fileId), new Fault(status, problem(status, code), false));
    }

    /** 文件查询返回 Platform 503。 */
    public synchronized void mockFileUnavailable(long fileId) {
        files.put(Long.toString(fileId), new Fault(503, problem(503, "SERVICE_UNAVAILABLE"), false));
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
        String[] tenant = tenantSessions.get(accountId);
        if (tenant != null) {
            return new AccountContextResponseBody(accountId, sessionId, "f***@example.test", true, "TENANT", tenant[0], tenant[1], List.of());
        }
        return new AccountContextResponseBody(accountId, sessionId, "f***@example.test", true, "ACCOUNT", null, null, List.of());
    }

    /** 材料要求查询；返回 DTO、Fault 或 null。 */
    public synchronized Object requirements(String qualificationType) {
        calls.add("requirements:" + qualificationType);
        return requirements.get(qualificationType);
    }

    /** Platform 内部文件查询；记录文件与调用身份头，返回 DTO、Fault 或 null。 */
    public synchronized Object file(String fileId, String callerService) {
        calls.add("file:" + fileId);
        fileCallers.add(String.valueOf(callerService));
        return files.get(fileId);
    }

    /** 文件查询请求携带的 X-Caller-Service 快照。 */
    public List<String> fileCallers() {
        return new ArrayList<>(fileCallers);
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
        tenantSessions.clear();
        requirements.clear();
        files.clear();
        review = null;
        calls.clear();
        fileCallers.clear();
        reviewRequests.clear();
        classificationReply = null;
        classificationRequests.clear();
        projections.clear();
        projectionRequests.clear();
    }
}
