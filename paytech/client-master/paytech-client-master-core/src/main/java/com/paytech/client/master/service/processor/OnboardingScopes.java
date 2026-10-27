package com.paytech.client.master.service.processor;

import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.onboarding.ProgramScope;
import com.paytech.client.master.domain.onboarding.pack.OnboardingPack;

/** 由申请冻结的辖区包与责任方构造 compliance 程序解析范围。 */
final class OnboardingScopes {
    /** 仅静态步骤。 */
    private OnboardingScopes() {
    }

    /**
     * 程序解析范围
     *
     * @param pack 申请冻结的辖区包
     * @param partyType 申请主主体类型
     * @param responsiblePartnerId 责任 PayFac，平台自营为空
     * @return 组织申请同时解析关联人员程序
     */
    static ProgramScope scope(OnboardingPack pack, ClientType partyType, Long responsiblePartnerId) {
        return new ProgramScope(pack.jurisdictionCode(), partyType, pack.tier(), responsiblePartnerId,
            partyType == ClientType.ORGANIZATION);
    }
}
