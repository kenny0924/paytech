package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.ClientReviewRiskClientImpl.require;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.client.master.api.onboardingadmin.ClientOnboardingAdminApi;
import com.paytech.client.master.api.onboardingadmin.request.IssueOnboardingCodeRequestBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminCatalogItemResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminClientDetailResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminClientSummaryResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminJurisdictionResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminOnboardingSchemaResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.OnboardingAdminClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.onboardingcode.response.IssuedOnboardingCodeResponseBody;
import com.paytech.operations.console.bff.service.model.onboardingcode.response.OnboardingCodeListResponseBody;
import com.paytech.operations.console.bff.service.model.onboardingcode.response.OnboardingCodeResponseBody;

import lombok.AllArgsConstructor;

/**
 * 调用 client-master `ClientOnboardingAdminApi`；结果必须对应请求的辖区、目录、Client、Partner 与入驻码，否则视为上游响应无效
 * <li>入驻码列表与作废结果只能带掩码（code 为 null）；明文只在签发响应中出现一次，出现在其他响应视为上游响应无效。</li>
 * <li>目录项与入驻码的辖区 / 状态过滤在 BFF 完成（client-master 不支持这两个参数）。</li>
 */
@Component
@AllArgsConstructor
public class OnboardingAdminClientImpl implements OnboardingAdminClient {
    /** 明文入驻码格式。 */
    static final Pattern PLAIN_CODE = Pattern.compile("^[A-Z0-9]{4}-[A-Z0-9]{4}$");
    /** 入驻码掩码格式 ****-XXXX。 */
    static final Pattern MASKED_CODE = Pattern.compile("^\\*{4}-[A-Z0-9]{4}$");
    /** client-master 目录项的全局辖区标记。 */
    static final String GLOBAL_JURISDICTION = "*";

    private final ClientOnboardingAdminApi api;

    @Override
    public List<AdminJurisdictionResponseBody> jurisdictions(OperatorContext o) {
        var response = api.jurisdictions(o.accountId(), o.sessionId());
        require(response != null && response.stream().allMatch(item -> item != null && item.getJurisdictionCode() != null));
        return response;
    }

    @Override
    public List<AdminOnboardingSchemaResponseBody> schemas(OperatorContext o, String jurisdiction) {
        var response = api.onboardingSchemas(o.accountId(), o.sessionId(), jurisdiction);
        require(response != null && response.stream().allMatch(schema -> schema != null && jurisdiction.equals(schema.getJurisdictionCode())));
        return response;
    }

    @Override
    public List<AdminCatalogItemResponseBody> catalogItems(OperatorContext o, String catalogCode, String jurisdiction) {
        var response = api.catalogItems(o.accountId(), o.sessionId(), catalogCode);
        require(response != null && response.stream().allMatch(item -> item != null && catalogCode.equals(item.getCatalogCode())));
        return response.stream()
            .filter(item -> jurisdiction == null || item.getJurisdictionCode() == null || GLOBAL_JURISDICTION.equals(item.getJurisdictionCode())
                || jurisdiction.equals(item.getJurisdictionCode())).toList();
    }

    @Override
    public PagingResponseBody<AdminClientSummaryResponseBody> clients(OperatorContext o, Integer page, Integer size, String status,
        String jurisdiction, String responsiblePartnerId) {
        var response = api.clients(o.accountId(), o.sessionId(), status, jurisdiction, responsiblePartnerId, page, size);
        require(response != null && response.items() != null);
        return response;
    }

    @Override
    public AdminClientDetailResponseBody client(OperatorContext o, String clientId) {
        var response = api.client(o.accountId(), o.sessionId(), clientId);
        require(response != null && response.getClient() != null && clientId.equals(response.getClient().getClientId())
            && response.getClient().getStatus() != null);
        return response;
    }

    @Override
    public OnboardingCodeListResponseBody onboardingCodes(OperatorContext o, String localPartnerId, String status) {
        var response = api.onboardingCodes(o.accountId(), o.sessionId(), localPartnerId);
        require(response != null);
        return new OnboardingCodeListResponseBody(response.stream().map(code -> masked(localPartnerId, code, false))
            .filter(code -> status == null || status.equals(code.getStatus())).toList());
    }

    @Override
    public IssuedOnboardingCodeResponseBody issueOnboardingCode(OperatorContext o, String localPartnerId, IssueOnboardingCodeRequestBody request) {
        var response = api.issueOnboardingCode(o.accountId(), o.sessionId(), localPartnerId, request);
        var body = response == null ? null : response.getBody();
        require(body != null);
        return new IssuedOnboardingCodeResponseBody(masked(localPartnerId, body, true), body.getCode());
    }

    @Override
    public OnboardingCodeResponseBody revokeOnboardingCode(OperatorContext o, String localPartnerId, String codeId) {
        var response = masked(localPartnerId, api.revokeOnboardingCode(o.accountId(), o.sessionId(), localPartnerId, codeId), false);
        require(codeId.equals(response.getCodeId()));
        return response;
    }

    /**
     * 入驻码必须属于该 Partner 且带 ****-XXXX 掩码；输出只含掩码
     *
     * @param localPartnerId 请求的 Partner
     * @param code client-master 入驻码
     * @param issued 是否签发响应：签发时必须带 XXXX-XXXX 明文，其他响应明文必须为 null
     * @return 掩码后的页面模型
     */
    static OnboardingCodeResponseBody masked(String localPartnerId,
        com.paytech.client.master.api.onboardingadmin.response.OnboardingCodeResponseBody code, boolean issued) {
        require(code != null && code.getCodeId() != null && localPartnerId.equals(code.getLocalPartnerId()) && code.getStatus() != null
            && code.getCodeMasked() != null && MASKED_CODE.matcher(code.getCodeMasked()).matches()
            && (issued ? code.getCode() != null && PLAIN_CODE.matcher(code.getCode()).matches() : code.getCode() == null));
        return new OnboardingCodeResponseBody(code.getCodeId(), code.getLocalPartnerId(), code.getCodeMasked(), code.getStatus(),
            code.getEffectiveFrom(), code.getEffectiveTo(), code.getMaxUses(), code.getUseCount(), code.getNote(), code.getIssuedBy(),
            code.getIssuedAt(), code.getRevokedBy(), code.getRevokedAt());
    }
}
