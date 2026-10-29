package com.paytech.operations.console.bff.service.processor;

import static com.paytech.operations.console.bff.service.processor.ClientReviewDetailsProcessor.result;
import static com.paytech.operations.console.bff.service.processor.ClientReviewDetailsProcessor.withRequestContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.paytech.client.contract.api.onboarding.ClientDraftModels.ClientDraftResponseBody;
import com.paytech.client.contract.api.onboarding.response.ClassificationItemResponseBody;
import com.paytech.client.contract.api.onboarding.response.ClassificationSetResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminClientDetailResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminClientSummaryResponseBody;
import com.paytech.compliance.api.response.ClientReviewCaseResponseBody;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.domain.enums.ReadAvailability;
import com.paytech.operations.console.bff.domain.enums.UnderwritingInputStatus;
import com.paytech.operations.console.bff.integration.ClientReviewCctClient;
import com.paytech.operations.console.bff.integration.ClientReviewRiskClient;
import com.paytech.operations.console.bff.integration.OnboardingAdminClient;
import com.paytech.operations.console.bff.integration.PlatformReferenceDataClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.MccResponseBody;
import com.paytech.operations.console.bff.service.model.riskunderwriting.UnderwritingClientBody;
import com.paytech.operations.console.bff.service.model.riskunderwriting.UnderwritingInputAssembly;
import com.paytech.operations.console.bff.service.model.riskunderwriting.UnderwritingSourcesBody;
import com.paytech.risk.decision.api.control.request.UnderwritingInputBody;

import lombok.AllArgsConstructor;

/**
 * 按 Client 从 Owner 读取并组装承保输入快照（工作台、命令转发与预览共用；技术方案 S-02、O-6）
 * <li>client-master 主档 → 最近一次已批准申请（按提交时间）的尽调案件 → 并发读取 compliance 案件、client-master 申请资料与分类
 * （共享 3 秒上限）→ Platform 按主 MCC 读取卡组织高风险标记。</li>
 * <li>主档失败整体失败；其他来源失败或超时标为 UNAVAILABLE、不伪造数据。strict 时（命令转发）任一来源不可用返回 503，不提交。</li>
 * <li>Owner 值不符合 Risk 输入合同（金额格式、代码、长度）时按未采集处理，由 Risk 的提交必填校验兜底。</li>
 */
@Component
@AllArgsConstructor
public class UnderwritingInputAssembler {
    /** client-master 申请已批准状态（ClientApplicationStatus.APPROVED）。 */
    static final String APPROVED_APPLICATION = "APPROVED";

    /** 申请资料中业务信息分组。 */
    static final String BUSINESS = "business";

    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH");
    private static final Set<String> TIERS = Set.of("SIMPLIFIED", "STANDARD", "ENHANCED");
    private static final Set<String> FULFILLMENT_TYPES = Set.of("INSTANT", "PRE_ORDER", "SUBSCRIPTION", "SERVICE");
    private static final String AMOUNT = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?";

    private final ClientReviewCctClient cct;
    private final ClientReviewRiskClient compliance;
    private final OnboardingAdminClient onboardingAdmin;
    private final PlatformReferenceDataClient referenceData;

    /**
     * 组装承保输入
     *
     * @param operator 当前操作人
     * @param clientId Client ID
     * @param strict 命令转发为 true：任一来源不可用抛 503
     * @return Client 摘要、输入快照、输入状态与各来源读取结果
     */
    public UnderwritingInputAssembly assemble(OperatorContext operator, String clientId, boolean strict) {
        AdminClientDetailResponseBody detail = onboardingAdmin.client(operator, clientId);
        AdminClientSummaryResponseBody summary = detail.getClient();
        if (summary == null || !clientId.equals(summary.getClientId())) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        UnderwritingInputBody input = baseInput(summary);
        UnderwritingClientBody client = new UnderwritingClientBody(clientId, summary.getClientNo(),
            input.getClientDisplayName(), summary.getClientType(), summary.getJurisdictionCode());
        AdminClientDetailResponseBody.Application application = latestApproved(detail.getApplications());
        if (application == null) {
            String none = ReadAvailability.NOT_APPLICABLE.getCode();
            return new UnderwritingInputAssembly(client, input, UnderwritingInputStatus.DD_NOT_APPROVED,
                new UnderwritingSourcesBody(none, none, none, none));
        }
        UnderwritingInputAssembly assembly = fromCase(operator, clientId, application.getDueDiligenceCaseId(), client, input);
        if (strict && assembly.getStatus() == UnderwritingInputStatus.SOURCE_UNAVAILABLE) {
            throw new ServiceUnavailableException(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        return assembly;
    }

    /** 并发读取案件、申请资料与分类，再读取主 MCC 的高风险标记。 */
    UnderwritingInputAssembly fromCase(OperatorContext operator, String clientId, String caseId, UnderwritingClientBody client,
        UnderwritingInputBody input) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(ClientReviewDetailsProcessor.DEADLINE_SECONDS);
            var reviewFuture = executor.submit(withRequestContext(() -> compliance.detail(operator, caseId)));
            var draftFuture = executor.submit(withRequestContext(() -> cct.application(operator, caseId)));
            var classificationFuture = executor.submit(withRequestContext(() -> cct.classifications(operator, caseId)));
            ClientReviewCaseResponseBody review = result(reviewFuture, deadline);
            ClientDraftResponseBody draft = result(draftFuture, deadline);
            ClassificationSetResponseBody classification = result(classificationFuture, deadline);
            requireSameClient(clientId, review, draft);
            ClassificationItemResponseBody primary = primary(classification);
            String mccCode = primary == null ? null : code(primary.getMccCode(), "[0-9]{4}");
            List<MccResponseBody> mccs = mccCode == null ? List.of()
                : result(executor.submit(withRequestContext(() -> referenceData.mccsByCodes(List.of(mccCode)))), deadline);
            applyCase(input, review);
            applyApplication(input, draft);
            applyClassification(input, classification, primary, mccCode, mccs);
            UnderwritingSourcesBody sources = new UnderwritingSourcesBody(availability(review), availability(draft),
                availability(classification), mccCode == null ? ReadAvailability.NOT_APPLICABLE.getCode() : availability(mccs));
            return new UnderwritingInputAssembly(client, input, status(sources, input), sources);
        }
    }

    /** Client 主档带出的快照字段；卡组织高风险标记默认 false，由 MCC 读取覆盖。 */
    static UnderwritingInputBody baseInput(AdminClientSummaryResponseBody summary) {
        UnderwritingInputBody input = new UnderwritingInputBody();
        String legalName = summary.getLegalName();
        input.setClientDisplayName(legalName == null || legalName.isBlank() ? summary.getClientNo() : truncate(legalName, 255));
        input.setClientNo(summary.getClientNo());
        input.setClientType(summary.getClientType());
        input.setJurisdictionCode(summary.getJurisdictionCode());
        input.setNetworkHighRiskMcc(Boolean.FALSE);
        return input;
    }

    /** 最近一次已批准且有尽调案件的申请（按提交时间，其次更新时间）；没有返回 null。 */
    static AdminClientDetailResponseBody.Application latestApproved(List<AdminClientDetailResponseBody.Application> applications) {
        if (applications == null) {
            return null;
        }
        return applications.stream().filter(Objects::nonNull)
            .filter(application -> APPROVED_APPLICATION.equals(application.getStatus()))
            .filter(application -> application.getDueDiligenceCaseId() != null)
            .max(Comparator.comparing(AdminClientDetailResponseBody.Application::getSubmittedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(AdminClientDetailResponseBody.Application::getUpdatedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder())))
            .orElse(null);
    }

    /** 案件与申请资料须属于请求的 Client，否则按上游无效响应处理。 */
    static void requireSameClient(String clientId, ClientReviewCaseResponseBody review, ClientDraftResponseBody draft) {
        boolean reviewMismatch = review != null && review.getClientId() != null && !clientId.equals(review.getClientId());
        boolean draftMismatch = draft != null && draft.clientId() != null && !clientId.equals(draft.clientId());
        if (reviewMismatch || draftMismatch) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
    }

    /** compliance 案件：CRR、档次与案件版本坐标。 */
    static void applyCase(UnderwritingInputBody input, ClientReviewCaseResponseBody review) {
        if (review == null) {
            return;
        }
        input.setCrr(oneOf(review.getRiskRating(), RISK_LEVELS));
        input.setDdTier(oneOf(review.getTier(), TIERS));
        input.setSourceCaseId(code(review.getCaseId(), "[1-9][0-9]{0,18}"));
        input.setSourceCaseVersion(code(review.getVersion(), "0|[1-9][0-9]{0,18}"));
    }

    /** client-master 申请资料：业务信息（含 REQ-12 四项）与资料版本坐标。 */
    static void applyApplication(UnderwritingInputBody input, ClientDraftResponseBody draft) {
        if (draft == null) {
            return;
        }
        input.setSourceApplicationId(code(draft.applicationId(), "[1-9][0-9]{0,18}"));
        input.setSourceDraftRevision(code(draft.draftRevision(), "0|[1-9][0-9]{0,18}"));
        Map<String, String> business = draft.values() == null ? null : draft.values().get(BUSINESS);
        if (business == null) {
            return;
        }
        String volume = amount(business.get("expectedVolume"));
        String ticket = amount(business.get("expectedTicket"));
        input.setExpectedMonthlyVolume(volume);
        input.setExpectedTicket(ticket);
        input.setVolumeCurrency(volume == null && ticket == null ? null : code(business.get("currency"), "[A-Z]{3}"));
        input.setFulfillmentType(oneOf(business.get("fulfillmentType"), FULFILLMENT_TYPES));
        input.setFulfillmentDays("PRE_ORDER".equals(input.getFulfillmentType()) ? days(business.get("fulfillmentDays")) : null);
        input.setWebsiteUrl(text(business.get("websiteUrl"), 2048));
        input.setRefundPolicyUrl(text(business.get("refundPolicyUrl"), 2048));
        input.setExpectedPaymentMethods(text(business.get("expectedPaymentMethods"), 512));
    }

    /** client-master 分类：主 MCC / 行业与确认时间；Platform MCC：卡组织高风险标记。 */
    static void applyClassification(UnderwritingInputBody input, ClassificationSetResponseBody classification,
        ClassificationItemResponseBody primary, String mccCode, List<MccResponseBody> mccs) {
        if (classification == null) {
            return;
        }
        input.setMccCode(mccCode);
        input.setIndustryCode(primary == null ? null : text(primary.getIndustryCode(), 64));
        input.setSourceClassificationConfirmedAt(classification.isConfirmed() ? instant(classification.getConfirmedAt()) : null);
        if (mccs != null) {
            input.setNetworkHighRiskMcc(mccs.stream().anyMatch(mcc -> Objects.equals(mccCode, mcc.getMccCode())
                && Boolean.TRUE.equals(mcc.getNetworkHighRisk())));
        }
    }

    /** 主分类项；没有标记主项时取第一项。 */
    static ClassificationItemResponseBody primary(ClassificationSetResponseBody classification) {
        if (classification == null || classification.getItems() == null || classification.getItems().isEmpty()) {
            return null;
        }
        return classification.getItems().stream().filter(Objects::nonNull).filter(ClassificationItemResponseBody::isPrimary)
            .findFirst().orElse(classification.getItems().getFirst());
    }

    /** 来源不可用优先，其次分类未确认，否则可提交。 */
    static UnderwritingInputStatus status(UnderwritingSourcesBody sources, UnderwritingInputBody input) {
        String unavailable = ReadAvailability.UNAVAILABLE.getCode();
        if (List.of(sources.getDueDiligenceCase(), sources.getApplication(), sources.getClassification(), sources.getMcc())
            .contains(unavailable)) {
            return UnderwritingInputStatus.SOURCE_UNAVAILABLE;
        }
        return input.getSourceClassificationConfirmedAt() == null || input.getMccCode() == null
            ? UnderwritingInputStatus.CLASSIFICATION_NOT_CONFIRMED : UnderwritingInputStatus.READY;
    }

    /** 读取结果的可用性标记；null 表示失败或超时。 */
    static String availability(Object value) {
        return (value == null ? ReadAvailability.UNAVAILABLE : ReadAvailability.AVAILABLE).getCode();
    }

    /** 合同金额：非负十进制，最多 16 位整数、8 位小数；否则按未采集。 */
    static String amount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String plain = new BigDecimal(value.strip()).stripTrailingZeros().toPlainString();
            return plain.matches(AMOUNT) ? plain : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    /** 预售交付天数 1—365；否则按未采集。 */
    static Integer days(String value) {
        if (value == null || !value.strip().matches("[1-9][0-9]{0,2}")) {
            return null;
        }
        int days = Integer.parseInt(value.strip());
        return days <= 365 ? days : null;
    }

    /** 分类确认时间规范为微秒 UTC；无法解析按未确认。 */
    static String instant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value).truncatedTo(ChronoUnit.MICROS).toString();
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    /** 闭合代码；不在集合内按未采集。 */
    static String oneOf(String value, Set<String> allowed) {
        return value != null && allowed.contains(value) ? value : null;
    }

    /** 符合格式的代码；否则按未采集。 */
    static String code(String value, String pattern) {
        return value != null && value.matches(pattern) ? value : null;
    }

    /** 非空且不超长的文本；否则按未采集。 */
    static String text(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength ? null : value;
    }

    /** 截断到最大长度。 */
    static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
