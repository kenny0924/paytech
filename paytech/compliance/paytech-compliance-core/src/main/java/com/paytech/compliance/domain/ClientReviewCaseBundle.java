package com.paytech.compliance.domain;

import java.util.List;

import com.paytech.compliance.core.dal.dataobject.CaseRequirement;
import com.paytech.compliance.core.dal.dataobject.CaseStateChange;
import com.paytech.compliance.core.dal.dataobject.CaseSubject;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.dal.dataobject.EvidenceRef;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一个待登记案件及其主体、材料要求、证据引用与初始状态变更。 */
@Getter
@AllArgsConstructor
public class ClientReviewCaseBundle {
    /** 案件。 */
    private final DueDiligenceCase reviewCase;
    /** 案件唯一的 PRIMARY 主体。 */
    private final CaseSubject subject;
    /** 按程序修订实例化的材料要求。 */
    private final List<CaseRequirement> requirements;
    /** 本案件主体的证据引用。 */
    private final List<EvidenceRef> evidence;
    /** 初始状态变更。 */
    private final CaseStateChange stateChange;
}
