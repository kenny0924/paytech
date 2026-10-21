package com.paytech.platform.support.referencedata.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.CurrencySpec;
import com.paytech.platform.support.referencedata.core.dal.mapper.CurrencySpecMapper;
import com.paytech.platform.support.referencedata.domainservice.repository.CurrencySpecRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的币种规则仓储。 */
@Repository
@AllArgsConstructor
public class CurrencySpecRepositoryImpl implements CurrencySpecRepository {

    private final CurrencySpecMapper mapper;

    /** 全部币种规则。 */
    @Override
    public List<CurrencySpec> findAll() {
        return mapper.selectAll();
    }

    /** 按币种代码查询。 */
    @Override
    public CurrencySpec findByCurrency(String currency) {
        return mapper.selectByCurrency(currency);
    }

    /** 排他锁定当前行。 */
    @Override
    public CurrencySpec lockByCurrency(String currency) {
        return mapper.lockByCurrency(currency);
    }

    /** 只把币种唯一键冲突转换为“已存在”业务拒绝，其他故障保留原异常。 */
    @Override
    public void insert(CurrencySpec spec) {
        try {
            requireSaved(mapper.insert(spec), spec.getCurrency());
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_currency")) {
                throw failure;
            }
            throw new BizCheckFailException("币种规则已存在: " + spec.getCurrency(), failure);
        }
    }

    /** 条件更新成功后同步规则、版本与时间。 */
    @Override
    public void updateRule(CurrencySpec spec, int minorUnit, CurrencyRoundingMode roundingMode, Instant updateTime) {
        requireVersionAvailable(spec);
        requireSaved(mapper.updateRule(spec, minorUnit, roundingMode, updateTime), spec.getCurrency());
        spec.setMinorUnit(minorUnit);
        spec.setRoundingMode(roundingMode);
        spec.setVersion(spec.getVersion() + 1);
        spec.setUpdateTime(updateTime);
    }

    /** 条件更新成功后同步状态、版本与时间。 */
    @Override
    public void changeStatus(CurrencySpec spec, CurrencySpecStatus status, Instant updateTime) {
        requireVersionAvailable(spec);
        requireSaved(mapper.updateStatus(spec, status, updateTime), spec.getCurrency());
        spec.setStatus(status);
        spec.setVersion(spec.getVersion() + 1);
        spec.setUpdateTime(updateTime);
    }

    /** 版本已到整型上限时拒绝，避免溢出。 */
    void requireVersionAvailable(CurrencySpec spec) {
        if (spec.getVersion() == Integer.MAX_VALUE) {
            throw new ConcurrentRequestException("币种规则版本已达到上限: " + spec.getCurrency());
        }
    }

    /** 未精确保存一行时回滚，由调用方丢弃本次 Context。 */
    void requireSaved(int changed, String currency) {
        if (changed != 1) {
            throw new ConcurrentRequestException("币种规则已被其他操作修改，请刷新后重试: " + currency);
        }
    }
}
