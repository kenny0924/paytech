package com.paytech.operations.console.bff.ext.rest;

import java.util.function.Supplier;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.contract.api.onboarding.response.ClassificationSetResponseBody;
import com.paytech.compliance.api.request.ClientReviewCheckResultRequestBody;
import com.paytech.compliance.api.request.ClientReviewDecisionRequestBody;
import com.paytech.compliance.api.request.ClientReviewEvidenceReviewRequestBody;
import com.paytech.compliance.api.request.ClientReviewHitResolutionRequestBody;
import com.paytech.compliance.api.request.ClientReviewRecommendationRequestBody;
import com.paytech.compliance.api.request.ClientReviewReturnRequestBody;
import com.paytech.compliance.api.request.ClientReviewRiskRatingRequestBody;
import com.paytech.compliance.api.request.ClientReviewScreeningRunRequestBody;
import com.paytech.compliance.api.request.ClientReviewWaiverRequestBody;
import com.paytech.compliance.api.response.ClientReviewCaseResponseBody;
import com.paytech.compliance.api.response.ClientReviewCommandResponseBody;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ClientReviewCctClient;
import com.paytech.operations.console.bff.integration.ClientReviewRiskClient;
import com.paytech.operations.console.bff.integration.EntityAdminClient;
import com.paytech.operations.console.bff.integration.PlatformReferenceDataClient;
import com.paytech.operations.console.bff.service.model.ClientReviewModels.ClassificationSetRequestBody;
import com.paytech.operations.console.bff.service.model.ClientReviewModels.ReviewCaseDetailResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.MccResponseBody;
import com.paytech.operations.console.bff.service.model.clientreview.response.ResponsiblePartnerListResponseBody;
import com.paytech.operations.console.bff.service.model.response.ClientReviewFileDownloadResponseBody;
import com.paytech.operations.console.bff.service.processor.ClientReviewCommandProcessor;
import com.paytech.operations.console.bff.service.processor.ClientReviewDetailsProcessor;
import com.paytech.operations.console.bff.service.processor.ClientReviewFileDownloadProcessor;
import com.paytech.operations.console.bff.service.processor.request.ClientReviewCommandRequest;
import com.paytech.operations.console.bff.service.processor.request.ClientReviewDetailsRequest;
import com.paytech.operations.console.bff.service.processor.request.ClientReviewFileDownloadRequest;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;

/**
 * O01—O05、O07—O10 与 CP-5 案件工作台；路由权限由 Gateway 路由码 + PLT-I02 统一授权（块 138 新增路由）
 * <li>O02 聚合 compliance 案件与 client-master 申请 / 分类，O10 核对案件证据后由 Platform 签发下载。</li>
 * <li>compliance 写命令使用其合同请求体；带 onBehalfOfPartnerId 时由 Processor 先核对 PayFac（S-05），其余为单 Owner 转发。</li>
 * <li>Owner 按案件状态防重，不转发 Idempotency-Key；超时后页面用原请求重试。</li>
 * <li>分类用的 MCC 搜索只列 Platform 中 ACTIVE 的 MCC，路由复用分类编辑权限（块 139）。</li>
 */
@RestController
@AllArgsConstructor
@RequestMapping("/client-onboarding-admin/v1")
public class ClientOnboardingReviewController {
    private static final String ID = "^[1-9][0-9]{0,18}$";
    private static final String CODE = "^[A-Z][A-Z0-9_]{0,63}$";

    private final ClientReviewCctClient cct;
    private final ClientReviewRiskClient risk;
    private final EntityAdminClient entityAdmin;
    private final ClientReviewCommandProcessor commands;
    private final ClientReviewDetailsProcessor details;
    private final ClientReviewFileDownloadProcessor fileDownload;
    private final PlatformReferenceDataClient referenceData;

    /** O01 案件列表，可按责任 PayFac（0 为平台自营）与辖区过滤。 */
    @GetMapping("/cases")
    public PagingResponseBody<ClientReviewCaseResponseBody> list(OperatorContext operator,
        @RequestParam(required = false) @Min(1) @Max(100000) Integer page, @RequestParam(required = false) @Min(1) @Max(100) Integer size,
        @RequestParam(required = false) @Pattern(regexp = CODE) String status,
        @RequestParam(required = false) @Pattern(regexp = CODE) String qualificationType,
        @RequestParam(required = false) @Pattern(regexp = ID) String applicationId,
        @RequestParam(required = false) @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$") String responsiblePartnerId,
        @RequestParam(required = false) @Pattern(regexp = "^[A-Z][A-Z0-9-]{1,15}$") String jurisdictionCode) {
        return risk.list(operator, page, size, status, qualificationType, applicationId, responsiblePartnerId, jurisdictionCode);
    }

    /** 责任方 / 代操作 PayFac 选项。 */
    @GetMapping("/responsible-partners")
    public ResponsiblePartnerListResponseBody responsiblePartners(OperatorContext operator) {
        return entityAdmin.responsiblePartners(operator);
    }

    /** O03 分类用的 MCC 搜索：编码前缀或英文名，只列 ACTIVE，按编码升序分页。 */
    @GetMapping("/mccs")
    public PagingResponseBody<MccResponseBody> searchMccs(@RequestParam(required = false) @Size(max = 64) String keyword,
        @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return referenceData.searchMccs(keyword == null ? null : keyword.trim(), ReferenceDataStatus.ACTIVE.getCode(),
            paging.page(), paging.size());
    }

    /** O02 案件详情。 */
    @GetMapping("/cases/{caseId}")
    public ReviewCaseDetailResponseBody detail(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String caseId) {
        return details.process(new ClientReviewDetailsRequest(operator, caseId));
    }

    /** O03 设置分类。 */
    @PutMapping("/cases/{caseId}/classifications")
    public ClassificationSetResponseBody replaceClassifications(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String caseId,
        @Valid @RequestBody ClassificationSetRequestBody request) {
        return cct.replace(operator, caseId, request);
    }

    /** O04 确认分类。 */
    @PostMapping("/cases/{caseId}/classification-confirmation")
    public ClassificationSetResponseBody confirmClassifications(OperatorContext operator,
        @PathVariable @Pattern(regexp = ID) String caseId) {
        return cct.confirm(operator, caseId);
    }

    /** O05 核验材料，拒绝带原因码。 */
    @PostMapping("/cases/{caseId}/evidence/{evidenceId}/review")
    public ClientReviewCommandResponseBody reviewEvidence(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String caseId,
        @PathVariable @Pattern(regexp = ID) String evidenceId, @Valid @RequestBody ClientReviewEvidenceReviewRequestBody request) {
        return command(operator, request.getOnBehalfOfPartnerId(), () -> risk.reviewEvidence(operator, caseId, evidenceId, request));
    }

    /** CP-5 豁免材料要求。 */
    @PostMapping("/cases/{caseId}/requirements/{requirementId}/waiver")
    public ClientReviewCommandResponseBody waiveRequirement(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String caseId,
        @PathVariable @Pattern(regexp = ID) String requirementId, @Valid @RequestBody ClientReviewWaiverRequestBody request) {
        return command(operator, request.getOnBehalfOfPartnerId(), () -> risk.waiveRequirement(operator, caseId, requirementId, request));
    }

    /** CP-5 录入人工检查结论。 */
    @PostMapping("/cases/{caseId}/checks/{checkId}/result")
    public ClientReviewCommandResponseBody recordCheckResult(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String caseId,
        @PathVariable @Pattern(regexp = ID) String checkId, @Valid @RequestBody ClientReviewCheckResultRequestBody request) {
        return command(operator, request.getOnBehalfOfPartnerId(), () -> risk.recordCheckResult(operator, caseId, checkId, request));
    }

    /** CP-5 重跑筛查。 */
    @PostMapping("/cases/{caseId}/screenings/run")
    public ClientReviewCommandResponseBody rerunScreenings(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String caseId,
        @Valid @RequestBody ClientReviewScreeningRunRequestBody request) {
        return command(operator, request.getOnBehalfOfPartnerId(), () -> risk.rerunScreenings(operator, caseId, request));
    }

    /** CP-5 处置筛查命中。 */
    @PostMapping("/cases/{caseId}/screening-hits/{hitId}/resolution")
    public ClientReviewCommandResponseBody resolveScreeningHit(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String caseId,
        @PathVariable @Pattern(regexp = ID) String hitId, @Valid @RequestBody ClientReviewHitResolutionRequestBody request) {
        return command(operator, request.getOnBehalfOfPartnerId(), () -> risk.resolveScreeningHit(operator, caseId, hitId, request));
    }

    /** CP-5 手工 CRR（整体替换评级与因子）。 */
    @PutMapping("/cases/{caseId}/risk-rating")
    public ClientReviewCommandResponseBody rateRisk(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String caseId,
        @Valid @RequestBody ClientReviewRiskRatingRequestBody request) {
        return command(operator, request.getOnBehalfOfPartnerId(), () -> risk.rateRisk(operator, caseId, request));
    }

    /** CP-5 推荐结论。 */
    @PostMapping("/cases/{caseId}/recommendation")
    public ClientReviewCommandResponseBody recommend(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String caseId,
        @Valid @RequestBody ClientReviewRecommendationRequestBody request) {
        return command(operator, request.getOnBehalfOfPartnerId(), () -> risk.recommend(operator, caseId, request));
    }

    /** O07 退回：需求级错误、期限与客户可见说明。 */
    @PostMapping("/cases/{caseId}/return")
    public ClientReviewCommandResponseBody returnCase(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String caseId,
        @Valid @RequestBody ClientReviewReturnRequestBody request) {
        return command(operator, request.getOnBehalfOfPartnerId(), () -> risk.returnCase(operator, caseId, request));
    }

    /** O08 通过；门禁与四眼由 compliance 判定（422 CLIENT_REVIEW_NOT_READY）。 */
    @PostMapping("/cases/{caseId}/approval")
    public ClientReviewCommandResponseBody approve(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String caseId,
        @Valid @RequestBody ClientReviewDecisionRequestBody request) {
        return command(operator, request.getOnBehalfOfPartnerId(), () -> risk.approve(operator, caseId, request));
    }

    /** O09 最终拒绝。 */
    @PostMapping("/cases/{caseId}/rejection")
    public ClientReviewCommandResponseBody reject(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String caseId,
        @Valid @RequestBody ClientReviewDecisionRequestBody request) {
        return command(operator, request.getOnBehalfOfPartnerId(), () -> risk.reject(operator, caseId, request));
    }

    /** O10 下载案件证据中的材料，返回最长 60 秒的原版本地址。 */
    @PostMapping("/cases/{caseId}/files/{fileId}/download")
    public ClientReviewFileDownloadResponseBody downloadFile(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String caseId,
        @PathVariable @Pattern(regexp = ID) String fileId) {
        return fileDownload.process(new ClientReviewFileDownloadRequest(operator, caseId, fileId));
    }

    /** compliance 写命令：代操作 PayFac 核对后单次调用。 */
    ClientReviewCommandResponseBody command(OperatorContext operator, String onBehalfOfPartnerId, Supplier<ClientReviewCommandResponseBody> call) {
        return commands.process(new ClientReviewCommandRequest(operator, onBehalfOfPartnerId, call));
    }
}
