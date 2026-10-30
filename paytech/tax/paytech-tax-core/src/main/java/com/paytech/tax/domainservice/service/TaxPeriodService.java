package com.paytech.tax.domainservice.service;

import java.time.LocalDate;

import com.paytech.tax.core.dal.dataobject.TaxPeriod;
import com.paytech.tax.core.dal.dataobject.TaxRegistration;
import com.paytech.tax.domain.policy.TaxPolicyRules;

/** 申报期间按需生成（technique 9.4）。 */
public interface TaxPeriodService {

    /**
     * 税点日期所属期间；不存在时按注册申报周期生成（单条插入，已存在不变），截止日按地区包规则计算
     *
     * @param registration 注册
     * @param rules 计算所依据地区包的规则（reporting.dueDateRule）
     * @param taxPointDate 注册法定时区下的税点日期
     * @return 期间（可能由并发方先生成）
     */
    TaxPeriod ensurePeriod(TaxRegistration registration, TaxPolicyRules rules, LocalDate taxPointDate);
}
