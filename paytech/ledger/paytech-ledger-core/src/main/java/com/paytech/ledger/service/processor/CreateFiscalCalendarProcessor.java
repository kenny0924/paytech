package com.paytech.ledger.service.processor;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.dal.dataobject.FinanceEntity;
import com.paytech.ledger.core.dal.dataobject.FiscalCalendar;
import com.paytech.ledger.core.enums.FinanceEntityStatus;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.domainservice.repository.FinanceEntityRepository;
import com.paytech.ledger.domainservice.service.AccountingPeriodService;
import com.paytech.ledger.domainservice.validator.account.AccountIdentityRule;
import com.paytech.ledger.service.processor.request.CreateFiscalCalendarRequest;
import com.paytech.ledger.service.processor.response.CreatedResourceResponse;

import lombok.AllArgsConstructor;

/** 为主体创建唯一的法定财务日历；主体唯一由唯一键保护，无需资源锁。 */
@Component
@AllArgsConstructor
public class CreateFiscalCalendarProcessor
    extends AbstractProcessor<CreateFiscalCalendarRequest, CreatedResourceResponse> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final AccountingPeriodService accountingPeriodService;
    private final FinanceEntityRepository financeEntityRepository;

    /** 仅依赖输入、首次与重放通用的附加校验。 */
    @Override
    protected void validate(CreateFiscalCalendarRequest request) {
        AccountIdentityRule.checkEntityId(request.getEntityType().getCode(), request.getEntityId());
        if (request.getYearStartMonth() < 1 || request.getYearStartMonth() > 12) {
            throw new InvalidParameterException("财年开始月份必须在 1 至 12 之间: " + request.getYearStartMonth());
        }
        try {
            ZoneId.of(request.getTimezone());
        } catch (DateTimeException e) {
            throw new InvalidParameterException("时区不是合法的 IANA 代码: " + request.getTimezone(), e);
        }
    }

    @Override
    protected CreatedResourceResponse processRequest(CreateFiscalCalendarRequest request) {
        checkFinanceEntity(request);
        FiscalCalendar calendar = buildCalendar(request);
        accountingPeriodService.createFiscalCalendar(calendar);
        CreatedResourceResponse response = new CreatedResourceResponse();
        response.setId(calendar.getId());
        return response;
    }

    /** Finance Entity 主体必须本地存在且 ACTIVE；OE 只校验格式。 */
    void checkFinanceEntity(CreateFiscalCalendarRequest request) {
        if (request.getEntityType() != LedgerEntityType.FINANCE_ENTITY) {
            return;
        }
        FinanceEntity entity = financeEntityRepository.findById(Long.parseLong(request.getEntityId()));
        if (entity == null || entity.getStatus() != FinanceEntityStatus.ACTIVE) {
            throw new FailException(LedgerSetupErrorCode.FINANCE_ENTITY_UNAVAILABLE);
        }
    }

    /** 构建新的财务日历。 */
    FiscalCalendar buildCalendar(CreateFiscalCalendarRequest request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        FiscalCalendar calendar = new FiscalCalendar();
        calendar.setId(idGenerator.nextId());
        calendar.setEntityType(request.getEntityType());
        calendar.setEntityId(request.getEntityId());
        calendar.setCalendarCode(request.getCalendarCode());
        calendar.setTimezone(request.getTimezone());
        calendar.setYearStartMonth(request.getYearStartMonth());
        calendar.setVersion(0L);
        calendar.setCreateTime(now);
        calendar.setUpdateTime(now);
        return calendar;
    }
}
