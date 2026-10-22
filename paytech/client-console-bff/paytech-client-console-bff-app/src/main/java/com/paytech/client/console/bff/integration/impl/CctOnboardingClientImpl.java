package com.paytech.client.console.bff.integration.impl;

import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.integration.CallerServiceCodes;
import com.paytech.client.console.bff.integration.CctOnboardingClient;
import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.client.contract.api.enums.ClientFormCatalogStatus;
import com.paytech.client.contract.api.onboarding.ClientDraftApi;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.ApplicationSummary;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.ClientDraftResponseBody;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.StartResponseBody;
import com.paytech.client.contract.api.onboarding.ClientFormOptionsApi;
import com.paytech.client.contract.api.onboarding.ClientOnboardingStartApi;
import com.paytech.client.contract.api.onboarding.response.ClientFormOptionsResponseBody;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.files.FileAddress;

import lombok.AllArgsConstructor;

/**
 * 调用 CCT 入驻只读接口并校验回执，限制 BFF 的聚合工作量
 * <li>任何身份不一致、状态无法识别或规模超限都按依赖不可用，不向页面返回部分事实。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class CctOnboardingClientImpl implements CctOnboardingClient {
    /** 客户类型的合同值。 */
    static final Set<String> CLIENT_TYPES = Set.of("INDIVIDUAL", "ORGANIZATION");

    /** 申请导航最多条数。 */
    static final int MAX_APPLICATIONS = 20;

    /** 目录每类选项最多条数。 */
    static final int MAX_CATALOG_OPTIONS = 200;

    /** 草稿人员最多条数。 */
    static final int MAX_PEOPLE = 100;

    /** 草稿材料最多条数。 */
    static final int MAX_DOCUMENTS = 500;

    private static final String ID = "[1-9][0-9]{0,18}";

    private final ClientDraftApi drafts;
    private final ClientFormOptionsApi catalogs;
    private final ClientOnboardingStartApi applications;

    /** 读取申请导航并校验条数与未完成申请唯一。 */
    @Override
    public StartResponseBody start(ClientActor actor) {
        var result = applications.start(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(), actor.getSessionId());
        if (result == null || result.applications() == null || result.applications().size() > MAX_APPLICATIONS) {
            throw new ServiceUnavailableException();
        }
        if (result.applications().stream().filter(ApplicationSummary::active).count() > 1) {
            throw new ServiceUnavailableException();
        }
        return result;
    }

    /** 读取草稿并校验身份与规模。 */
    @Override
    public ClientDraftResponseBody draft(ClientActor actor, String applicationId) {
        var draft = drafts.read(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(), actor.getSessionId(), applicationId);
        if (!validDraft(draft, applicationId)) {
            throw new ServiceUnavailableException();
        }
        return draft;
    }

    /** 读取受控目录并校验类型、状态与完整性。 */
    @Override
    public ClientFormOptionsResponseBody formOptions(ClientActor actor, String clientType) {
        var value = catalogs.options(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(), actor.getSessionId(), clientType);
        if (value == null || !clientType.equals(value.getClientType()) || !boundedCatalog(value)) {
            throw new ServiceUnavailableException();
        }
        requireKnownCatalogStatus(value.getCatalogStatus());
        if (ClientFormCatalogStatus.AVAILABLE.getCode().equals(value.getCatalogStatus()) && !completeCatalog(value, clientType)) {
            throw new ServiceUnavailableException();
        }
        if (value.getIdentifierTypes().stream()
            .anyMatch(identifier -> identifier == null || !Set.of("REQUIRED", "NONE").contains(identifier.getIssuingCountryMode()))) {
            throw new ServiceUnavailableException();
        }
        return value;
    }

    /** 申请 ID 一致、主主体为规范 ID、客户类型有效，且人员与材料规模有界。 */
    static boolean validDraft(ClientDraftResponseBody draft, String applicationId) {
        if (draft == null || !applicationId.equals(draft.applicationId()) || draft.subjectPartyId() == null
            || !draft.subjectPartyId().matches(ID) || !CLIENT_TYPES.contains(draft.clientType())) {
            return false;
        }
        if (draft.people() == null || draft.relations() == null || draft.documents() == null || draft.values() == null) {
            return false;
        }
        return draft.people().size() <= MAX_PEOPLE && draft.documents().size() <= MAX_DOCUMENTS
            && draft.documents().stream().allMatch(document -> document != null && validFileAddress(document.fileAddress()));
    }

    /** 材料保存的文件地址必须符合文件服务签发的格式。 */
    static boolean validFileAddress(String fileAddress) {
        try {
            FileAddress.parse(fileAddress);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /** 三类选项列表都存在且不超过上限。 */
    static boolean boundedCatalog(ClientFormOptionsResponseBody value) {
        if (value.getOrganizationTypes() == null || value.getBusinessModels() == null || value.getIdentifierTypes() == null) {
            return false;
        }
        return value.getOrganizationTypes().size() <= MAX_CATALOG_OPTIONS && value.getBusinessModels().size() <= MAX_CATALOG_OPTIONS
            && value.getIdentifierTypes().size() <= MAX_CATALOG_OPTIONS;
    }

    /** 可用目录必须有经营模式与证件类型；组织客户还必须有组织类型。 */
    static boolean completeCatalog(ClientFormOptionsResponseBody value, String clientType) {
        return !value.getBusinessModels().isEmpty() && !value.getIdentifierTypes().isEmpty()
            && !("ORGANIZATION".equals(clientType) && value.getOrganizationTypes().isEmpty());
    }

    /** 目录状态必须是 CCT 已发布代码。 */
    static void requireKnownCatalogStatus(String status) {
        try {
            ClientFormCatalogStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new ServiceUnavailableException();
        }
    }
}
