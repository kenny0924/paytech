package com.paytech.tax.test.mocker.local;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.tax.core.enums.FilingFrequency;
import com.paytech.tax.core.enums.MarketCollectionStatus;
import com.paytech.tax.core.enums.MarketObligation;
import com.paytech.tax.core.enums.TaxCategoryStatus;
import com.paytech.tax.core.enums.TaxRegistrationStatus;
import com.paytech.tax.test.builder.TaxTestConstants;
import com.paytech.tax.test.dal.TaxTestMapper;

/**
 * 测试纳税主体的准入、注册、地区包与税类前置数据（经 TaxTestMapper 插入并提交）
 * <li>ID 由调用方给出且不小于 1000；注册默认 ACTIVE、按注册所在国申报币种、按季申报。</li>
 */
@TestComponent
public class TaxSubjectMocker {
    private final TaxTestMapper mapper;

    /** 注入测试 Mapper。 */
    public TaxSubjectMocker(TaxTestMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 主体在一国按地区包计税：ACTIVE 注册 + REGISTRATION_REQUIRED / ENABLED 准入
     *
     * @param id 注册 ID，准入 ID 为 id + 1
     * @param country 国家
     * @param policyId 地区包 ID
     * @param reportingCurrency 注册申报币种
     * @param timezone 注册法定时区
     * @param frequency 申报周期
     * @return 注册 ID
     */
    public long registered(long id, String country, long policyId, String reportingCurrency, String timezone,
        FilingFrequency frequency) {
        mapper.insertRegistration(id, TaxTestConstants.SUBJECT_PARTY_ID, country, TaxRegistrationStatus.ACTIVE.getCode(),
            reportingCurrency, timezone, frequency.getCode());
        mapper.insertMarket(id + 1, TaxTestConstants.SUBJECT_PARTY_ID, country,
            MarketObligation.REGISTRATION_REQUIRED.getCode(), MarketCollectionStatus.ENABLED.getCode(), id, policyId);
        return id;
    }

    /**
     * 只有准入、没有注册的准入（BLOCKED、NO_COLLECTION_REQUIRED 等）
     *
     * @param id 准入 ID
     * @param country 国家
     * @param obligation 评估结论
     * @param collectionStatus 收税状态
     * @param policyId 地区包 ID；可空
     */
    public void market(long id, String country, MarketObligation obligation, MarketCollectionStatus collectionStatus,
        Long policyId) {
        mapper.insertMarket(id, TaxTestConstants.SUBJECT_PARTY_ID, country, obligation.getCode(), collectionStatus.getCode(),
            null, policyId);
    }

    /** 插入一个已批准的测试地区包。 */
    public void policy(long id, String country, String policyJson) {
        mapper.insertPolicy(id, country, policyJson);
    }

    /** 插入一个测试税类。 */
    public void category(long id, String code, TaxCategoryStatus status) {
        mapper.insertCategory(id, code, status.getCode());
    }

    /** 初始化 DML 中地区包的内容（用于派生测试地区包）。 */
    public String seedPolicyJson(long policyId) {
        return mapper.selectPolicyJson(policyId);
    }
}
