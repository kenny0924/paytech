package com.paytech.platform.support.referencedata.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.ReferenceCurrency;
import com.paytech.platform.support.referencedata.core.dal.mapper.ReferenceCurrencyMapper;
import com.paytech.platform.support.referencedata.domain.CurrencyAttributes;
import com.paytech.platform.support.referencedata.domainservice.repository.ReferenceCurrencyRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的币种仓储。 */
@Repository
@AllArgsConstructor
public class ReferenceCurrencyRepositoryImpl implements ReferenceCurrencyRepository {

    /** 币种代码唯一索引名，用于识别重复新建。 */
    static final String CODE_UNIQUE_KEY = "uk_ref_currency_code";

    private final ReferenceCurrencyMapper mapper;

    /** 按条件查询。 */
    @Override
    public List<ReferenceCurrency> find(ReferenceDataStatus status, List<String> codes) {
        return mapper.selectByFilter(status, codes);
    }

    /** 按币种代码查询。 */
    @Override
    public ReferenceCurrency findByCurrencyCode(String currencyCode) {
        return mapper.selectByCurrencyCode(currencyCode);
    }

    /** 排他锁定当前行。 */
    @Override
    public ReferenceCurrency lockByCurrencyCode(String currencyCode) {
        return mapper.lockByCurrencyCode(currencyCode);
    }

    /** 只把币种唯一键冲突转换为“已存在”业务拒绝，其他故障保留原异常。 */
    @Override
    public void insert(ReferenceCurrency currency) {
        try {
            requireSaved(mapper.insert(currency), currency.getCurrencyCode());
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains(CODE_UNIQUE_KEY)) {
                throw failure;
            }
            throw new BizCheckFailException("币种规则已存在: " + currency.getCurrencyCode(), failure);
        }
    }

    /** 条件更新成功后同步属性、版本与时间。 */
    @Override
    public void updateAttributes(ReferenceCurrency currency, CurrencyAttributes attributes, Instant updateTime) {
        requireVersionAvailable(currency);
        requireSaved(mapper.updateAttributes(currency, attributes, updateTime), currency.getCurrencyCode());
        currency.setNumericCode(attributes.getNumericCode());
        currency.setName(attributes.getName());
        currency.setSymbol(attributes.getSymbol());
        currency.setMinorUnit(attributes.getMinorUnit());
        currency.setRoundingMode(attributes.getRoundingMode());
        currency.setLogoFileAddress(attributes.getLogoFileAddress());
        currency.setVersion(currency.getVersion() + 1);
        currency.setUpdateTime(updateTime);
    }

    /** 条件更新成功后同步状态、版本与时间。 */
    @Override
    public void changeStatus(ReferenceCurrency currency, CurrencySpecStatus status, Instant updateTime) {
        requireVersionAvailable(currency);
        requireSaved(mapper.updateStatus(currency, status, updateTime), currency.getCurrencyCode());
        currency.setStatus(status);
        currency.setVersion(currency.getVersion() + 1);
        currency.setUpdateTime(updateTime);
    }

    /** 版本已到整型上限时拒绝，避免溢出。 */
    void requireVersionAvailable(ReferenceCurrency currency) {
        if (currency.getVersion() == Integer.MAX_VALUE) {
            throw new ConcurrentRequestException("币种规则版本已达到上限: " + currency.getCurrencyCode());
        }
    }

    /** 未精确保存一行时回滚，由调用方丢弃本次 Context。 */
    void requireSaved(int changed, String currencyCode) {
        if (changed != 1) {
            throw new ConcurrentRequestException("币种规则已被其他操作修改，请刷新后重试: " + currencyCode);
        }
    }
}
