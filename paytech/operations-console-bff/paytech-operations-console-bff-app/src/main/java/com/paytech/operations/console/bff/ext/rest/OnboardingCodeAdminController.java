package com.paytech.operations.console.bff.ext.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.operations.console.bff.integration.EntityAdminClient;
import com.paytech.operations.console.bff.integration.OnboardingAdminClient;
import com.paytech.client.master.api.onboardingadmin.request.IssueOnboardingCodeRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.clientreview.response.ResponsiblePartnerListResponseBody;
import com.paytech.operations.console.bff.service.model.onboardingcode.response.IssuedOnboardingCodeResponseBody;
import com.paytech.operations.console.bff.service.model.onboardingcode.response.OnboardingCodeListResponseBody;
import com.paytech.operations.console.bff.service.model.onboardingcode.response.OnboardingCodeResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * CM-12 入驻码：PayFac 选项、列表（ONBOARDING_CODE_READ）与签发 / 作废（ONBOARDING_CODE_MANAGE），client-master 单 Owner 转发
 * <li>只能为 OE_PROVIDER Partner 签发，由 client-master 判定（422 PARTNER_NOT_OE_PROVIDER）。</li>
 * <li>列表与作废只含掩码；签发响应含明文码且只出现一次，/onboarding-code-admin 只记元数据日志；不转发 Idempotency-Key。</li>
 * <li>作废不收请求体（client-master 合同不记原因）。</li>
 */
@RestController
@AllArgsConstructor
@RequestMapping("/onboarding-code-admin/v1")
public class OnboardingCodeAdminController {
    private static final String ID = "^[1-9][0-9]{0,18}$";

    private final EntityAdminClient entityAdmin;
    private final OnboardingAdminClient onboardingAdmin;

    /** 可签发入驻码的 PayFac（ACTIVE + OE_PROVIDER）。 */
    @GetMapping("/local-partners")
    public ResponsiblePartnerListResponseBody partners(OperatorContext operator) {
        return entityAdmin.responsiblePartners(operator);
    }

    /** 某 PayFac 的入驻码（掩码）。 */
    @GetMapping("/local-partners/{localPartnerId}/onboarding-codes")
    public OnboardingCodeListResponseBody codes(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String localPartnerId,
        @RequestParam(required = false) @Pattern(regexp = "^(ACTIVE|REVOKED|EXPIRED)$") String status) {
        return onboardingAdmin.onboardingCodes(operator, localPartnerId, status);
    }

    /** 签发入驻码（client-master 返回 201，BFF 以 200 返回掩码 + 一次性明文）。 */
    @PostMapping("/local-partners/{localPartnerId}/onboarding-codes")
    public IssuedOnboardingCodeResponseBody issue(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String localPartnerId,
        @Valid @RequestBody IssueOnboardingCodeRequestBody request) {
        return onboardingAdmin.issueOnboardingCode(operator, localPartnerId, request);
    }

    /** 作废入驻码。 */
    @PostMapping("/local-partners/{localPartnerId}/onboarding-codes/{codeId}/revocation")
    public OnboardingCodeResponseBody revoke(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String localPartnerId,
        @PathVariable @Pattern(regexp = ID) String codeId) {
        return onboardingAdmin.revokeOnboardingCode(operator, localPartnerId, codeId);
    }
}
