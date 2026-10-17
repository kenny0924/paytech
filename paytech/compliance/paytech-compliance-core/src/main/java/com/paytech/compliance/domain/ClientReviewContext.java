package com.paytech.compliance.domain;

import java.util.List;

import com.paytech.compliance.core.dal.dataobject.CaseDependency;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次 Client 入驻审核登记：主案件、关联人员子案件及依赖，检查完成后一次构造。 */
@Getter
@AllArgsConstructor
public class ClientReviewContext {
    /** 主案件。 */
    private final ClientReviewCaseBundle mainCase;
    /** 关联人员子案件。 */
    private final List<ClientReviewCaseBundle> childCases;
    /** 主案件对子案件的依赖。 */
    private final List<CaseDependency> dependencies;
}
