package com.paytech.client.console.bff.service.processor;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.domain.enums.SectionAvailability;
import com.paytech.client.console.bff.integration.CctOnboardingClient;
import com.paytech.client.console.bff.integration.ComplianceOnboardingClient;
import com.paytech.client.console.bff.integration.PlatformAuthenticationClient;
import com.paytech.client.console.bff.integration.PlatformOnboardingFileClient;
import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.client.console.bff.service.model.ClientOnboardingModels.ApplicationResponseBody;
import com.paytech.client.console.bff.service.model.ClientOnboardingModels.Document;
import com.paytech.client.console.bff.service.model.ClientOnboardingModels.MaterialSubject;
import com.paytech.client.console.bff.service.model.ClientOnboardingModels.ReviewResult;
import com.paytech.client.console.bff.service.processor.request.ClientApplicationDetailsRequest;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.ClientDraftResponseBody;
import com.paytech.compliance.api.MaterialRequirementsModels.RequirementBody;
import com.paytech.compliance.api.enums.MaterialQualificationType;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.onboardingfiles.response.FileResponseBody;

import lombok.AllArgsConstructor;

/**
 * 入驻申请详情：CCT 草稿为主，并发补充 Compliance 材料要求、审核结果与 Platform 文件状态
 * <li>材料要求按资格类型各查询一次；文件按 fileId 各查询一次；审核结果只在 CCT 状态为 RETURNED/APPROVED/REJECTED 时读取。</li>
 * <li>部分依赖失败只把对应区域标为 UNAVAILABLE，保留 CCT 引用、不伪造空数据；会话失效终止整页。</li>
 * <li>全部并发读取共享 3 秒等待上限、最多 8 个并发；读取前后各回读一次 Platform 会话。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientApplicationDetailsProcessor
    extends AbstractProcessor<ClientApplicationDetailsRequest, ApplicationResponseBody> {
    /** 并发读取的总等待上限。 */
    static final long DEADLINE_SECONDS = 3;

    /** 并发读取上限。 */
    static final int MAX_PARALLEL_READS = 8;

    /** 有客户可见审核结果的 CCT 状态。 */
    static final Set<String> REVIEWED = Set.of("RETURNED", "APPROVED", "REJECTED");

    /** 仍关联申请的关系状态。 */
    static final Set<String> LINKED_RELATION_STATUSES = Set.of("DRAFT", "ACTIVE");

    private final CctOnboardingClient onboarding;
    private final ComplianceOnboardingClient compliance;
    private final PlatformAuthenticationClient authentication;
    private final PlatformOnboardingFileClient files;

    @Override
    protected ApplicationResponseBody processRequest(ClientApplicationDetailsRequest request) {
        var actor = request.getActor();
        authentication.context(actor);
        var draft = onboarding.draft(actor, request.getApplicationId());
        var executor = Executors.newFixedThreadPool(MAX_PARALLEL_READS, Thread.ofVirtual().factory());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DEADLINE_SECONDS);
        try {
            var subjects = materialSubjects(draft);
            var requirements = submitRequirements(executor, subjects);
            var reviewFuture =
                REVIEWED.contains(draft.status())
                    ? executor.submit(ConcurrentPageReads
                        .withRequestContext(() -> compliance.reviewResult(draft.applicationId(), draft.status())))
                    : null;
            var fileFutures = submitFiles(executor, actor, draft);
            var materials = materials(subjects, requirements, deadline);
            var documents = documents(draft, fileFutures, deadline);
            var review = ConcurrentPageReads.result(reviewFuture, deadline);
            authentication.context(actor);
            String reviewStatus =
                reviewFuture == null ? SectionAvailability.NONE.getCode() : SectionAvailability.of(review);
            return response(draft, documents, materials, reviewStatus, review);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 主主体加仍有关联的人员；材料要求在聚合时按资格类型查询。 */
    static List<MaterialSubject> materialSubjects(ClientDraftResponseBody draft) {
        var subjects = new ArrayList<MaterialSubject>();
        subjects.add(placeholder(draft.subjectPartyId(), OnboardingQualifications.primary(draft.clientType())));
        for (var person : draft.people()) {
            boolean linked =
                draft.relations().stream().anyMatch(relation -> relation.relatedPartyId().equals(person.partyId())
                    && LINKED_RELATION_STATUSES.contains(relation.status()));
            if (linked) {
                subjects.add(placeholder(person.partyId(), MaterialQualificationType.RELATED_PERSON_KYC.getCode()));
            }
        }
        return subjects;
    }

    /** 尚未读取要求的材料主体。 */
    static MaterialSubject placeholder(String subjectPartyId, String qualificationType) {
        return new MaterialSubject(subjectPartyId, qualificationType, SectionAvailability.UNAVAILABLE.getCode(),
            List.of());
    }

    /** 每种资格类型提交一次材料要求读取。 */
    Map<String, Future<List<RequirementBody>>> submitRequirements(ExecutorService executor,
        List<MaterialSubject> subjects) {
        var futures = new LinkedHashMap<String, Future<List<RequirementBody>>>();
        for (var subject : subjects) {
            futures.computeIfAbsent(subject.qualificationType(),
                type -> executor.submit(ConcurrentPageReads.withRequestContext(() -> compliance.requirements(type))));
        }
        return futures;
    }

    /** 每个 fileId 提交一次文件状态读取。 */
    Map<String, Future<FileResponseBody>> submitFiles(ExecutorService executor, ClientActor actor,
        ClientDraftResponseBody draft) {
        var futures = new LinkedHashMap<String, Future<FileResponseBody>>();
        for (var document : draft.documents()) {
            futures.computeIfAbsent(document.fileId(),
                id -> executor.submit(ConcurrentPageReads.withRequestContext(() -> files.file(actor, id))));
        }
        return futures;
    }

    /** 为每个材料主体填入要求；读取失败的类型标为不可用。 */
    static List<MaterialSubject> materials(List<MaterialSubject> subjects,
        Map<String, Future<List<RequirementBody>>> requirements, long deadline) {
        return subjects.stream().map(subject -> {
            var list = ConcurrentPageReads.result(requirements.get(subject.qualificationType()), deadline);
            return new MaterialSubject(subject.subjectPartyId(), subject.qualificationType(),
                SectionAvailability.of(list), list == null ? List.of() : list);
        }).toList();
    }

    /** 为每条材料引用附上文件状态；文件无法查询时只保留 CCT 引用。 */
    static List<Document> documents(ClientDraftResponseBody draft, Map<String, Future<FileResponseBody>> files,
        long deadline) {
        return draft.documents().stream().map(document -> {
            var file = ConcurrentPageReads.result(files.get(document.fileId()), deadline);
            return new Document(document.documentId(), document.subjectPartyId(), document.requirementCode(),
                document.documentType(), document.fileId(), document.subjectInScope(), SectionAvailability.of(file),
                file);
        }).toList();
    }

    /** 组装申请详情页。 */
    static ApplicationResponseBody response(ClientDraftResponseBody draft, List<Document> documents,
        List<MaterialSubject> materials, String reviewStatus, ReviewResult review) {
        return new ApplicationResponseBody(draft.applicationId(), draft.applicationNo(), draft.clientId(),
            draft.clientType(), draft.subjectPartyId(), draft.status(), draft.savedAt(), draft.editable(),
            draft.values(), draft.people(), draft.relations(), documents, materials, reviewStatus, review);
    }
}
