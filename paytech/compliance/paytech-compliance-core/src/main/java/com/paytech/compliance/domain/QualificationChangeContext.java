package com.paytech.compliance.domain;

import com.paytech.compliance.core.dal.dataobject.QualificationDecision;
import com.paytech.compliance.core.dal.dataobject.QualificationStateChange;
import com.paytech.compliance.core.dal.dataobject.RoleQualification;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次资格变更：新决定、要切换的现有投影或要新建的投影、目标投影值，以及一条状态变更
 * <li>current 与 created 恰有一个非空：已有投影时按读取时版本条件切换到 target，否则插入版本 1 的新投影（created 即 target）。</li>
 * <li>状态变更的聚合版本为变更后的投影版本；保存时同事务登记 QUALIFICATION_CHANGED 发布任务。</li>
 */
@Getter
@AllArgsConstructor
public class QualificationChangeContext {
    /** 本次新增的资格决定。 */
    private final QualificationDecision decision;
    /** 已有投影，读取时版本；没有投影时为空。 */
    private final RoleQualification current;
    /** 要新建的投影；已有投影时为空。 */
    private final RoleQualification created;
    /** 目标投影值：状态、有效期、复审调度与冻结属性。 */
    private final RoleQualification target;
    /** 本次状态变更。 */
    private final QualificationStateChange stateChange;

    /** 本次变更后的投影；保存成功后已同步状态与版本。 */
    public RoleQualification qualification() {
        return current != null ? current : created;
    }
}
