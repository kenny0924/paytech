package com.paytech.card.api.binlibrary;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.binlibrary.request.ActivateBinLibraryVersionRequestBody;
import com.paytech.card.api.binlibrary.request.CreateBinLibraryVersionRequestBody;
import com.paytech.card.api.binlibrary.response.BinImportErrorResponseBody;
import com.paytech.card.api.binlibrary.response.BinLibraryActivationResponseBody;
import com.paytech.card.api.binlibrary.response.BinLibraryVersionResponseBody;
import com.paytech.card.api.binlibrary.response.BinLookupResponseBody;
import com.paytech.card.api.binlibrary.response.BinRangeResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * BIN 库运营私网接口（2026-10-22 BIN 库需求 technique §3.5.1）：版本列表与详情、导入错误、版本号段列表（2026-10-27 号段列表需求 technique
 * §3.1）、用已上传文件创建版本、激活与回切、激活历史、单个 BIN 查询。
 * <li>唯一调用方 operations-console-bff，其他调用方返回 404；写接口要求 {@link CardApiConstants#ACTOR_HEADER}，缺失或格式错误返回 400。</li>
 * <li>只按 BIN（6～8 位）查询，任何接口都不接收或返回完整卡号。错误只用公共代码：INVALID_REQUEST、BUSINESS_CHECK_FAILED、NOT_FOUND、
 * CONCURRENT_REQUEST、SERVICE_UNAVAILABLE。</li>
 */
@HttpExchange(url = "/card/v1/bin-library", accept = MediaType.APPLICATION_JSON_VALUE)
public interface CardBinLibraryApi {

    /**
     * 分页列出 BIN 库版本
     *
     * @param caller 调用方服务名，只接受 operations-console-bff
     * @param status 可选状态筛选：VALIDATING、VALID、INVALID、ACTIVE、RETIRED
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按版本号倒序的分页结果；参数非法时 400
     */
    @GetExchange("/versions")
    PagingResponseBody<BinLibraryVersionResponseBody> listVersions(
        @RequestHeader(CardApiConstants.CALLER_HEADER) String caller,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "VALIDATING|VALID|INVALID|ACTIVE|RETIRED", message = "版本状态无效") String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 查询 BIN 库版本详情（含导入进度）
     *
     * @param caller 调用方服务名，只接受 operations-console-bff
     * @param versionNo 版本号
     * @return 版本详情；版本不存在时 404
     */
    @GetExchange("/versions/{versionNo}")
    BinLibraryVersionResponseBody getVersion(@RequestHeader(CardApiConstants.CALLER_HEADER) String caller,
        @PathVariable("versionNo") @Pattern(regexp = "[1-9][0-9]{0,8}", message = "版本号无效") String versionNo);

    /**
     * 分页列出某版本的导入错误明细（每个版本最多记录前 200 条）
     *
     * @param caller 调用方服务名，只接受 operations-console-bff
     * @param versionNo 版本号
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按行号排序的分页结果；版本不存在时 404
     */
    @GetExchange("/versions/{versionNo}/errors")
    PagingResponseBody<BinImportErrorResponseBody> listImportErrors(
        @RequestHeader(CardApiConstants.CALLER_HEADER) String caller,
        @PathVariable("versionNo") @Pattern(regexp = "[1-9][0-9]{0,8}", message = "版本号无效") String versionNo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 分页列出某版本的号段，可按前缀、卡组织、借贷记与发卡国组合筛选
     *
     * @param caller 调用方服务名，只接受 operations-console-bff
     * @param versionNo 版本号
     * @param binPrefix 可选 1～8 位数字，匹配以其开头的号段
     * @param cardBrand 可选卡组织：VISA、MASTERCARD、AMEX、UNIONPAY、MADA、DISCOVER、JCB、DINERS
     * @param fundingType 可选借贷记类型：CREDIT、DEBIT、PREPAID、CHARGE
     * @param issuerCountry 可选发卡国 ISO 3166-1 alpha-2
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按号段前缀升序的分页结果
     *         <li>参数格式错误 400；版本不存在 404；版本仍在校验或校验失败 400。</li>
     */
    @GetExchange("/versions/{versionNo}/ranges")
    PagingResponseBody<BinRangeResponseBody> listRanges(@RequestHeader(CardApiConstants.CALLER_HEADER) String caller,
        @PathVariable("versionNo") @Pattern(regexp = "[1-9][0-9]{0,8}", message = "版本号无效") String versionNo,
        @RequestParam(value = "binPrefix", required = false) @Pattern(regexp = "[0-9]{1,8}", message = "BIN 前缀必须为 1 至 8 位数字") String binPrefix,
        @RequestParam(value = "cardBrand", required = false) @Pattern(regexp = "VISA|MASTERCARD|AMEX|UNIONPAY|MADA|DISCOVER|JCB|DINERS", message = "卡组织无效") String cardBrand,
        @RequestParam(value = "fundingType", required = false) @Pattern(regexp = "CREDIT|DEBIT|PREPAID|CHARGE", message = "借贷记类型无效") String fundingType,
        @RequestParam(value = "issuerCountry", required = false) @Pattern(regexp = "[A-Z]{2}", message = "发卡国无效") String issuerCountry,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 用已上传到 Platform 文件服务的 CSV 创建 BIN 库版本，异步扫描、校验与写入号段
     *
     * @param caller 调用方服务名，只接受 operations-console-bff
     * @param actorAccountId 导入人 Operations Account ID
     * @param body 文件地址
     * @return 新版本（VALIDATING）为 201；同一文件已建过版本时 200 并返回原版本
     *         <li>文件不存在、来源 / 上传原因 / 类型不符、扫描未通过时 400；另一导入或激活正在执行时 409；Platform 不可用时 503。</li>
     */
    @PostExchange(url = "/versions", contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<BinLibraryVersionResponseBody> createVersion(
        @RequestHeader(CardApiConstants.CALLER_HEADER) String caller,
        @RequestHeader(CardApiConstants.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}", message = "操作人无效") String actorAccountId,
        @Valid @RequestBody CreateBinLibraryVersionRequestBody body);

    /**
     * 激活 VALID 版本或回切到 RETIRED 版本，原生效版本同时停用
     *
     * @param caller 调用方服务名，只接受 operations-console-bff
     * @param actorAccountId 操作人 Operations Account ID
     * @param versionNo 目标版本号
     * @param body 页面看到的当前生效版本号
     * @return 激活记录
     *         <li>目标状态不是 VALID / RETIRED（含已是 ACTIVE）时 400；版本不存在时 404；期望生效版本与实际不符或另一操作正在执行时 409。</li>
     */
    @PostExchange(url = "/versions/{versionNo}/activation", contentType = MediaType.APPLICATION_JSON_VALUE)
    BinLibraryActivationResponseBody activateVersion(@RequestHeader(CardApiConstants.CALLER_HEADER) String caller,
        @RequestHeader(CardApiConstants.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}", message = "操作人无效") String actorAccountId,
        @PathVariable("versionNo") @Pattern(regexp = "[1-9][0-9]{0,8}", message = "版本号无效") String versionNo,
        @Valid @RequestBody ActivateBinLibraryVersionRequestBody body);

    /**
     * 分页列出激活与回切历史
     *
     * @param caller 调用方服务名，只接受 operations-console-bff
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按激活时间倒序的分页结果
     */
    @GetExchange("/activations")
    PagingResponseBody<BinLibraryActivationResponseBody> listActivations(
        @RequestHeader(CardApiConstants.CALLER_HEADER) String caller,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 按 BIN 查询卡属性（8 位优先，其次 7、6 位前缀）
     *
     * @param caller 调用方服务名，只接受 operations-console-bff
     * @param bin 6～8 位数字 BIN
     * @param versionNo 可选版本号；不带时查当前生效版本
     * @return 卡属性；未识别也是 200（recognized=false）
     *         <li>BIN 格式错误 400；指定版本不存在 404；指定版本仍在校验或校验失败 400。</li>
     */
    @GetExchange("/bins/{bin}")
    BinLookupResponseBody lookupBin(@RequestHeader(CardApiConstants.CALLER_HEADER) String caller,
        @PathVariable("bin") @Pattern(regexp = "[0-9]{6,8}", message = "BIN 必须为 6 至 8 位数字") String bin,
        @RequestParam(value = "versionNo", required = false) @Pattern(regexp = "[1-9][0-9]{0,8}", message = "版本号无效") String versionNo);
}
