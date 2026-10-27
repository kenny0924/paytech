package com.paytech.client.master.ext.rest;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.master.api.enums.EntityAdminErrorCode;
import com.paytech.client.master.api.onboardingadmin.ClientOnboardingAdminApi;
import com.paytech.client.master.api.onboardingadmin.request.IssueOnboardingCodeRequestBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminCatalogItemResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminClientDetailResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminClientSummaryResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminJurisdictionResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminOnboardingSchemaResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.OnboardingCodeResponseBody;
import com.paytech.client.master.domainservice.service.OnboardingAdminQueryService;
import com.paytech.client.master.service.processor.GetClientAdminDetailProcessor;
import com.paytech.client.master.service.processor.IssueOnboardingCodeProcessor;
import com.paytech.client.master.service.processor.RevokeOnboardingCodeProcessor;
import com.paytech.client.master.service.processor.request.IssueOnboardingCodeRequest;
import com.paytech.client.master.service.processor.request.RevokeOnboardingCodeRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/**
 * CM-10 / CM-11 / CM-12 运营协议适配；操作者来自 Gateway 可信头，只校验存在并记录
 * <li>与入驻草稿、补偿开关同时开启时注册。</li>
 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(prefix = "onboarding", name = {"draft-enabled", "recovery-enabled"}, havingValue = "true")
public class ClientOnboardingAdminController implements ClientOnboardingAdminApi {
    private final OnboardingAdminQueryService queries;
    private final GetClientAdminDetailProcessor details;
    private final IssueOnboardingCodeProcessor issues;
    private final RevokeOnboardingCodeProcessor revocations;

    /** 辖区包。 */
    @Override
    public List<AdminJurisdictionResponseBody> jurisdictions(String actorAccountId, String sessionId) {
        ClientClassificationAdminController.actor(actorAccountId, sessionId);
        return queries.jurisdictions().stream().map(OnboardingAdminConverter::jurisdiction).toList();
    }

    /** Schema 版本。 */
    @Override
    public List<AdminOnboardingSchemaResponseBody> onboardingSchemas(String actorAccountId, String sessionId, String jurisdiction) {
        ClientClassificationAdminController.actor(actorAccountId, sessionId);
        return queries.schemas(code(jurisdiction, "jurisdiction")).stream().map(OnboardingAdminConverter::schema).toList();
    }

    /** 目录项。 */
    @Override
    public List<AdminCatalogItemResponseBody> catalogItems(String actorAccountId, String sessionId, String catalogCode) {
        ClientClassificationAdminController.actor(actorAccountId, sessionId);
        return queries.catalogItems(code(catalogCode, "catalogCode")).stream().map(OnboardingAdminConverter::catalogItem).toList();
    }

    /** Client 分页。 */
    @Override
    public PagingResponseBody<AdminClientSummaryResponseBody> clients(String actorAccountId, String sessionId, String status,
        String jurisdiction, String responsiblePartnerId, Integer page, Integer size) {
        ClientClassificationAdminController.actor(actorAccountId, sessionId);
        Long partner = responsiblePartnerId == null || responsiblePartnerId.isBlank() ? null
            : ClientClassificationAdminController.id(responsiblePartnerId);
        return queries.clients(optional(status), optional(jurisdiction), partner, EntityAdminConverter.paging(page, size))
            .map(OnboardingAdminConverter::client);
    }

    /** Client 详情。 */
    @Override
    public AdminClientDetailResponseBody client(String actorAccountId, String sessionId, String clientId) {
        ClientClassificationAdminController.actor(actorAccountId, sessionId);
        return OnboardingAdminConverter.detail(details.process(ClientClassificationAdminController.id(clientId)));
    }

    /** 入驻码列表。 */
    @Override
    public List<OnboardingCodeResponseBody> onboardingCodes(String actorAccountId, String sessionId, String localPartnerId) {
        ClientClassificationAdminController.actor(actorAccountId, sessionId);
        return queries.onboardingCodes(partner(localPartnerId)).stream().map(OnboardingAdminConverter::code).toList();
    }

    /** 签发。 */
    @Override
    public ResponseEntity<OnboardingCodeResponseBody> issueOnboardingCode(String actorAccountId, String sessionId, String localPartnerId,
        IssueOnboardingCodeRequestBody body) {
        long actor = ClientClassificationAdminController.actor(actorAccountId, sessionId);
        var code = issues.process(new IssueOnboardingCodeRequest(actor, partner(localPartnerId), instant(body.getEffectiveFrom()),
            instant(body.getEffectiveTo()), body.getMaxUses(), optional(body.getNote())));
        return ResponseEntity.status(201).body(OnboardingAdminConverter.issuedCode(code));
    }

    /** 作废。 */
    @Override
    public OnboardingCodeResponseBody revokeOnboardingCode(String actorAccountId, String sessionId, String localPartnerId,
        String codeId) {
        long actor = ClientClassificationAdminController.actor(actorAccountId, sessionId);
        return OnboardingAdminConverter.code(revocations.process(new RevokeOnboardingCodeRequest(actor, partner(localPartnerId),
            ClientClassificationAdminController.id(codeId))));
    }

    /** Local Partner 路径参数；非法按不存在处理。 */
    static long partner(String value) {
        try {
            return ClientClassificationAdminController.id(value);
        } catch (FailException invalid) {
            throw new FailException(EntityAdminErrorCode.LOCAL_PARTNER_NOT_FOUND);
        }
    }

    /** 必填代码参数。 */
    static String code(String value, String name) {
        if (value == null || !value.strip().matches("[A-Z0-9*][A-Z0-9_*-]{0,63}")) {
            throw new InvalidParameterException(name + " 格式不正确");
        }
        return value.strip();
    }

    /** 空白视为未提供。 */
    static String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** 可空 ISO-8601 UTC 时间。 */
    static Instant instant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.strip());
        } catch (DateTimeParseException invalid) {
            throw new InvalidParameterException("时间须为 ISO-8601 UTC 格式");
        }
    }
}
