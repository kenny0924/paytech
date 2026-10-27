package com.paytech.compliance.domain;

import java.util.List;
import java.util.Optional;

import com.paytech.compliance.core.dal.dataobject.ProgramCheck;
import com.paytech.compliance.core.dal.dataobject.ProgramRequirement;
import com.paytech.compliance.core.dal.dataobject.ProgramScreening;
import com.paytech.compliance.core.dal.dataobject.ProgramVersionView;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一个程序版本及其全部模板（材料槽含允许类型、检查、筛查）。 */
@Getter
@AllArgsConstructor
public class ResolvedProgram {
    /** 程序与版本属性。 */
    private final ProgramVersionView version;
    /** 材料槽，按代码排序。 */
    private final List<ProgramRequirement> requirements;
    /** 检查模板，按代码排序。 */
    private final List<ProgramCheck> checks;
    /** 筛查模板。 */
    private final List<ProgramScreening> screenings;

    /** 版本 ID。 */
    public long versionId() {
        return version.getProgramVersionId();
    }

    /** 按代码查找材料槽。 */
    public Optional<ProgramRequirement> requirement(String requirementCode) {
        return requirements.stream().filter(row -> row.getRequirementCode().equals(requirementCode)).findFirst();
    }
}
