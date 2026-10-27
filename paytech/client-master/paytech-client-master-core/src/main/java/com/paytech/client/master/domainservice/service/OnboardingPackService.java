package com.paytech.client.master.domainservice.service;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.Jurisdiction;
import com.paytech.client.master.core.dal.dataobject.OnboardingSchema;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.onboarding.pack.OnboardingPack;
import com.paytech.client.master.domain.onboarding.pack.RuleSettings;

/** 辖区包与采集 Schema 的只读装载；只访问本库目录表，不开事务、不调用外部服务。 */
public interface OnboardingPackService {
    /**
     * 装载申请冻结的 Schema 版本及其辖区包
     *
     * @param schemaId 申请冻结的 Schema 版本
     * @return 完整辖区包快照；Schema 或辖区不存在时为目录损坏的系统错误
     * <li>2026-10-27 之前的遗留申请没有 Schema（schemaId 为空），只读展示，不参与采集、需求计算或提交：返回 409 APPLICATION_NOT_EDITABLE。</li>
     */
    OnboardingPack load(Long schemaId);

    /**
     * 解析新申请或更换组织类型时使用的 ACTIVE Schema：先取组织类型专用版本，没有时取 * 默认版本
     *
     * @param jurisdictionCode 辖区代码
     * @param partyType 主主体类型
     * @param organizationTypeCode 组织类型代码，可空
     * @return ACTIVE Schema；没有可用版本时为 null
     */
    OnboardingSchema resolveSchema(String jurisdictionCode, ClientType partyType, String organizationTypeCode);

    /**
     * 读取辖区包
     *
     * @param jurisdictionCode 辖区代码
     * @return 辖区包；不存在为 null
     */
    Jurisdiction jurisdiction(String jurisdictionCode);

    /**
     * 全部辖区包（含非 ACTIVE），按代码排序
     *
     * @return 辖区包
     */
    List<Jurisdiction> jurisdictions();

    /**
     * 辖区及其组织类型、标识类型作用域的规则开关
     *
     * @param jurisdictionCode 辖区代码
     * @return 规则解释器
     */
    RuleSettings rules(String jurisdictionCode);
}
