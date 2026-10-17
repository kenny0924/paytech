package com.paytech.client.console.bff.service.processor;

import com.paytech.client.console.bff.service.model.ClientOnboardingModels.*;
import com.paytech.client.console.bff.ext.rest.response.FormOptionsResponseBody;
import com.paytech.client.contract.api.enums.ClientFormCatalogStatus;
import com.paytech.client.contract.api.onboarding.response.*;
import com.paytech.client.contract.api.onboarding.*;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.ClientDraftResponseBody;
import com.paytech.foundation.common.error.*;
import com.paytech.platform.support.api.enums.*;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileApi;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileModels.FileResponseBody;
import com.paytech.compliance.api.MaterialRequirementsApi;
import com.paytech.compliance.api.ClientReviewResultApi;
import com.paytech.compliance.api.MaterialRequirementsModels.RequirementBody;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 组合已授权 Owner 查询；没有业务写入、持久化缓存或跨库事务。 */
@Component
@lombok.AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientOnboardingProcessor {

    private final ClientDraftApi drafts;
    private final OnboardingFileApi files;
    private final ClientFormOptionsApi catalogs;
    private final ClientSessionProcessor sessions;
    private final MaterialRequirementsApi risk;
    private final ClientOnboardingStartApi applications;
    private final ClientReviewResultApi reviews;
    private final ClientWorkspaceProcessor workspaces;

    /** 有客户可见审核结果的 CCT 状态。 */
    static final Set<String> REVIEWED = Set.of("RETURNED", "APPROVED", "REJECTED");

    /** Risk 退回合同允许的分区。 */
    static final Set<String> RETURN_SECTIONS = Set.of("BASIC", "BUSINESS", "PEOPLE", "MATERIALS");

    /** GET 只导航既有申请并列出本账号工作区，不以读取页面触发创建。 */
    public StartResponseBody start(HttpServletRequest request) {
        var identity = sessions.identity(request);
        sessions.context(request);
        var result = applications.start("client-console-bff", identity[0], identity[1]);
        if (result == null || result.applications() == null || result.applications().size() > 20) {
            throw new ServiceUnavailableException();
        }
        var active = result
            .applications()
            .stream()
            .filter(a -> a.active())
            .toList();
        if (active.size() > 1) {
            throw new ServiceUnavailableException();
        }
        var workspaceList = workspaces.list(request, identity);
        sessions.context(request);
        return new StartResponseBody(
            result.applications(),
            result.hasMore(),
            active.isEmpty() ? null : active.getFirst().applicationId(),
            workspaceList
        );
    }

    /** C03 并发组合通用目录与主主体材料要求；一个 Owner 不可用时保留另一方事实，不按国家区分。 */
    public FormOptionsResponseBody options(HttpServletRequest request, String type) {
        var identity=sessions.identity(request);
        sessions.context(request);
        if (!Set.of("INDIVIDUAL", "ORGANIZATION").contains(type)) {
            throw new InvalidParameterException("客户类型无效");
        }
        var executor=Executors.newFixedThreadPool(2,Thread.ofVirtual().factory());
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        try {
            var catalogFuture=executor.submit(context(() -> catalog(identity,type)));
            var requirementsFuture=executor.submit(context(() -> requirements(
                type.equals("ORGANIZATION") ? "ORGANIZATION_KYB" : "INDIVIDUAL_KYC")));
            var catalog=result(catalogFuture,deadline);
            var required=result(requirementsFuture,deadline);
            sessions.context(request);
            return new FormOptionsResponseBody(type,catalog==null ? "UNAVAILABLE" : catalog.getCatalogStatus(),
                catalog==null ? List.of() : catalog.getOrganizationTypes(),
                catalog==null ? List.of() : catalog.getBusinessModels(),
                catalog==null ? List.of() : catalog.getIdentifierTypes(),
                required==null ? "UNAVAILABLE" : "AVAILABLE",
                required==null ? List.of() : required);
        } finally { executor.shutdownNow(); }
    }

    /** CCT 目录必须属于准确客户类型；未知状态与缺失列表显式不可用。 */
    ClientFormOptionsResponseBody catalog(String[] identity,String type) {
        var value=catalogs.options("client-console-bff",identity[0],identity[1],type);
        if(value==null || !type.equals(value.getClientType())
            || value.getOrganizationTypes()==null || value.getBusinessModels()==null || value.getIdentifierTypes()==null
            || value.getOrganizationTypes().size()>200 || value.getBusinessModels().size()>200 || value.getIdentifierTypes().size()>200) {
            throw new ServiceUnavailableException();
        }
        try { ClientFormCatalogStatus.valueOf(value.getCatalogStatus()); }
        catch(IllegalArgumentException | NullPointerException invalid) { throw new ServiceUnavailableException(); }
        if("AVAILABLE".equals(value.getCatalogStatus()) && (value.getBusinessModels().isEmpty() || value.getIdentifierTypes().isEmpty()
            || ("ORGANIZATION".equals(type) && value.getOrganizationTypes().isEmpty()))) { throw new ServiceUnavailableException(); }
        if(value.getIdentifierTypes().stream().anyMatch(i -> i==null || !Set.of("REQUIRED","NONE").contains(i.getIssuingCountryMode()))) {
            throw new ServiceUnavailableException();
        }
        return value;
    }

    /** 文件和要求并发只读聚合；部分依赖失败显式 unavailable，不隐藏 CCT 引用。 */
    public ApplicationResponseBody read(HttpServletRequest request, String applicationId) {
        var identity = sessions.identity(request);
        sessions.context(request);
        var draft = drafts.read("client-console-bff", identity[0], identity[1], applicationId);
        validate(draft, applicationId);
        var executor = Executors.newFixedThreadPool(8, Thread.ofVirtual().factory());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        try {
            var subjects = subjects(draft);
            var required = new LinkedHashMap<String, Future<List<RequirementBody>>>();
            for (var subject : subjects) {
                required.computeIfAbsent(subject.qualificationType(), type -> executor.submit(context(() -> requirements(type))));
            }
            var reviewFuture = REVIEWED.contains(draft.status())
                ? executor.submit(context(() -> review(draft.applicationId(), draft.status()))) : null;
            var metadata = new LinkedHashMap<String, Future<FileResponseBody>>();
            for (var doc : draft.documents()) {
                metadata.computeIfAbsent(doc.fileId(), id -> executor.submit(context(() -> file(identity, id))));
            }
            var material = subjects
                .stream()
                .map(subject -> {
                    var list = result(required.get(subject.qualificationType()), deadline);
                    return new MaterialSubject(
                        subject.subjectPartyId(),
                        subject.qualificationType(),
                        list == null ? "UNAVAILABLE" : "AVAILABLE",
                        list == null ? List.of() : list
                    );
                })
                .toList();
            var documents = draft
                .documents()
                .stream()
                .map(doc -> {
                    var file = result(metadata.get(doc.fileId()), deadline);
                    return new Document(
                        doc.documentId(),
                        doc.subjectPartyId(),
                        doc.requirementCode(),
                        doc.documentType(),
                        doc.fileId(),
                        doc.subjectInScope(),
                        file == null ? "UNAVAILABLE" : "AVAILABLE",
                        file
                    );
                })
                .toList();
            var review = result(reviewFuture, deadline);
            sessions.context(request);
            return new ApplicationResponseBody(
                draft.applicationId(),
                draft.applicationNo(),
                draft.clientId(),
                draft.clientType(),
                draft.subjectPartyId(),
                draft.status(),
                draft.savedAt(),
                draft.editable(),
                draft.values(),
                draft.people(),
                draft.relations(),
                documents,
                material,
                reviewFuture == null ? "NONE" : review == null ? "UNAVAILABLE" : "AVAILABLE",
                review
            );
        } finally {
            executor.shutdownNow();
        }
    }

    /** Risk 审核结果只与 CCT 结束状态一致时采用；不一致说明投影尚未同步，按不可用处理。 */
    ReviewResult review(String applicationId, String status) {
        var value = reviews.result("client-console-bff", applicationId);
        if (value == null || !applicationId.equals(value.getApplicationId()) || !status.equals(value.getOutcome())) {
            throw new ServiceUnavailableException();
        }
        if ("RETURNED".equals(status) && (value.getReturnSection() == null || !RETURN_SECTIONS.contains(value.getReturnSection()))) {
            throw new ServiceUnavailableException();
        }
        return new ReviewResult(value.getOutcome(), value.getReturnSection(), value.getPublicNote(), value.getDecidedAt());
    }

    /** 已发布代码必须识别，未知状态不默认开放下载。 */
    FileResponseBody file(String[] identity, String id) {
        var value = files.read(identity[0], identity[1], id);
        if (value == null || !id.equals(value.fileId()) || value.allowedActions() == null) {
            throw new ServiceUnavailableException();
        }
        FileScanStatus.fromCode(value.scanStatus());
        FileObjectStatus.fromCode(value.objectStatus());
        if (
            value
                .allowedActions()
                .stream()
                .anyMatch(a -> !"DOWNLOAD".equals(a))
        ) {
            throw new ServiceUnavailableException();
        }
        return value;
    }

    /** 规则目录只允许真实、唯一且有界的条目；Program 身份不投影给浏览器。 */
    List<RequirementBody> requirements(String type) {
        var value = risk.read("client-console-bff", type);
        if (value == null || value.items() == null || value.items().isEmpty() || value.items().size() > 1024) {
            throw new ServiceUnavailableException();
        }
        var codes = new HashSet<String>();
        for (var item : value.items()) {
            if (
                item == null ||
                item.requirementCode() == null ||
                !item.requirementCode().matches("[A-Z][A-Z0-9_]{0,63}") ||
                item.documentType() == null ||
                !item.documentType().matches("[A-Z][A-Z0-9_]{0,63}") ||
                !codes.add(item.requirementCode())
            ) {
                throw new ServiceUnavailableException();
            }
        }
        return value.items();
    }

    /** 主主体坐标来自 CCT；只为仍有关联的人员查询要求。 */
    List<MaterialSubject> subjects(ClientDraftResponseBody draft) {
        var rows = new ArrayList<MaterialSubject>();
        boolean organization = draft.clientType().equals("ORGANIZATION");
        rows.add(
            subject(
                draft.subjectPartyId(),
                organization ? "ORGANIZATION_KYB" : "INDIVIDUAL_KYC"
            )
        );
        for (var person : draft.people()) {
            if (
                draft
                    .relations()
                    .stream()
                    .anyMatch(
                        relation ->
                            relation.relatedPartyId().equals(person.partyId()) &&
                            Set.of("DRAFT", "ACTIVE").contains(relation.status())
                    )
            ) {
                rows.add(subject(person.partyId(), "RELATED_PERSON_KYC"));
            }
        }
        return rows;
    }

    /** 材料主体只由资格类型决定材料要求；要求在聚合时按类型查询一次。 */
    MaterialSubject subject(String party, String type) {
        return new MaterialSubject(party, type, "UNAVAILABLE", List.of());
    }

    /** 限制响应工作量，并拒绝 Owner 身份不匹配。 */
    void validate(ClientDraftResponseBody draft, String id) {
        if (
            draft == null ||
            !id.equals(draft.applicationId()) ||
            draft.subjectPartyId() == null ||
            !draft.subjectPartyId().matches("[1-9][0-9]{0,18}") ||
            !Set.of("INDIVIDUAL", "ORGANIZATION").contains(draft.clientType()) ||
            draft.people() == null ||
            draft.relations() == null ||
            draft.documents() == null ||
            draft.values() == null ||
            draft.people().size() > 100 ||
            draft.documents().size() > 500
        ) {
            throw new ServiceUnavailableException();
        }
    }

    /** 并发只读任务继承当前请求日志坐标，结束后清除虚拟线程上下文。 */
    <T> Callable<T> context(Supplier<T> action) {
        var values = org.slf4j.MDC.getCopyOfContextMap();
        return () -> {
            if (values != null) {
                org.slf4j.MDC.setContextMap(values);
            }
            try {
                return action.get();
            } finally {
                org.slf4j.MDC.clear();
            }
        };
    }

    /** 有界等待只影响展示；认证失败仍直接终止整页。 */
    <T> T result(Future<T> future, long deadline) {
        if (future == null) {
            return null;
        }
        try {
            return future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException();
        } catch (ExecutionException failure) {
            if (
                failure.getCause() instanceof FailException rejected &&
                "CLIENT_SESSION_INVALID".equals(rejected.getCode())
            ) {
                throw rejected;
            }
            return null;
        } catch (TimeoutException unavailable) {
            future.cancel(true);
            return null;
        }
    }
}
