package com.paytech.client.console.bff.integration.impl;

import java.util.List;
import java.util.Set;


import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.integration.CallerServiceCodes;
import com.paytech.client.console.bff.integration.CctOnboardingClient;
import com.paytech.client.master.api.onboarding.ClientOnboardingApplicationApi;
import com.paytech.client.master.api.onboarding.response.RequirementItemResponseBody;
import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.client.contract.api.onboarding.ClientDraftApi;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.ApplicationSummary;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.ClientDraftResponseBody;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.StartResponseBody;
import com.paytech.client.contract.api.onboarding.ClientOnboardingStartApi;
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

    /** 草稿人员最多条数。 */
    static final int MAX_PEOPLE = 100;

    /** 需求清单最多条数。 */
    static final int MAX_REQUIREMENTS = 1000;

    /** 草稿材料最多条数。 */
    static final int MAX_DOCUMENTS = 500;

    private static final String ID = "[1-9][0-9]{0,18}";

    private final ClientDraftApi drafts;
    private final ClientOnboardingStartApi applications;
    private final ClientOnboardingApplicationApi onboardingApplications;

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

    /** 读取需求清单并校验条数与每项的需求键、状态。 */
    @Override
    public List<RequirementItemResponseBody> requirements(ClientActor actor, String applicationId) {
        var value = onboardingApplications.requirements(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(),
            actor.getSessionId(), applicationId);
        if (value == null || value.getItems() == null || value.getItems().size() > MAX_REQUIREMENTS
            || !applicationId.equals(value.getApplicationId())
            || value.getItems().stream().anyMatch(item -> item == null || item.getRequirementKey() == null || item.getStatus() == null)) {
            throw new ServiceUnavailableException();
        }
        return value.getItems();
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
}
