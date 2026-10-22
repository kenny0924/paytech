package com.paytech.card.service;

import com.paytech.card.api.enums.BinLibraryVersionStatus;
import com.paytech.card.core.dal.dataobject.CardBinImportError;
import com.paytech.card.core.dal.dataobject.CardBinLibraryVersion;
import com.paytech.card.domain.binlibrary.BinLibraryActivationView;
import com.paytech.card.domain.binlibrary.CardBinMatchView;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

/** BIN 库只读查询：版本、导入错误、激活历史与单个 BIN；不开事务、不缓存，每次读取已提交状态。 */
public interface CardBinLibraryQueryService {
    /**
     * 按主键读取版本（导入任务使用）
     *
     * @param versionId 版本 ID
     * @return 版本；不存在时为 null
     */
    CardBinLibraryVersion findVersion(long versionId);

    /**
     * 按版本号读取版本
     *
     * @param versionNo 版本号
     * @return 版本
     *         <li>不存在时抛出 NOT_FOUND。</li>
     */
    CardBinLibraryVersion getVersion(int versionNo);

    /**
     * 分页列出版本
     *
     * @param status 可选状态
     * @param paging 已校验的分页参数
     * @return 版本号倒序的分页结果
     */
    PagingResponseBody<CardBinLibraryVersion> listVersions(BinLibraryVersionStatus status, PagingRequestBody paging);

    /**
     * 分页列出某版本的导入错误
     *
     * @param versionNo 版本号
     * @param paging 已校验的分页参数
     * @return 按行号排序的分页结果
     *         <li>版本不存在时抛出 NOT_FOUND。</li>
     */
    PagingResponseBody<CardBinImportError> listImportErrors(int versionNo, PagingRequestBody paging);

    /**
     * 分页列出激活历史
     *
     * @param paging 已校验的分页参数
     * @return 按激活时间倒序的分页结果
     */
    PagingResponseBody<BinLibraryActivationView> listActivations(PagingRequestBody paging);

    /**
     * 按 BIN 做最长前缀匹配
     *
     * @param bin 6～8 位数字 BIN
     * @param versionNo 指定版本号；null 查当前生效版本
     * @return 匹配结果；不指定版本且没有生效版本时为 null
     *         <li>指定版本不存在时抛出 NOT_FOUND；指定版本仍在校验或校验失败时抛出业务检查失败。</li>
     */
    CardBinMatchView lookupBin(String bin, Integer versionNo);
}
