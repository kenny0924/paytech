package com.paytech.card.service;

import java.util.List;

import com.paytech.card.core.dal.dataobject.CardBinLibraryVersion;
import com.paytech.card.domain.binlibrary.BinLibraryActivationContext;
import com.paytech.card.domain.binlibrary.BinLibraryValidation;
import com.paytech.card.domain.binlibrary.BinRangeRow;

/**
 * BIN 库版本的本地写入：创建（同事务登记导入任务）、导入结果与进度、激活与回切
 * <li>只访问本库；不调用 Platform 或其他外部服务。状态与进度更新以版本 CAS 保护，行数不符抛并发异常并整体回滚。</li>
 */
public interface CardBinLibraryService {
    /**
     * 保存 VALIDATING 新版本并在同一事务登记导入任务 CARD_BIN_LIBRARY_IMPORT
     *
     * @param version 已准备 ID、版本号、文件与操作人的新版本
     */
    void createVersion(CardBinLibraryVersion version);

    /**
     * 原生效版本 → RETIRED、目标 → ACTIVE、追加激活记录，一个本地事务
     *
     * @param context 已核对期望生效版本与目标状态的激活上下文
     */
    void activateVersion(BinLibraryActivationContext context);

    /**
     * 文件级失败：VALIDATING → INVALID
     *
     * @param version 校验中的版本
     * @param failureReason 失败原因，不含卡号
     */
    void rejectVersion(CardBinLibraryVersion version, String failureReason);

    /**
     * 行级失败：一个事务写入前 200 条错误明细并转为 INVALID
     *
     * @param version 校验中的版本
     * @param validation 第一遍校验结果
     */
    void saveValidationFailure(CardBinLibraryVersion version, BinLibraryValidation validation);

    /**
     * 分批删除该版本上次中断留下的号段（每批 5000 条，各自提交）
     *
     * @param version 校验中的版本
     */
    void clearRanges(CardBinLibraryVersion version);

    /**
     * 一个短事务批量写入号段并推进已写入行数
     *
     * @param version 校验中的版本
     * @param rows 本批号段
     * @param processedRows 本批写入后的累计行数
     */
    void saveRangeBatch(CardBinLibraryVersion version, List<BinRangeRow> rows, int processedRows);

    /**
     * 号段全部写入后 VALIDATING → VALID
     *
     * @param version 校验中的版本
     * @param totalRows 数据行数
     */
    void completeVersion(CardBinLibraryVersion version, int totalRows);
}
