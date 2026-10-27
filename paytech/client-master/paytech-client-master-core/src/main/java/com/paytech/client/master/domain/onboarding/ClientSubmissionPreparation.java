package com.paytech.client.master.domain.onboarding;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.ApplicationDeclaration;
import com.paytech.client.master.core.dal.dataobject.ClientApplicationDocument;
import com.paytech.client.master.core.dal.dataobject.ClientPartyRelation;
import com.paytech.client.master.domain.onboarding.requirement.OnboardingRequirement;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次提交检查的结果：受审主体、范围内关系、材料、声明与需求清单。 */
@Getter
@AllArgsConstructor
public class ClientSubmissionPreparation {
    /** 受审主体，第一项为主主体。 */
    private final List<ClientSubmissionSubject> subjects;
    /** 提交范围内的关系。 */
    private final List<ClientPartyRelation> relations;
    /** 材料引用。 */
    private final List<ClientApplicationDocument> documents;
    /** 申请的全部声明（只追加，同类型以最后一行为准）。 */
    private final List<ApplicationDeclaration> declarations;
    /** 需求清单；阻止提交的项见 {@link #errors()}。 */
    private final List<OnboardingRequirement> requirements;

    /** 阻止提交的需求项。 */
    public List<OnboardingRequirement> errors() {
        return requirements.stream().filter(OnboardingRequirement::blocksSubmission).toList();
    }

    /** 是否可以提交。 */
    public boolean ready() {
        return errors().isEmpty();
    }

    /** 是否存在证件号已被其他主体使用的问题。 */
    public boolean isIdentityConflict() {
        return subjects.stream().anyMatch(ClientSubmissionSubject::identityConflict);
    }
}
