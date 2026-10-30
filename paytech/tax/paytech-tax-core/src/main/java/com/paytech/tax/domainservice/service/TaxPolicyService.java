package com.paytech.tax.domainservice.service;

import java.time.LocalDate;

import com.paytech.tax.domain.calculation.ResolvedTaxPolicy;
import com.paytech.tax.domain.policy.TaxPolicyRules;

/** 地区包加载与校验：只读本库 t_tax_policy，解析与互斥校验由纯函数完成。 */
public interface TaxPolicyService {

    /**
     * 计算时选用的地区包：必须存在、APPROVED、辖区一致、税点日期在有效期内且内容通过 Schema 与互斥校验
     *
     * @param policyId 准入指向的地区包 ID；可空
     * @param jurisdictionCode 供应地
     * @param taxPointDate 税点日期
     * @return 可用地区包；任一条件不满足返回 null（调用方 BLOCKED(POLICY_INVALID)）
     */
    ResolvedTaxPolicy resolveForCalculation(Long policyId, String jurisdictionCode, LocalDate taxPointDate);

    /**
     * 读取已被计算引用的地区包规则（登记、开票时解释历史计算）
     *
     * @param policyId 地区包 ID
     * @return 地区包及规则；不存在或内容已不合法为系统异常（APPROVED 内容不可改）
     */
    ResolvedTaxPolicy loadReferenced(long policyId);

    /**
     * 解析地区包内容
     *
     * @param policyJson policy_json
     * @return 规则；不合法时抛出 {@link com.paytech.tax.domain.policy.TaxPolicyInvalidException}
     */
    TaxPolicyRules parse(String policyJson);
}
