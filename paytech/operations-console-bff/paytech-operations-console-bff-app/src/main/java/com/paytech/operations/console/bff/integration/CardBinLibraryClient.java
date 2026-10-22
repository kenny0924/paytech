package com.paytech.operations.console.bff.integration;

import com.paytech.card.api.binlibrary.request.ActivateBinLibraryVersionRequestBody;
import com.paytech.card.api.binlibrary.request.CreateBinLibraryVersionRequestBody;
import com.paytech.card.api.binlibrary.response.BinImportErrorResponseBody;
import com.paytech.card.api.binlibrary.response.BinLibraryActivationResponseBody;
import com.paytech.card.api.binlibrary.response.BinLibraryVersionResponseBody;
import com.paytech.card.api.binlibrary.response.BinLookupResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import org.springframework.http.ResponseEntity;

/**
 * Card BIN 库的本地端口；版本状态、校验、激活与查询规则归 Card
 * <li>页面请求与响应直接使用 Card 运营合同的模型，BFF 不增加页面字段；写命令携带会话操作人。</li>
 *
 * @author Claude
 */
public interface CardBinLibraryClient {

    /**
     * 版本分页
     *
     * @param status 可选状态
     * @param page 已校验页码
     * @param size 已校验条数
     * @return 版本分页
     */
    PagingResponseBody<BinLibraryVersionResponseBody> listVersions(String status, int page, int size);

    /**
     * 版本详情
     *
     * @param versionNo 版本号
     * @return 版本
     */
    BinLibraryVersionResponseBody getVersion(int versionNo);

    /**
     * 导入错误分页
     *
     * @param versionNo 版本号
     * @param page 已校验页码
     * @param size 已校验条数
     * @return 错误分页
     */
    PagingResponseBody<BinImportErrorResponseBody> listImportErrors(int versionNo, int page, int size);

    /**
     * 激活历史分页
     *
     * @param page 已校验页码
     * @param size 已校验条数
     * @return 激活记录分页
     */
    PagingResponseBody<BinLibraryActivationResponseBody> listActivations(int page, int size);

    /**
     * 按 BIN 查询卡属性
     *
     * @param bin 6～8 位数字
     * @param versionNo 可选版本号，null 查当前生效版本
     * @return 查询结果
     */
    BinLookupResponseBody lookupBin(String bin, Integer versionNo);

    /**
     * 用已上传文件创建版本
     *
     * @param context 操作人
     * @param request 文件地址
     * @return Card 的状态码（201 新建 / 200 同文件原版本）与版本
     */
    ResponseEntity<BinLibraryVersionResponseBody> createVersion(OperatorContext context, CreateBinLibraryVersionRequestBody request);

    /**
     * 激活或回切
     *
     * @param context 操作人
     * @param versionNo 目标版本号
     * @param request 期望的当前生效版本号
     * @return 激活记录
     */
    BinLibraryActivationResponseBody activateVersion(OperatorContext context, int versionNo,
        ActivateBinLibraryVersionRequestBody request);
}
