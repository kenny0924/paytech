package com.paytech.client.master.service.processor;

import java.util.ArrayList;
import java.util.List;

import com.paytech.client.master.api.onboarding.response.JurisdictionResponseBody;
import com.paytech.client.master.core.dal.dataobject.Jurisdiction;
import com.paytech.client.master.domain.enums.AcceptancePolicy;
import com.paytech.client.master.domain.enums.RuleCode;
import com.paytech.client.master.domain.onboarding.pack.OnboardingPack;
import com.paytech.client.master.domain.onboarding.pack.RuleSettings;
import com.paytech.client.master.domainservice.service.OnboardingPackService;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.service.processor.request.ClientSessionRequest;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** CM-1 可入驻辖区：ACTIVE 且准入策略为 OPEN 或 ENHANCED_ONLY；会话在事务外核验，只读本库。 */
@AllArgsConstructor
public class GetOnboardingJurisdictionsProcessor extends AbstractProcessor<ClientSessionRequest, List<JurisdictionResponseBody>> {
    private final OnboardingPackService packs;
    private final ClientDraftAccountClient accounts;

    /** 逐个辖区按规则开关解析准入策略。 */
    @Override
    protected List<JurisdictionResponseBody> processRequest(ClientSessionRequest request) {
        accounts.authorize(request.getAccountId(), request.getSessionId());
        List<JurisdictionResponseBody> result = new ArrayList<>();
        for (Jurisdiction jurisdiction : packs.jurisdictions()) {
            if (!OnboardingPack.active(jurisdiction.getStatus())) {
                continue;
            }
            RuleSettings rules = packs.rules(jurisdiction.getJurisdictionCode());
            AcceptancePolicy policy = AcceptancePolicy.fromCode(rules.value(RuleCode.ACCEPTANCE_POLICY, jurisdiction.getJurisdictionCode()));
            if (policy == null || policy == AcceptancePolicy.CLOSED) {
                continue;
            }
            var body = new JurisdictionResponseBody();
            body.setJurisdictionCode(jurisdiction.getJurisdictionCode());
            body.setCountryCode(jurisdiction.getCountryCode());
            body.setLabelKey(jurisdiction.getLabelKey());
            body.setAcceptancePolicy(policy.getCode());
            body.setAcceptanceReasonKey(rules.value(RuleCode.ACCEPTANCE_REASON_KEY, jurisdiction.getJurisdictionCode()));
            result.add(body);
        }
        return result;
    }
}
