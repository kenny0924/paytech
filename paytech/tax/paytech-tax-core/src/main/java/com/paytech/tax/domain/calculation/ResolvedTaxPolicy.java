package com.paytech.tax.domain.calculation;

import com.paytech.tax.core.dal.dataobject.TaxPolicy;
import com.paytech.tax.domain.policy.TaxPolicyRules;

/**
 * 已校验可用的地区包版本及其规则
 *
 * @param policy 地区包记录
 * @param rules 解析后的规则
 */
public record ResolvedTaxPolicy(TaxPolicy policy, TaxPolicyRules rules) {
}
