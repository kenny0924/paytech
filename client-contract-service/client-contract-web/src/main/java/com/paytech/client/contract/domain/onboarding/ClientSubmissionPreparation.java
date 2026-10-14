package com.paytech.client.contract.domain.onboarding;

import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.ClientApplicationDocument;
import com.paytech.client.contract.core.dal.dataobject.ClientPartyRelation;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次提交检查的结果：受审主体、范围内关系、材料引用及逐项问题。 */
@Getter
@AllArgsConstructor
public class ClientSubmissionPreparation {
    /** 受审主体，第一项为主主体。 */
    private final List<ClientSubmissionSubject> subjects;
    /** 提交范围内的关系。 */
    private final List<ClientPartyRelation> relations;
    /** 材料引用。 */
    private final List<ClientApplicationDocument> documents;
    /** 逐项问题；为空表示可以提交。 */
    private final List<ProblemFieldError> issues;
    /** 是否存在证件号已被其他主体使用的问题。 */
    private final boolean identityConflict;

    /** 是否可以提交。 */
    public boolean ready() {
        return issues.isEmpty();
    }
}
