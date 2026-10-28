package com.paytech.card.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.binlibrary.CardBinLibraryApi;
import com.paytech.card.api.binlibrary.request.ActivateBinLibraryVersionRequestBody;
import com.paytech.card.api.binlibrary.request.CreateBinLibraryVersionRequestBody;
import com.paytech.card.api.binlibrary.response.BinImportErrorResponseBody;
import com.paytech.card.api.binlibrary.response.BinLibraryActivationResponseBody;
import com.paytech.card.api.binlibrary.response.BinLibraryVersionResponseBody;
import com.paytech.card.api.binlibrary.response.BinLookupResponseBody;
import com.paytech.card.api.binlibrary.response.BinRangeResponseBody;
import com.paytech.card.ext.rest.converter.CardBinLibraryConverter;
import com.paytech.card.service.CardBinLibraryQueryService;
import com.paytech.card.service.CardCallerService;
import com.paytech.card.service.processor.ActivateBinLibraryVersionProcessor;
import com.paytech.card.service.processor.CreateBinLibraryVersionProcessor;
import com.paytech.card.service.processor.request.ActivateBinLibraryVersionRequest;
import com.paytech.card.service.processor.request.CreateBinLibraryVersionRequest;
import com.paytech.card.service.processor.response.CreateBinLibraryVersionResponse;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.files.FileAddress;

import lombok.AllArgsConstructor;

/** BIN 库运营私网入口：查询按调用方白名单授权后委托只读服务；创建版本与激活委托各自的 Processor。 */
@RestController
@AllArgsConstructor
public class CardBinLibraryController implements CardBinLibraryApi {
    final CardCallerResolver callers;
    final CardCallerService cardCallerService;
    final CardBinLibraryConverter converter;
    final CardBinLibraryQueryService queryService;
    final CreateBinLibraryVersionProcessor createProcessor;
    final ActivateBinLibraryVersionProcessor activateProcessor;

    /** 分页列出 BIN 库版本 */
    @Override
    public PagingResponseBody<BinLibraryVersionResponseBody> listVersions(String caller, String status, Integer page,
        Integer size) {
        requireOperations(caller);
        return queryService.listVersions(converter.convertStatus(status), PagingRequestBody.of(page, size))
            .map(converter::convertVersion);
    }

    /** 查询 BIN 库版本详情 */
    @Override
    public BinLibraryVersionResponseBody getVersion(String caller, String versionNo) {
        requireOperations(caller);
        return converter.convertVersion(queryService.getVersion(Integer.parseInt(versionNo)));
    }

    /** 分页列出导入错误 */
    @Override
    public PagingResponseBody<BinImportErrorResponseBody> listImportErrors(String caller, String versionNo, Integer page,
        Integer size) {
        requireOperations(caller);
        return queryService.listImportErrors(Integer.parseInt(versionNo), PagingRequestBody.of(page, size))
            .map(converter::convertError);
    }

    /** 分页列出版本号段 */
    @Override
    public PagingResponseBody<BinRangeResponseBody> listRanges(String caller, String versionNo, String binPrefix,
        String cardBrand, String fundingType, String issuerCountry, Integer page, Integer size) {
        requireOperations(caller);
        return queryService.listRanges(Integer.parseInt(versionNo),
            converter.convertRangeFilter(binPrefix, cardBrand, fundingType, issuerCountry), PagingRequestBody.of(page, size))
            .map(converter::convertRange);
    }

    /** 用已上传文件创建版本：新建 201，同一文件的原版本 200 */
    @Override
    public ResponseEntity<BinLibraryVersionResponseBody> createVersion(String caller, String actorAccountId,
        CreateBinLibraryVersionRequestBody body) {
        FileAddress fileAddress = converter.convertFileAddress(body.getFileAddress());
        CreateBinLibraryVersionResponse response = createProcessor.process(new CreateBinLibraryVersionRequest(
            callers.resolve(caller), Long.valueOf(actorAccountId), fileAddress.value(), fileAddress.fileId()));
        return ResponseEntity.status(response.isCreated() ? HttpStatus.CREATED : HttpStatus.OK)
            .body(converter.convertVersion(response.getVersion()));
    }

    /** 激活或回切 */
    @Override
    public BinLibraryActivationResponseBody activateVersion(String caller, String actorAccountId, String versionNo,
        ActivateBinLibraryVersionRequestBody body) {
        return converter.convertActivationResult(activateProcessor.process(new ActivateBinLibraryVersionRequest(
            callers.resolve(caller), Long.valueOf(actorAccountId), Integer.valueOf(versionNo),
            body.getExpectedActiveVersionNo())));
    }

    /** 分页列出激活历史 */
    @Override
    public PagingResponseBody<BinLibraryActivationResponseBody> listActivations(String caller, Integer page,
        Integer size) {
        requireOperations(caller);
        return queryService.listActivations(PagingRequestBody.of(page, size)).map(converter::convertActivation);
    }

    /** 按 BIN 查询卡属性 */
    @Override
    public BinLookupResponseBody lookupBin(String caller, String bin, String versionNo) {
        requireOperations(caller);
        return converter.convertLookup(queryService.lookupBin(bin, versionNo == null ? null : Integer.valueOf(versionNo)));
    }

    /** 查询入口只接受 operations-console-bff，其他调用方 404。 */
    void requireOperations(String caller) {
        cardCallerService.require(callers.resolve(caller), CardApiConstants.OPERATIONS_CONSOLE_BFF);
    }
}
