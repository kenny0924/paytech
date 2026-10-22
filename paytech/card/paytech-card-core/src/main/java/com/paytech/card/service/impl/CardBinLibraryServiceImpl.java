package com.paytech.card.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.card.core.dal.dataobject.CardBinImportError;
import com.paytech.card.core.dal.dataobject.CardBinLibraryVersion;
import com.paytech.card.core.dal.dataobject.CardBinRange;
import com.paytech.card.domain.binlibrary.BinImportRowError;
import com.paytech.card.domain.binlibrary.BinLibraryActivationContext;
import com.paytech.card.domain.binlibrary.BinLibraryValidation;
import com.paytech.card.domain.binlibrary.BinRangeRow;
import com.paytech.card.domainservice.repository.CardBinImportErrorRepository;
import com.paytech.card.domainservice.repository.CardBinLibraryActivationRepository;
import com.paytech.card.domainservice.repository.CardBinLibraryVersionRepository;
import com.paytech.card.domainservice.repository.CardBinRangeRepository;
import com.paytech.card.service.CardBinLibraryService;
import com.paytech.card.service.handler.CardBinLibraryImportHandler;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.id.IdGenerator;

import lombok.AllArgsConstructor;

/** BIN 库版本的本地写入 */
@Service
@AllArgsConstructor
public class CardBinLibraryServiceImpl implements CardBinLibraryService {
    /** 清理残留号段时每条 DELETE 的行数上限。 */
    static final int CLEAR_BATCH_SIZE = 5000;

    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final CardBinRangeRepository cardBinRangeRepository;
    private final CompensationEventRegistrar compensationEventRegistrar;
    private final CardBinImportErrorRepository cardBinImportErrorRepository;
    private final CardBinLibraryVersionRepository cardBinLibraryVersionRepository;
    private final CardBinLibraryActivationRepository cardBinLibraryActivationRepository;

    /**
     * 版本与导入任务同事务提交，提交后由补偿执行器唤醒导入
     * <li>版本号或文件地址撞唯一索引（锁失效时的并发创建）整体回滚，按并发冲突返回。</li>
     */
    @Override
    public void createVersion(CardBinLibraryVersion version) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                cardBinLibraryVersionRepository.insertVersion(version);
                compensationEventRegistrar.register(CardBinLibraryImportHandler.buildEvent(version.getVersionId()));
            });
        } catch (DuplicateKeyException concurrent) {
            throw new ConcurrentRequestException("BIN 库版本正在被并发创建，请刷新后重试", concurrent);
        }
    }

    /**
     * 先停用原生效版本释放占位，再激活目标并留痕
     * <li>任一行数不符或生效占位撞唯一索引（锁失效时的并发激活）整体回滚，按并发冲突返回。</li>
     */
    @Override
    public void activateVersion(BinLibraryActivationContext context) {
        Instant now = context.getActivation().getCreateTime();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (context.getPrevious() != null) {
                    cardBinLibraryVersionRepository.retire(context.getPrevious(), now);
                }
                cardBinLibraryVersionRepository.activate(context.getTarget(), now);
                cardBinLibraryActivationRepository.insertActivation(context.getActivation());
            });
        } catch (DuplicateKeyException concurrent) {
            throw new ConcurrentRequestException("生效版本已被他人修改，请刷新后重试", concurrent);
        }
    }

    @Override
    public void rejectVersion(CardBinLibraryVersion version, String failureReason) {
        cardBinLibraryVersionRepository.markInvalid(version, failureReason, null, 0, now());
    }

    /** 错误明细与 INVALID 状态一起提交。 */
    @Override
    public void saveValidationFailure(CardBinLibraryVersion version, BinLibraryValidation validation) {
        Instant now = now();
        List<CardBinImportError> errors =
            validation.getErrors().stream().map(error -> importError(version, error, now)).toList();
        transactionTemplate.executeWithoutResult(status -> {
            cardBinImportErrorRepository.insertErrors(errors);
            cardBinLibraryVersionRepository.markInvalid(version, null, validation.getTotalRows(),
                validation.getErrorRows(), now);
        });
    }

    /** 每条 DELETE 自动提交，直到没有残留。 */
    @Override
    public void clearRanges(CardBinLibraryVersion version) {
        while (cardBinRangeRepository.deleteRanges(version.getVersionId(), CLEAR_BATCH_SIZE) > 0) {
            // 继续删除下一批
        }
    }

    /** 本批号段与进度 CAS 一起提交。 */
    @Override
    public void saveRangeBatch(CardBinLibraryVersion version, List<BinRangeRow> rows, int processedRows) {
        Instant now = now();
        List<CardBinRange> ranges = rows.stream().map(row -> range(version, row, now)).toList();
        transactionTemplate.executeWithoutResult(status -> {
            cardBinRangeRepository.insertRanges(ranges);
            cardBinLibraryVersionRepository.updateProgress(version, processedRows, now);
        });
    }

    @Override
    public void completeVersion(CardBinLibraryVersion version, int totalRows) {
        cardBinLibraryVersionRepository.markValid(version, totalRows, now());
    }

    /** 错误明细行。 */
    CardBinImportError importError(CardBinLibraryVersion version, BinImportRowError error, Instant now) {
        CardBinImportError row = new CardBinImportError();
        row.setErrorId(idGenerator.nextId());
        row.setVersionId(version.getVersionId());
        row.setLineNo(error.getLineNo());
        row.setColumnName(error.getColumnName());
        row.setErrorCode(error.getErrorCode());
        row.setErrorMessage(error.getErrorMessage());
        row.setValueExcerpt(error.getValueExcerpt());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /** 号段行。 */
    CardBinRange range(CardBinLibraryVersion version, BinRangeRow row, Instant now) {
        CardBinRange range = new CardBinRange();
        range.setRangeId(idGenerator.nextId());
        range.setVersionId(version.getVersionId());
        range.setBinPrefix(row.getBinPrefix());
        range.setPrefixLength(row.getBinPrefix().length());
        range.setCardBrand(row.getCardBrand());
        range.setFundingType(row.getFundingType());
        range.setCardLevel(row.getCardLevel());
        range.setCardProduct(row.getCardProduct());
        range.setIssuerCountry(row.getIssuerCountry());
        range.setIssuerRegion(row.getIssuerRegion());
        range.setIssuerName(row.getIssuerName());
        range.setCreateTime(now);
        range.setUpdateTime(now);
        return range;
    }

    /** UTC 微秒时间。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
