package com.paytech.finance.accounting.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.finance.accounting.core.dal.dataobject.AccountingPeriod;
import com.paytech.finance.accounting.core.enums.PeriodStatus;
import com.paytech.finance.accounting.domainservice.repository.AccountingPeriodRepository;
import com.paytech.finance.accounting.domainservice.service.AccountingPeriodService;
import com.paytech.finance.accounting.service.processor.request.OpenAccountingPeriodRequest;
import com.paytech.finance.accounting.service.processor.response.OpenAccountingPeriodResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 把 FUTURE 期间开放过账
 * <li>已经 OPEN 时直接返回当前状态；其他状态拒绝。</li>
 * <li>单行状态迁移由状态与版本 CAS 保护，无需资源锁。</li>
 */
@Component
@AllArgsConstructor
public class OpenAccountingPeriodProcessor
    extends AbstractProcessor<OpenAccountingPeriodRequest, OpenAccountingPeriodResponse> {
    private final AccountingPeriodService accountingPeriodService;
    private final AccountingPeriodRepository accountingPeriodRepository;

    @Override
    protected OpenAccountingPeriodResponse processRequest(OpenAccountingPeriodRequest request) {
        AccountingPeriod period = accountingPeriodRepository.findById(request.getAccountingPeriodId());
        if (period == null) {
            throw new BizCheckFailException("会计期间不存在: " + request.getAccountingPeriodId());
        }
        if (period.getStatus() == PeriodStatus.OPEN) {
            return response(period);
        }
        if (period.getStatus() != PeriodStatus.FUTURE) {
            throw new BizCheckFailException("只有 FUTURE 期间可以开放: " + period.getId() + ", 状态 "
                + period.getStatus().getCode());
        }
        accountingPeriodService.openAccountingPeriod(period);
        return response(period);
    }

    /** 从期间当前状态构造响应。 */
    OpenAccountingPeriodResponse response(AccountingPeriod period) {
        OpenAccountingPeriodResponse response = new OpenAccountingPeriodResponse();
        response.setAccountingPeriodId(period.getId());
        response.setStatus(period.getStatus());
        return response;
    }
}
