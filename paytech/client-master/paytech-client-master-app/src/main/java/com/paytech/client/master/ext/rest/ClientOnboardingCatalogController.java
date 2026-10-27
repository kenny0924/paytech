package com.paytech.client.master.ext.rest;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.APPLICATION_NOT_FOUND;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.CLIENT_SESSION_INVALID;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.master.api.onboarding.ClientOnboardingCatalogApi;
import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody;
import com.paytech.client.master.api.onboarding.response.JurisdictionResponseBody;
import com.paytech.client.master.domain.onboarding.ClientDraftCommand;
import com.paytech.client.master.service.processor.GetOnboardingFormSchemaProcessor;
import com.paytech.client.master.service.processor.GetOnboardingJurisdictionsProcessor;
import com.paytech.client.master.service.processor.request.ClientSessionRequest;
import com.paytech.client.master.service.processor.request.GetFormSchemaRequest;
import com.paytech.foundation.common.error.FailException;

import lombok.AllArgsConstructor;

/** CM-1 / CM-2 私网协议适配；只允许 client-console-bff。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(prefix = "onboarding", name = "draft-enabled", havingValue = "true")
public class ClientOnboardingCatalogController implements ClientOnboardingCatalogApi {
    private final GetOnboardingJurisdictionsProcessor jurisdictions;
    private final GetOnboardingFormSchemaProcessor formSchemas;

    /** 可入驻辖区。 */
    @Override
    public List<JurisdictionResponseBody> jurisdictions(String caller, String accountId, String sessionId) {
        caller(caller);
        return jurisdictions.process(new ClientSessionRequest(session(accountId), session(sessionId)));
    }

    /** 表单渲染规格。 */
    @Override
    public FormSchemaResponseBody formSchema(String caller, String accountId, String sessionId, String applicationId,
        String jurisdiction, String partyType, String organizationType) {
        caller(caller);
        return formSchemas.process(new GetFormSchemaRequest(session(accountId), session(sessionId),
            applicationId == null ? null : ClientDraftCommand.id(applicationId), blank(jurisdiction), blank(partyType),
            blank(organizationType)));
    }

    /** Caller 仅防误调用。 */
    static void caller(String caller) {
        if (!"client-console-bff".equals(caller)) {
            throw new FailException(APPLICATION_NOT_FOUND);
        }
    }

    /** 非法身份坐标与无效会话统一响应。 */
    static long session(String value) {
        try {
            return ClientDraftCommand.id(value);
        } catch (FailException invalid) {
            throw new FailException(CLIENT_SESSION_INVALID);
        }
    }

    /** 空白参数视为未提供。 */
    static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
