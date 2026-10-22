package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.require;

import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.binlibrary.CardBinLibraryApi;
import com.paytech.card.api.binlibrary.request.ActivateBinLibraryVersionRequestBody;
import com.paytech.card.api.binlibrary.request.CreateBinLibraryVersionRequestBody;
import com.paytech.card.api.binlibrary.response.BinImportErrorResponseBody;
import com.paytech.card.api.binlibrary.response.BinLibraryActivationResponseBody;
import com.paytech.card.api.binlibrary.response.BinLibraryVersionResponseBody;
import com.paytech.card.api.binlibrary.response.BinLookupResponseBody;
import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.CardBinLibraryClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import lombok.AllArgsConstructor;

/**
 * Card BIN 库的 HTTP 转发：每个入口只调用一次 Card，不重试；请求幂等由 Gateway 负责
 * <li>调用方固定为 operations-console-bff；写命令的操作人取会话 Account ID 填入 X-Gateway-Actor-Subject，查询不带操作人。</li>
 * <li>回执中的版本号须与请求一致，分页窗口须与请求一致；不符按上游无效响应处理。Card 422 字段路径只投影到本次请求体。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class CardBinLibraryClientImpl implements CardBinLibraryClient {
    private static final String CALLER = CardApiConstants.OPERATIONS_CONSOLE_BFF;

    private final CardBinLibraryApi api;

    /** 版本分页，核对分页窗口。 */
    @Override
    public PagingResponseBody<BinLibraryVersionResponseBody> listVersions(String status, int page, int size) {
        return window(api.listVersions(CALLER, status, page, size), page, size);
    }

    /** 版本详情；版本号一致。 */
    @Override
    public BinLibraryVersionResponseBody getVersion(int versionNo) {
        var version = api.getVersion(CALLER, String.valueOf(versionNo));
        require(version != null && versionNo == version.getVersionNo());
        return version;
    }

    /** 导入错误分页，核对分页窗口。 */
    @Override
    public PagingResponseBody<BinImportErrorResponseBody> listImportErrors(int versionNo, int page, int size) {
        return window(api.listImportErrors(CALLER, String.valueOf(versionNo), page, size), page, size);
    }

    /** 激活历史分页，核对分页窗口。 */
    @Override
    public PagingResponseBody<BinLibraryActivationResponseBody> listActivations(int page, int size) {
        return window(api.listActivations(CALLER, page, size), page, size);
    }

    /** BIN 查询原样返回。 */
    @Override
    public BinLookupResponseBody lookupBin(String bin, Integer versionNo) {
        var result = api.lookupBin(CALLER, bin, versionNo == null ? null : String.valueOf(versionNo));
        require(result != null);
        return result;
    }

    /** 创建版本：保留 Card 的 201 / 200 区分，回执须带版本号。 */
    @Override
    public ResponseEntity<BinLibraryVersionResponseBody> createVersion(OperatorContext context,
        CreateBinLibraryVersionRequestBody request) {
        var response = write(request, () -> api.createVersion(CALLER, context.accountId(), request));
        require(response != null && response.getBody() != null && response.getBody().getVersionNo() > 0
            && (response.getStatusCode().isSameCodeAs(HttpStatus.CREATED) || response.getStatusCode().isSameCodeAs(HttpStatus.OK)));
        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    }

    /** 激活或回切；回执版本号与目标一致。 */
    @Override
    public BinLibraryActivationResponseBody activateVersion(OperatorContext context, int versionNo,
        ActivateBinLibraryVersionRequestBody request) {
        var activation = write(request, () -> api.activateVersion(CALLER, context.accountId(), String.valueOf(versionNo), request));
        require(activation != null && versionNo == activation.getVersionNo());
        return activation;
    }

    /** 分页回执与请求窗口一致、条目数与总数自洽。 */
    private static <T> PagingResponseBody<T> window(PagingResponseBody<T> value, int page, int size) {
        require(value != null && value.items() != null && value.page() == page && value.size() == size && value.total() >= 0);
        long remaining = Math.max(0L, value.total() - ((long) page - 1) * size);
        require(value.items().size() == Math.min((long) size, remaining));
        return value;
    }

    /** 执行一次写调用，把 Card 字段错误投影到本次请求体。 */
    private static <T> T write(Object request, Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }
}
