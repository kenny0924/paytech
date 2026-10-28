package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.card.api.binlibrary.request.ActivateBinLibraryVersionRequestBody;
import com.paytech.card.api.binlibrary.request.CreateBinLibraryVersionRequestBody;
import com.paytech.card.api.binlibrary.response.BinImportErrorResponseBody;
import com.paytech.card.api.binlibrary.response.BinLibraryActivationResponseBody;
import com.paytech.card.api.binlibrary.response.BinLibraryVersionResponseBody;
import com.paytech.card.api.binlibrary.response.BinLookupResponseBody;
import com.paytech.card.api.binlibrary.response.BinRangeResponseBody;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.BinLibraryFileClient;
import com.paytech.operations.console.bff.integration.CardBinLibraryClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.binlibrary.request.StartBinLibraryUploadRequestBody;
import com.paytech.operations.console.bff.service.model.binlibrary.response.BinLibraryUploadCompletionResponseBody;
import com.paytech.operations.console.bff.service.model.binlibrary.response.LabeledBinLibraryActivationResponseBody;
import com.paytech.operations.console.bff.service.model.binlibrary.response.LabeledBinLibraryVersionResponseBody;
import com.paytech.operations.console.bff.service.processor.GetBinLibraryVersionProcessor;
import com.paytech.operations.console.bff.service.processor.ListBinLibraryActivationsProcessor;
import com.paytech.operations.console.bff.service.processor.ListBinLibraryVersionsProcessor;
import com.paytech.operations.console.bff.service.processor.request.GetBinLibraryVersionRequest;
import com.paytech.operations.console.bff.service.processor.request.ListBinLibraryActivationsRequest;
import com.paytech.operations.console.bff.service.processor.request.ListBinLibraryVersionsRequest;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;

/**
 * 卡 BIN 库页的浏览器入口（块 132，technique.md 3.5.7 B1—B9；号段列表 B10 为 2026-10-27 需求 technique §3.3，块 140）
 * <li>Gateway 绑定：B1—B5、B10 CARD_BIN_LIBRARY_READ；上传开始 / 完成、创建版本、激活 CARD_BIN_LIBRARY_MANAGE，并经 PLT-I02 实时授权。</li>
 * <li>B1、B2、B4 经 Processor 转发 Card 后补操作人显示名（Platform 失败时显示名为 null）；B3、B5、B8—B10 简单转发 Card BIN 库运营接口；
 * B6、B7 转发 Platform 内部文件接口，CSV 由浏览器直传对象存储，不经 Gateway。</li>
 * <li>上传开始的响应含临时 PUT 授权：/card-admin/v1/bin-library/uploads 只记元数据日志。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/card-admin/v1/bin-library")
public class CardBinLibraryController {
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final Set<String> PAGING = Set.of("page", "size");
    private static final Set<String> RANGE_QUERY = Set.of("binPrefix", "cardBrand", "fundingType", "issuerCountry", "page", "size");

    private final CardBinLibraryClient client;
    private final BinLibraryFileClient fileClient;
    private final GetBinLibraryVersionProcessor versionProcessor;
    private final ListBinLibraryVersionsProcessor versionsProcessor;
    private final ListBinLibraryActivationsProcessor activationsProcessor;

    /** B1 版本分页，按版本号倒序；附导入人显示名。 */
    @GetMapping("/versions")
    public PagingResponseBody<LabeledBinLibraryVersionResponseBody> listVersions(HttpServletRequest servletRequest,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "VALIDATING|VALID|INVALID|ACTIVE|RETIRED") String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("status", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return versionsProcessor.process(new ListBinLibraryVersionsRequest(status, paging));
    }

    /** B2 版本详情（含导入进度）；附导入人显示名。 */
    @GetMapping("/versions/{versionNo}")
    public LabeledBinLibraryVersionResponseBody getVersion(HttpServletRequest servletRequest,
        @PathVariable("versionNo") @Positive Integer versionNo) {
        requireQueryKeys(servletRequest, Set.of());
        return versionProcessor.process(new GetBinLibraryVersionRequest(versionNo));
    }

    /** B3 导入错误分页，按行号。 */
    @GetMapping("/versions/{versionNo}/errors")
    public PagingResponseBody<BinImportErrorResponseBody> listImportErrors(HttpServletRequest servletRequest,
        @PathVariable("versionNo") @Positive Integer versionNo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, PAGING);
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listImportErrors(versionNo, paging.page(), paging.size());
    }

    /** B10 版本号段分页，按号段前缀升序；筛选条件与分页原样转发 Card。 */
    @GetMapping("/versions/{versionNo}/ranges")
    public PagingResponseBody<BinRangeResponseBody> listRanges(HttpServletRequest servletRequest,
        @PathVariable("versionNo") @Positive Integer versionNo,
        @RequestParam(value = "binPrefix", required = false) @Pattern(regexp = "[0-9]{1,8}") String binPrefix,
        @RequestParam(value = "cardBrand", required = false) @Pattern(regexp = "VISA|MASTERCARD|AMEX|UNIONPAY|MADA|DISCOVER|JCB|DINERS") String cardBrand,
        @RequestParam(value = "fundingType", required = false) @Pattern(regexp = "CREDIT|DEBIT|PREPAID|CHARGE") String fundingType,
        @RequestParam(value = "issuerCountry", required = false) @Pattern(regexp = "[A-Z]{2}") String issuerCountry,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, RANGE_QUERY);
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listRanges(versionNo, binPrefix, cardBrand, fundingType, issuerCountry, paging.page(), paging.size());
    }

    /** B4 激活历史分页，按时间倒序；附操作人显示名。 */
    @GetMapping("/activations")
    public PagingResponseBody<LabeledBinLibraryActivationResponseBody> listActivations(HttpServletRequest servletRequest,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, PAGING);
        return activationsProcessor.process(new ListBinLibraryActivationsRequest(PagingRequestBody.of(page, size)));
    }

    /** B5 单个 BIN 查询；未识别同样 200。 */
    @GetMapping("/bins/{bin}")
    public BinLookupResponseBody lookupBin(HttpServletRequest servletRequest,
        @PathVariable("bin") @Pattern(regexp = "[0-9]{6,8}") String bin,
        @RequestParam(value = "versionNo", required = false) @Positive Integer versionNo) {
        requireQueryKeys(servletRequest, Set.of("versionNo"));
        return client.lookupBin(bin, versionNo);
    }

    /** B6 开始上传 CSV，返回浏览器直传的临时 PUT 授权。 */
    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public FileUploadResponseBody startUpload(HttpServletRequest servletRequest, OperatorContext context,
        @RequestBody @NotNull @Valid StartBinLibraryUploadRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return fileClient.startUpload(context, body);
    }

    /** B7 完成上传，返回文件地址与扫描状态；不接受 Body。 */
    @PostMapping("/uploads/{uploadId}/completion")
    public BinLibraryUploadCompletionResponseBody completeUpload(HttpServletRequest servletRequest,
        @PathVariable("uploadId") @Pattern(regexp = ID) String uploadId) {
        requireQueryKeys(servletRequest, Set.of());
        return fileClient.completeUpload(uploadId);
    }

    /** B8 用已上传文件创建版本：Card 新建 201，同文件重复提交 200。 */
    @PostMapping("/versions")
    public ResponseEntity<BinLibraryVersionResponseBody> createVersion(HttpServletRequest servletRequest, OperatorContext context,
        @RequestBody @NotNull @Valid CreateBinLibraryVersionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createVersion(context, body);
    }

    /** B9 激活或回切；期望生效版本不符 409。 */
    @PostMapping("/versions/{versionNo}/activation")
    public BinLibraryActivationResponseBody activateVersion(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("versionNo") @Positive Integer versionNo,
        @RequestBody @NotNull @Valid ActivateBinLibraryVersionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.activateVersion(context, versionNo, body);
    }
}
