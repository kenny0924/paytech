package com.paytech.ledger.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.account.api.rule.AccountIdentityRule;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.ledger.core.constant.AccountingCodes;
import com.paytech.ledger.core.dal.dataobject.BookSequence;
import com.paytech.ledger.core.dal.dataobject.ChartOfAccounts;
import com.paytech.ledger.core.dal.dataobject.CurrencySpec;
import com.paytech.ledger.core.dal.dataobject.FinanceEntity;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.enums.BookStatus;
import com.paytech.ledger.core.enums.FinanceEntityStatus;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.core.enums.ReferenceDataStatus;
import com.paytech.ledger.domainservice.repository.ChartOfAccountsRepository;
import com.paytech.ledger.domainservice.repository.CurrencySpecRepository;
import com.paytech.ledger.domainservice.repository.FinanceEntityRepository;
import com.paytech.ledger.domainservice.service.LedgerSetupService;
import com.paytech.ledger.service.processor.request.CreateLedgerBookRequest;
import com.paytech.ledger.service.processor.response.CreatedResourceResponse;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

import lombok.AllArgsConstructor;

/**
 * 创建 OPEN 账簿并初始化其序号行
 * <li>OE 主体只校验 entityNo 格式，本期不向 CCT 核对 OE 是否存在；Finance Entity 必须本地存在且 ACTIVE。</li>
 * <li>同主体同币种唯一由唯一键保护，无需资源锁。</li>
 */
@Component
@AllArgsConstructor
public class CreateLedgerBookProcessor extends AbstractProcessor<CreateLedgerBookRequest, CreatedResourceResponse> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final LedgerSetupService ledgerSetupService;
    private final CurrencySpecRepository currencySpecRepository;
    private final FinanceEntityRepository financeEntityRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;

    /** 仅依赖输入、首次与重放通用的附加校验。 */
    @Override
    protected void validate(CreateLedgerBookRequest request) {
        AccountIdentityRule.checkEntityId(request.getEntityType().getCode(), request.getEntityId());
    }

    @Override
    protected CreatedResourceResponse processRequest(CreateLedgerBookRequest request) {
        checkActiveCurrency(request.getCurrency());
        ChartOfAccounts chart = getActiveChart(request.getChartCode());
        checkFinanceEntity(request);
        LedgerBook book = buildLedgerBook(request, chart);
        ledgerSetupService.createLedgerBook(book, buildSequence(book));
        CreatedResourceResponse response = new CreatedResourceResponse();
        response.setId(book.getId());
        return response;
    }

    /** 币种必须已在币种规则中启用。 */
    void checkActiveCurrency(String currency) {
        CurrencySpec spec = currencySpecRepository.findByCurrency(currency);
        if (spec == null || spec.getStatus() != CurrencySpecStatus.ACTIVE) {
            throw new BizCheckFailException("币种未启用: " + currency);
        }
    }

    /** 科目表必须存在且启用。 */
    ChartOfAccounts getActiveChart(String chartCode) {
        ChartOfAccounts chart = chartOfAccountsRepository.findByChartCode(chartCode);
        if (chart == null || chart.getStatus() != ReferenceDataStatus.ACTIVE) {
            throw new BizCheckFailException("科目表不存在或未启用: " + chartCode);
        }
        return chart;
    }

    /** Finance Entity 主体必须本地存在且 ACTIVE。 */
    void checkFinanceEntity(CreateLedgerBookRequest request) {
        if (request.getEntityType() != LedgerEntityType.FINANCE_ENTITY) {
            return;
        }
        FinanceEntity entity = financeEntityRepository.findById(Long.parseLong(request.getEntityId()));
        if (entity == null || entity.getStatus() != FinanceEntityStatus.ACTIVE) {
            throw new BizCheckFailException("财务主体不存在或已关闭: " + request.getEntityId());
        }
    }

    /** 构建新的账簿。 */
    LedgerBook buildLedgerBook(CreateLedgerBookRequest request, ChartOfAccounts chart) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        LedgerBook book = new LedgerBook();
        book.setId(idGenerator.nextId());
        book.setEntityType(request.getEntityType());
        book.setEntityId(request.getEntityId());
        book.setCurrency(request.getCurrency());
        book.setChartId(chart.getId());
        book.setBookStatus(BookStatus.OPEN);
        book.setVersion(0L);
        book.setCreateTime(now);
        book.setUpdateTime(now);
        return book;
    }

    /** 构建账簿的序号行，从 1 开始分配。 */
    BookSequence buildSequence(LedgerBook book) {
        BookSequence sequence = new BookSequence();
        sequence.setId(idGenerator.nextId());
        sequence.setLedgerBookId(book.getId());
        sequence.setNextValue(AccountingCodes.FIRST_JOURNAL_SEQUENCE);
        sequence.setCreateTime(book.getCreateTime());
        sequence.setUpdateTime(book.getCreateTime());
        return sequence;
    }
}
