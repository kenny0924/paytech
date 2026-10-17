package com.paytech.risk.compliance.domain;

import java.util.List;

import com.paytech.risk.compliance.core.dal.dataobject.CaseClassification;
import com.paytech.risk.compliance.core.dal.dataobject.CaseDependency;
import com.paytech.risk.compliance.core.dal.dataobject.CaseSubject;
import com.paytech.risk.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.risk.compliance.core.dal.dataobject.EvidenceRef;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次 Client 入驻提交的审核全貌：主案件、关联人员子案件、主体、要求、证据、依赖与已接收分类。
 * <li>多表组合查询结果，只承载本次查询与门禁判断所需数据。</li>
 */
@Getter
@AllArgsConstructor
public class ClientReviewFamily {
    /** 主案件。 */
    private final DueDiligenceCase mainCase;
    /** 关联人员子案件。 */
    private final List<DueDiligenceCase> childCases;
    /** 主案件对子案件的依赖。 */
    private final List<CaseDependency> dependencies;
    /** 全部案件的主体。 */
    private final List<CaseSubject> subjects;
    /** 全部案件的材料要求及模板。 */
    private final List<ReviewRequirementView> requirements;
    /** 全部案件的证据引用。 */
    private final List<EvidenceRef> evidence;
    /** 主案件已接收的分类。 */
    private final List<CaseClassification> classifications;

    /** 主案件与子案件。 */
    public List<DueDiligenceCase> allCases() {
        var all = new java.util.ArrayList<DueDiligenceCase>();
        all.add(mainCase);
        all.addAll(childCases);
        return all;
    }

    /** 主案件是否仍在进行中。 */
    public boolean isActive() {
        return Integer.valueOf(1).equals(mainCase.getActiveSlot());
    }
}
