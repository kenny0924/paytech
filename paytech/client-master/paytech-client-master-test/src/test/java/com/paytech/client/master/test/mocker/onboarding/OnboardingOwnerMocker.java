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
import com.paytech.compliance.api.response.ClientReviewRequirementErrorBody;
import com.paytech.compliance.api.response.ClientReviewResultResponseBody;
import com.paytech.compliance.api.response.ProgramMaterialBody;
import com.paytech.compliance.api.response.ProgramResolutionResponseBody;
import com.paytech.platform.support.api.tenantprojection.request.ProvisionRequestBody;
import com.paytech.platform.support.api.tenantprojection.request.StatusRequestBody;
import com.paytech.platform.support.api.tenantprojection.response.ProjectionResponseBody;
import com.paytech.compliance.api.request.ClientReviewClassificationsRequestBody;
import com.paytech.compliance.api.request.ClientReviewRegistrationRequestBody;
import com.paytech.compliance.api.request.ClientReviewWithdrawalRequestBody;
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

    /**
     * 程序的一个材料槽
     *
     * @param requirementCode 材料要求代码
     * @param documentType 接受的材料类型
     * @param required 是否必交
     */
    public record Material(String requirementCode, String documentType, boolean required) {
    }

    /** 程序解析请求的非敏感记录。 */
    public record ProgramCall(String jurisdiction, String partyType, String roleType, String responsiblePartnerId) {
    }

    private final Map<String, String> sessions = new HashMap<>();
    private final Map<String, String[]> tenantSessions = new HashMap<>();
    private final Map<String, Object> programs = new HashMap<>();
    private final List<ProgramCall> programCalls = new CopyOnWriteArrayList<>();
    private Object withdrawal;
    private final Map<String, Object> reviewResults = new HashMap<>();
    private final List<ClientReviewWithdrawalRequestBody> withdrawalRequests = new CopyOnWriteArrayList<>();
    private final Map<String, Object> files = new HashMap<>();
    private Object review;
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final List<String> fileCallers = new CopyOnWriteArrayList<>();
    private final List<ClientReviewRegistrationRequestBody> reviewRequests = new CopyOnWriteArrayList<>();
    private Object classificationReply;
    private final List<ClientReviewClassificationsRequestBody> classificationRequests = new CopyOnWriteArrayList<>();
    private final Map<String, ProjectionResponseBody> projections = new HashMap<>();
    private final List<StatusRequestBody> projectionRequests = new CopyOnWriteArrayList<>();
    private boolean provisionAccepted;
    private final List<ProvisionRequestBody> provisionRequests = new CopyOnWriteArrayList<>();

    /** 当前账号会话有效。 */
    public synchronized void mockSession(long accountId, long sessionId) {
        sessions.put(Long.toString(accountId), Long.toString(sessionId));
    }

    /** 当前账号的租户上下文会话有效；Platform 返回 TENANT 上下文。 */
    public synchronized void mockTenantSession(long accountId, long sessionId, long tenantId, long memberId) {
        sessions.put(Long.toString(accountId), Long.toString(sessionId));
        tenantSessions.put(Long.toString(accountId), new String[] {Long.toString(tenantId), Long.toString(memberId)});
    }

    /** 某 主体类型:角色 的生效程序及其材料槽（适用角色 PRIMARY）。 */
    public synchronized void mockProgram(String partyType, String roleType, Material... items) {
        var body = new ProgramResolutionResponseBody();
        body.setProgramId("9300000000000900");
        body.setProgramCode(partyType + "_" + roleType);
        body.setProgramVersionId("9300000000000901");
        body.setVersionNo(1);
        body.setTier("STANDARD");
        body.setMaterials(java.util.Arrays.stream(items).map(item -> {
            var material = new ProgramMaterialBody();
            material.setRequirementCode(item.requirementCode());
            material.setSubjectRole("PRIMARY");
            material.setDocumentTypes(List.of(item.documentType()));
            material.setRequired(item.required());
            material.setWaivable(false);
            return material;
        }).toList());
        body.setChecks(List.of());
        body.setScreenings(List.of());
        programs.put(partyType + ":" + roleType, body);
    }

    /**
     * 常用程序：组织 REGISTRATION（必交）；个人 IDENTITY（必交）与 ADDRESS（可选）；关联人员 IDENTITY（必交）
     */
    public void mockStandardPrograms() {
        mockProgram("ORGANIZATION", "CLIENT", new Material("REGISTRATION", "REGISTRATION_CERTIFICATE", true));
        mockProgram("INDIVIDUAL", "CLIENT", new Material("IDENTITY", "IDENTITY_DOCUMENT", true),
            new Material("ADDRESS", "PROOF_OF_ADDRESS", false));
        mockProgram("INDIVIDUAL", "RELATED_PERSON", new Material("IDENTITY", "IDENTITY_DOCUMENT", true));
    }

    /** 某 主体类型:角色 的程序解析断连。 */
    public synchronized void mockProgramUnavailable(String partyType, String roleType) {
        programs.put(partyType + ":" + roleType, new Fault(0, null, true));
    }

    /** 某 主体类型:角色 的程序解析返回 503（无程序或程序歧义）。 */
    public synchronized void mockProgramMissing(String partyType, String roleType) {
        programs.put(partyType + ":" + roleType, new Fault(503, problem(503, "PROGRAM_NOT_AVAILABLE"), false));
    }

    /**
     * CP-4：某申请当前任务已退回，带需求级错误与补件期限
     *
     * @param errors 每项为 {需求键, 主体 Party ID（申请级为 null）, 错误码, 补救动作}
     */
    public synchronized void mockReturnedResult(long applicationId, long caseId, String dueTime, String[]... errors) {
        var body = new ClientReviewResultResponseBody();
        body.setApplicationId(Long.toString(applicationId));
        body.setCaseId(Long.toString(caseId));
        body.setStatus("RETURNED");
        body.setOutcome("RETURNED");
        body.setRoundNo(1);
        body.setDueTime(dueTime);
        body.setErrors(java.util.Arrays.stream(errors).map(error -> {
            var item = new ClientReviewRequirementErrorBody();
            item.setRequirementKey(error[0]);
            item.setSubjectPartyId(error[1]);
            item.setCode(error[2]);
            item.setRemediation(error[3]);
            item.setReason("Reviewer note");
            item.setDueTime(dueTime);
            return item;
        }).toList());
        reviewResults.put(Long.toString(applicationId), body);
    }

    /** CP-4 结果；返回 DTO、Fault 或 null。 */
    public synchronized Object reviewResult(String applicationId) {
        calls.add("reviewResult:" + applicationId);
        return reviewResults.get(applicationId);
    }

    /** I03 撤回受理。 */
    public synchronized void mockWithdrawalAccepted() {
        withdrawal = Boolean.TRUE;
    }

    /** I03 撤回被明确拒绝，例如 409 CLIENT_REVIEW_CLOSED。 */
    public synchronized void mockWithdrawalRejected(int status, String code) {
        withdrawal = new Fault(status, problem(status, code), false);
    }

    /** I03 撤回；返回 DTO、Fault 或 null。 */
    public synchronized Object withdraw(String caseId, ClientReviewWithdrawalRequestBody request) {
        calls.add("withdraw:" + caseId);
        withdrawalRequests.add(request);
        if (Boolean.TRUE.equals(withdrawal)) {
            var body = new ClientReviewCommandResponseBody();
            body.setCaseId(caseId);
            body.setStatus("WITHDRAWN");
            body.setAccepted(true);
            body.setProcessing(false);
            return body;
        }
        return withdrawal;
    }

    /** I03 请求快照。 */
    public List<ClientReviewWithdrawalRequestBody> withdrawalRequests() {
        return new ArrayList<>(withdrawalRequests);
    }

    /** 程序解析请求快照。 */
    public List<ProgramCall> programCalls() {
        return new ArrayList<>(programCalls);
    }

    /** 某账号上传的文件，对象 ACTIVE，扫描状态与内容摘要按本例指定。 */
    public synchronized void mockFile(long uploaderAccountId, long fileId, String contentHash, String scanStatus) {
        mockFile(uploaderAccountId, fileId, contentHash, scanStatus, "ACTIVE");
    }

    /** 某账号上传的文件，扫描与对象状态、内容摘要都按本例指定。 */
    public synchronized void mockFile(long uploaderAccountId, long fileId, String contentHash, String scanStatus, String objectStatus) {
        files.put(Long.toString(fileId), new StoredFileResponseBody(Long.toString(fileId), FileAddress.of(fileId).value(), "client-console-bff",
            "CLIENT_ONBOARDING_MATERIAL", "material.pdf", "application/pdf", "1024", contentHash, scanStatus, objectStatus,
            Long.toString(uploaderAccountId), Instant.parse("2026-10-22T00:00:00Z"), "PRIVATE", 1, null, null, null));
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

    /** PLT-I17 查询当前位置；未配置返回 null，已允许首版开通而尚未开通时返回 404 NOT_FOUND。 */
    public synchronized Object projection(String ownerRefId) {
        calls.add("projection:" + ownerRefId);
        var position = projections.get(ownerRefId);
        if (position == null && provisionAccepted) {
            return new Fault(404, problem(404, "NOT_FOUND"), false);
        }
        return position;
    }

    /** 新建 Client 的首版工作区开通由 Platform 受理。 */
    public synchronized void mockProvisionAccepted() {
        provisionAccepted = true;
    }

    /** PLT-I17 首版开通：已配置时按请求建立 PENDING 位置并返回；未配置返回 null。 */
    public synchronized ProjectionResponseBody provision(ProvisionRequestBody request) {
        calls.add("provision:" + request.getOwnerRefId());
        provisionRequests.add(request);
        if (!provisionAccepted) {
            return null;
        }
        var position = new ProjectionResponseBody("CLIENT", request.getOwnerRefId(), request.getRequestedByAccountId(),
            "880" + Long.parseLong(request.getOwnerRefId()) % 1_000_000, "881" + Long.parseLong(request.getOwnerRefId()) % 1_000_000,
            "PENDING", request.getSourceVersion(), request.getPayloadHash());
        projections.put(request.getOwnerRefId(), position);
        return position;
    }

    /** PLT-I17 首版开通请求快照。 */
    public List<ProvisionRequestBody> provisionRequests() {
        return new ArrayList<>(provisionRequests);
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

    /** 程序解析；返回 DTO、Fault 或 null。 */
    public synchronized Object program(String jurisdiction, String partyType, String roleType, String responsiblePartnerId) {
        calls.add("program:" + partyType + ":" + roleType);
        programCalls.add(new ProgramCall(jurisdiction, partyType, roleType, responsiblePartnerId));
        return programs.get(partyType + ":" + roleType);
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
        programs.clear();
        programCalls.clear();
        withdrawal = null;
        reviewResults.clear();
        withdrawalRequests.clear();
        files.clear();
        review = null;
        calls.clear();
        fileCallers.clear();
        reviewRequests.clear();
        classificationReply = null;
        classificationRequests.clear();
        projections.clear();
        projectionRequests.clear();
        provisionAccepted = false;
        provisionRequests.clear();
    }
}
