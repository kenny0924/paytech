package com.paytech.client.master.domain.onboarding.requirement;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.paytech.client.master.domain.enums.ClientApplicationStatus;
import com.paytech.client.master.domain.onboarding.pack.OnboardingPack;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次需求计算的全部输入；由提交准备步骤在事务外一次构造。 */
@Getter
@AllArgsConstructor
public class RequirementInput {
    /** 申请冻结的辖区包与 Schema。 */
    private final OnboardingPack pack;
    /** 组织类型代码；个人且无个人经营形态时为空。 */
    private final String organizationTypeCode;
    /** 受审主体，第一项为主主体。 */
    private final List<RequirementSubject> subjects;
    /** 进行中关系。 */
    private final List<RelationFact> relations;
    /** 已关联材料。 */
    private final List<DocumentFact> documents;
    /** compliance 程序材料要求：主主体与关联人员合并。 */
    private final List<MaterialRequirement> materials;
    /** 声明类型到最新取值。 */
    private final Map<String, String> declarations;
    /** 申请状态。 */
    private final ClientApplicationStatus applicationStatus;
    /** 退回补件截止时间，可空。 */
    private final Instant reviewDueTime;
    /** 计算时点。 */
    private final Instant now;
}
