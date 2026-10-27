package com.paytech.client.master.integration;

import java.util.List;

import com.paytech.client.master.domain.onboarding.ProgramScope;
import com.paytech.client.master.domain.onboarding.requirement.MaterialRequirement;

/** compliance CP-1 程序解析出站端口：只取材料槽；只能在数据库事务外调用。 */
public interface ComplianceProgramClient {
    /**
     * 解析申请适用的材料要求
     *
     * @param scope 辖区、主体类型、档次、责任方与是否含关联人员
     * @return 主主体程序的材料（适用角色 PRIMARY 等原样保留）；含关联人员时追加 RELATED_PERSON 程序的材料，
     *         其中适用受审主体本身（PRIMARY）的材料改为 ANY_RELATED
     *         <li>无可用程序、程序歧义或 compliance 不可用时失败关闭为 ONBOARDING_DEPENDENCY_UNAVAILABLE（503）。</li>
     */
    List<MaterialRequirement> materials(ProgramScope scope);
}
