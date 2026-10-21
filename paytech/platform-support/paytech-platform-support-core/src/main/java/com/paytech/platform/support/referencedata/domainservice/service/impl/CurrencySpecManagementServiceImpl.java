package com.paytech.platform.support.referencedata.domainservice.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.paytech.platform.support.referencedata.core.dal.dataobject.CurrencySpec;
import com.paytech.platform.support.referencedata.domain.ChangeCurrencySpecStatusContext;
import com.paytech.platform.support.referencedata.domain.UpdateCurrencySpecContext;
import com.paytech.platform.support.referencedata.domainservice.repository.CurrencySpecRepository;
import com.paytech.platform.support.referencedata.domainservice.service.CurrencySpecManagementService;

import lombok.AllArgsConstructor;

/** 只保存币种规则本表，不访问外部能力或其他 Service。 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CurrencySpecManagementServiceImpl implements CurrencySpecManagementService {

    private final CurrencySpecRepository repository;

    /** 插入新行，唯一键冲突由 Repository 转换为并发冲突。 */
    @Override
    public void create(CurrencySpec spec) {
        repository.insert(spec);
    }

    /** 写入 Context 中已核对的目标规则。 */
    @Override
    public void update(UpdateCurrencySpecContext context) {
        repository.updateRule(context.getSpec(), context.getMinorUnit(), context.getRoundingMode(),
            context.getUpdateTime());
    }

    /** 写入 Context 中已核对的目标状态。 */
    @Override
    public void changeStatus(ChangeCurrencySpecStatusContext context) {
        repository.changeStatus(context.getSpec(), context.getStatus(), context.getUpdateTime());
    }
}
