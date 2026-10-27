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
import com.paytech.client.console.bff.integration.PlatformFileClient;
import com.paytech.client.console.bff.service.model.response.ApplicationResponseBody;
import com.paytech.client.console.bff.service.model.response.ApplicationDocument;
import com.paytech.client.console.bff.service.model.response.OnboardingFileResponseBody;
import com.paytech.client.console.bff.service.model.response.ReviewResult;
import com.paytech.client.console.bff.service.processor.request.ClientApplicationDetailsRequest;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.ClientDraftResponseBody;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.files.FileAddress;

import lombok.AllArgsConstructor;
import com.paytech.client.master.api.onboarding.response.RequirementItemResponseBody;

/**
 * 入驻申请详情：client-master 草稿为主，并发补充需求清单、审核结果与 Platform 文件状态
 * <li>材料槽随采集 Schema 下发（client-master 合并 compliance 程序），本页不再按资格类型查询材料要求。</li>
 * <li>文件按申请保存的文件地址各查询一次（地址出现在本人申请中即可读）；审核结果只在申请状态为 RETURNED/APPROVED/REJECTED/WITHDRAWN 时读取。</li>
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
    static final Set<String> REVIEWED = Set.of("RETURNED", "APPROVED", "REJECTED", "WITHDRAWN");

    /** 仍需要需求清单的可编辑状态。 */
    static final Set<String> EDITABLE = Set.of("DRAFT", "RETURNED");

    private final CctOnboardingClient onboarding;
    private final ComplianceOnboardingClient compliance;
    private final PlatformAuthenticationClient authentication;
    private final PlatformFileClient files;

    @Override
    protected ApplicationResponseBody processRequest(ClientApplicationDetailsRequest request) {
        var actor = request.getActor();
        authentication.context(actor);
        var draft = onboarding.draft(actor, request.getApplicationId());
        var executor = Executors.newFixedThreadPool(MAX_PARALLEL_READS, Thread.ofVirtual().factory());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DEADLINE_SECONDS);
        try {
            var reviewFuture =
                REVIEWED.contains(draft.status())
                    ? executor.submit(ConcurrentPageReads
                        .withRequestContext(() -> compliance.reviewResult(draft.applicationId(), draft.status())))
                    : null;
            var requirementsFuture = EDITABLE.contains(draft.status())
                ? executor.submit(ConcurrentPageReads.withRequestContext(() -> onboarding.requirements(actor, draft.applicationId())))
                : null;
            var fileFutures = submitFiles(executor, draft);
            var documents = documents(draft, fileFutures, deadline);
            var review = ConcurrentPageReads.result(reviewFuture, deadline);
            var requirements = ConcurrentPageReads.result(requirementsFuture, deadline);
            authentication.context(actor);
            String reviewStatus =
                reviewFuture == null ? SectionAvailability.NONE.getCode() : SectionAvailability.of(review);
            String requirementsStatus =
                requirementsFuture == null ? SectionAvailability.NONE.getCode() : SectionAvailability.of(requirements);
            return response(draft, documents, reviewStatus, review, requirementsStatus,
                requirements == null ? List.of() : requirements);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 每个文件地址提交一次文件状态读取。 */
    Map<String, Future<OnboardingFileResponseBody>> submitFiles(ExecutorService executor,
        ClientDraftResponseBody draft) {
        var futures = new LinkedHashMap<String, Future<OnboardingFileResponseBody>>();
        for (var document : draft.documents()) {
            futures.computeIfAbsent(document.fileAddress(), address -> executor.submit(ConcurrentPageReads
                .withRequestContext(() -> OnboardingFileResponseBody.of(files.file(fileId(address))))));
        }
        return futures;
    }

    /** CCT 回执已校验地址格式，这里只取出接口寻址用的文件 ID。 */
    static String fileId(String fileAddress) {
        return Long.toString(FileAddress.parse(fileAddress).fileId());
    }

    /** 为每条材料引用附上文件状态；文件无法查询时只保留 CCT 引用。 */
    static List<ApplicationDocument> documents(ClientDraftResponseBody draft,
        Map<String, Future<OnboardingFileResponseBody>> files, long deadline) {
        return draft.documents().stream().map(document -> {
            var file = ConcurrentPageReads.result(files.get(document.fileAddress()), deadline);
            return new ApplicationDocument(document.documentId(), document.subjectPartyId(), document.requirementCode(),
                document.documentType(), fileId(document.fileAddress()), document.fileAddress(),
                document.subjectInScope(), SectionAvailability.of(file), file);
        }).toList();
    }

    /** 组装申请详情页。 */
    static ApplicationResponseBody response(ClientDraftResponseBody draft, List<ApplicationDocument> documents,
        String reviewStatus, ReviewResult review, String requirementsStatus, List<RequirementItemResponseBody> requirements) {
        return new ApplicationResponseBody(draft.applicationId(), draft.applicationNo(), draft.clientId(),
            draft.clientType(), draft.subjectPartyId(), draft.status(), draft.savedAt(), draft.editable(),
            draft.values(), draft.people(), draft.relations(), documents, reviewStatus, review, draft.jurisdictionCode(),
            draft.organizationTypeCode(), draft.schemaId(), draft.reviewDueTime(), requirementsStatus, requirements,
            draft.responsiblePartnerId(), draft.responsiblePartnerName(), draft.declarations() == null ? List.of() : draft.declarations());
    }
}
