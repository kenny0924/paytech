package com.paytech.ledger.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.ledger.core.dal.dataobject.FinanceEntity;
import com.paytech.ledger.core.enums.FinanceEntityStatus;
import com.paytech.ledger.domainservice.service.LedgerSetupService;
import com.paytech.ledger.service.processor.request.CreateFinanceEntityRequest;
import com.paytech.ledger.service.processor.response.CreatedResourceResponse;

import lombok.AllArgsConstructor;

/** 创建 ACTIVE 财务主体；代码与法律 Party 的唯一性由唯一键保护，无需资源锁。 */
@Component
@AllArgsConstructor
public class CreateFinanceEntityProcessor extends AbstractProcessor<CreateFinanceEntityRequest, CreatedResourceResponse> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final LedgerSetupService ledgerSetupService;

    @Override
    protected CreatedResourceResponse processRequest(CreateFinanceEntityRequest request) {
        FinanceEntity entity = buildFinanceEntity(request);
        ledgerSetupService.createFinanceEntity(entity);
        CreatedResourceResponse response = new CreatedResourceResponse();
        response.setId(entity.getId());
        return response;
    }

    /** 构建新的财务主体。 */
    FinanceEntity buildFinanceEntity(CreateFinanceEntityRequest request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        FinanceEntity entity = new FinanceEntity();
        entity.setId(idGenerator.nextId());
        entity.setEntityCode(request.getEntityCode());
        entity.setLegalPartyId(request.getLegalPartyId());
        entity.setDisplayName(request.getDisplayName());
        entity.setStatus(FinanceEntityStatus.ACTIVE);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return entity;
    }
}
