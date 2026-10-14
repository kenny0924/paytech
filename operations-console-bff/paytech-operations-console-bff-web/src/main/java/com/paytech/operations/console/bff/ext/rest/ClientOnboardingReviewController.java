package com.paytech.operations.console.bff.ext.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.contract.api.onboarding.response.ClassificationSetResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ClientReviewCctClient;
import com.paytech.operations.console.bff.integration.ClientReviewRiskClient;
import com.paytech.operations.console.bff.service.ClientReviewDetailsProcessor;
import com.paytech.operations.console.bff.service.ClientReviewFileProcessor;
import com.paytech.operations.console.bff.service.model.ClientReviewModels.ClassificationSetRequestBody;
import com.paytech.operations.console.bff.service.model.ClientReviewModels.DecisionRequestBody;
import com.paytech.operations.console.bff.service.model.ClientReviewModels.EvidenceReviewRequestBody;
import com.paytech.operations.console.bff.service.model.ClientReviewModels.ReturnRequestBody;
import com.paytech.operations.console.bff.service.model.ClientReviewModels.ReviewCaseDetailResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.platform.support.api.reviewfiles.response.ReviewFileDownloadResponseBody;
import com.paytech.risk.api.compliance.response.ClientReviewCaseResponseBody;
import com.paytech.risk.api.compliance.response.ClientReviewCommandResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * O01—O05、O07—O10 Client 入驻审核；路由权限由 Gateway 路由码 + PLT-I02 统一授权
 * <li>O02 聚合 Risk 任务与 CCT 申请/分类，O10 核对任务证据后由 Platform 签发下载，其余为单 Owner 转发。</li>
 * <li>Owner 按任务状态防重，不转发 Idempotency-Key；超时后页面用原请求重试。</li>
 */
@RestController
@AllArgsConstructor
@RequestMapping("/client-onboarding-admin/v1")
public class ClientOnboardingReviewController {
    private static final String ID = "^[1-9][0-9]{0,18}$";
    private static final String CODE = "^[A-Z][A-Z0-9_]{0,63}$";

    private final ClientReviewRiskClient risk;
    private final ClientReviewCctClient cct;
    private final ClientReviewDetailsProcessor details;
    private final ClientReviewFileProcessor reviewFiles;

    /** O01 审核列表。 */
    @GetMapping("/cases")
    public PagingResponseBody<ClientReviewCaseResponseBody> list(OperatorContext operator,
        @RequestParam(required = false) @Min(1) @Max(100000) Integer page, @RequestParam(required = false) @Min(1) @Max(100) Integer size,
        @RequestParam(required = false) @Pattern(regexp = CODE) String status,
        @RequestParam(required = false) @Pattern(regexp = CODE) String qualificationType,
        @RequestParam(required = false) @Pattern(regexp = ID) String applicationId) {
        return risk.list(operator, page, size, status, qualificationType, applicationId);
    }

    /** O02 审核详情。 */
    @GetMapping("/cases/{caseId}")
    public ReviewCaseDetailResponseBody detail(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String caseId) {
        return details.details(operator, caseId);
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

    /** O05 核验材料。 */
    @PostMapping("/cases/{caseId}/evidence/{evidenceId}/review")
    public ClientReviewCommandResponseBody reviewEvidence(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String caseId,
        @PathVariable @Pattern(regexp = ID) String evidenceId, @Valid @RequestBody EvidenceReviewRequestBody request) {
        return risk.reviewEvidence(operator, caseId, evidenceId, request);
    }

    /** O07 退回修改。 */
    @PostMapping("/cases/{caseId}/return")
    public ClientReviewCommandResponseBody returnCase(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String caseId,
        @Valid @RequestBody ReturnRequestBody request) {
        return risk.returnCase(operator, caseId, request);
    }

    /** O08 通过。 */
    @PostMapping("/cases/{caseId}/approval")
    public ClientReviewCommandResponseBody approve(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String caseId,
        @Valid @RequestBody DecisionRequestBody request) {
        return risk.approve(operator, caseId, request);
    }

    /** O09 最终拒绝。 */
    @PostMapping("/cases/{caseId}/rejection")
    public ClientReviewCommandResponseBody reject(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String caseId,
        @Valid @RequestBody DecisionRequestBody request) {
        return risk.reject(operator, caseId, request);
    }

    /** O10 下载任务证据中的材料，返回最长 60 秒的原版本地址。 */
    @PostMapping("/cases/{caseId}/files/{fileId}/download")
    public ReviewFileDownloadResponseBody downloadFile(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String caseId,
        @PathVariable @Pattern(regexp = ID) String fileId) {
        return reviewFiles.download(operator, caseId, fileId);
    }
}
