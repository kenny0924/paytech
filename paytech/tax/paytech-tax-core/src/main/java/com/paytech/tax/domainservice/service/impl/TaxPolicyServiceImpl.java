package com.paytech.tax.domainservice.service.impl;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.tax.core.dal.dataobject.TaxPolicy;
import com.paytech.tax.core.enums.TaxPolicyStatus;
import com.paytech.tax.domain.calculation.ResolvedTaxPolicy;
import com.paytech.tax.domain.policy.TaxPolicyInvalidException;
import com.paytech.tax.domain.policy.TaxPolicyRules;
import com.paytech.tax.domain.policy.TaxPolicyRulesParser;
import com.paytech.tax.domainservice.repository.TaxPolicyRepository;
import com.paytech.tax.domainservice.service.TaxPolicyService;

import lombok.AllArgsConstructor;

/** TaxPolicyService 实现：APPROVED 内容不可修改，因此每次读取后即时解析，不缓存。 */
@Service
@AllArgsConstructor
public class TaxPolicyServiceImpl implements TaxPolicyService {
    private final TaxPolicyRepository taxPolicyRepository;

    /** 存在、APPROVED、辖区一致、有效且内容合法才返回。 */
    @Override
    public ResolvedTaxPolicy resolveForCalculation(Long policyId, String jurisdictionCode, LocalDate taxPointDate) {
        TaxPolicy policy = policyId == null ? null : taxPolicyRepository.findById(policyId);
        if (policy == null || policy.getStatus() != TaxPolicyStatus.APPROVED
            || !policy.getJurisdictionCode().equals(jurisdictionCode) || !effective(policy, taxPointDate)) {
            return null;
        }
        try {
            return new ResolvedTaxPolicy(policy, parse(policy.getPolicyJson()));
        } catch (TaxPolicyInvalidException invalid) {
            return null;
        }
    }

    /** 被计算引用的地区包必须仍可解析，否则说明 APPROVED 内容被改动，属系统不变量破坏。 */
    @Override
    public ResolvedTaxPolicy loadReferenced(long policyId) {
        TaxPolicy policy = taxPolicyRepository.findById(policyId);
        if (policy == null) {
            throw new ErrorException("计算引用的地区包不存在: " + policyId);
        }
        try {
            return new ResolvedTaxPolicy(policy, parse(policy.getPolicyJson()));
        } catch (TaxPolicyInvalidException invalid) {
            throw new ErrorException("计算引用的地区包内容已不合法: " + policyId, invalid);
        }
    }

    /** 纯函数解析。 */
    @Override
    public TaxPolicyRules parse(String policyJson) {
        return TaxPolicyRulesParser.parse(policyJson);
    }

    /** 半开区间 [effective_from, effective_to)。 */
    static boolean effective(TaxPolicy policy, LocalDate date) {
        return !date.isBefore(policy.getEffectiveFrom())
            && (policy.getEffectiveTo() == null || date.isBefore(policy.getEffectiveTo()));
    }
}
