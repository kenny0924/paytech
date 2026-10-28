package com.paytech.card.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.card.api.enums.BinLibraryVersionStatus;
import com.paytech.card.core.dal.dataobject.CardBinImportError;
import com.paytech.card.core.dal.dataobject.CardBinLibraryVersion;
import com.paytech.card.core.dal.dataobject.CardBinRange;
import com.paytech.card.domain.binlibrary.BinLibraryActivationView;
import com.paytech.card.domain.binlibrary.BinRangeFilter;
import com.paytech.card.domain.binlibrary.CardBinMatchView;
import com.paytech.card.domainservice.repository.CardBinImportErrorRepository;
import com.paytech.card.domainservice.repository.CardBinLibraryActivationRepository;
import com.paytech.card.domainservice.repository.CardBinLibraryVersionRepository;
import com.paytech.card.domainservice.repository.CardBinRangeRepository;
import com.paytech.card.service.CardBinLibraryQueryService;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** BIN 库只读查询 */
@Service
@AllArgsConstructor
public class CardBinLibraryQueryServiceImpl implements CardBinLibraryQueryService {
    private final CardBinRangeRepository cardBinRangeRepository;
    private final CardBinImportErrorRepository cardBinImportErrorRepository;
    private final CardBinLibraryVersionRepository cardBinLibraryVersionRepository;
    private final CardBinLibraryActivationRepository cardBinLibraryActivationRepository;

    @Override
    public CardBinLibraryVersion findVersion(long versionId) {
        return cardBinLibraryVersionRepository.findById(versionId);
    }

    /** 不存在返回 NOT_FOUND（404）。 */
    @Override
    public CardBinLibraryVersion getVersion(int versionNo) {
        CardBinLibraryVersion version = cardBinLibraryVersionRepository.findByVersionNo(versionNo);
        if (version == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "BIN 库版本不存在: v" + versionNo);
        }
        return version;
    }

    /** 同一筛选条件下计数与分页。 */
    @Override
    public PagingResponseBody<CardBinLibraryVersion> listVersions(BinLibraryVersionStatus status,
        PagingRequestBody paging) {
        return new PagingResponseBody<>(
            cardBinLibraryVersionRepository.listVersions(status, paging.offset(), paging.size()), paging.page(),
            paging.size(), cardBinLibraryVersionRepository.countVersions(status));
    }

    /** 先确认版本存在，再按行号分页。 */
    @Override
    public PagingResponseBody<CardBinImportError> listImportErrors(int versionNo, PagingRequestBody paging) {
        long versionId = getVersion(versionNo).getVersionId();
        return new PagingResponseBody<>(cardBinImportErrorRepository.listErrors(versionId, paging.offset(), paging.size()),
            paging.page(), paging.size(), cardBinImportErrorRepository.countErrors(versionId));
    }

    /** 只有号段已写完的版本（VALID、ACTIVE、RETIRED）可浏览；同一筛选条件下计数与分页。 */
    @Override
    public PagingResponseBody<CardBinRange> listRanges(int versionNo, BinRangeFilter filter, PagingRequestBody paging) {
        CardBinLibraryVersion version = getVersion(versionNo);
        if (version.getStatus() == BinLibraryVersionStatus.VALIDATING
            || version.getStatus() == BinLibraryVersionStatus.INVALID) {
            throw new BizCheckFailException(
                "版本尚未校验通过，无号段可浏览: v" + versionNo + ", 状态: " + version.getStatus().getCode());
        }
        long versionId = version.getVersionId();
        return new PagingResponseBody<>(cardBinRangeRepository.listRanges(versionId, filter, paging.offset(), paging.size()),
            paging.page(), paging.size(), cardBinRangeRepository.countRanges(versionId, filter));
    }

    @Override
    public PagingResponseBody<BinLibraryActivationView> listActivations(PagingRequestBody paging) {
        return new PagingResponseBody<>(cardBinLibraryActivationRepository.listActivations(paging.offset(), paging.size()),
            paging.page(), paging.size(), cardBinLibraryActivationRepository.countActivations());
    }

    /** 指定版本时要求版本存在且号段已写完（VALID、ACTIVE、RETIRED）。 */
    @Override
    public CardBinMatchView lookupBin(String bin, Integer versionNo) {
        CardBinMatchView match = cardBinRangeRepository.findMatch(bin, versionNo);
        if (versionNo == null) {
            return match;
        }
        if (match == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "BIN 库版本不存在: v" + versionNo);
        }
        if (match.getVersionStatus() == BinLibraryVersionStatus.VALIDATING
            || match.getVersionStatus() == BinLibraryVersionStatus.INVALID) {
            throw new BizCheckFailException("BIN 库版本 v" + versionNo + " 尚未通过校验，不能查询");
        }
        return match;
    }
}
