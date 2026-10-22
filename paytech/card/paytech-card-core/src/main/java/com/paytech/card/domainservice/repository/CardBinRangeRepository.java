package com.paytech.card.domainservice.repository;

import java.util.List;

import com.paytech.card.core.dal.dataobject.CardBinRange;
import com.paytech.card.domain.binlibrary.CardBinMatchView;

/** BIN 号段数据访问 */
public interface CardBinRangeRepository {
    /** 批量插入同一版本的号段；行数不符视为系统错误。 */
    void insertRanges(List<CardBinRange> ranges);

    /** 删除某版本至多 limit 条号段，返回删除条数。 */
    int deleteRanges(long versionId, int limit);

    /**
     * 按卡 BIN 做最长前缀匹配（8 位优先，其次 7、6 位）
     *
     * @param cardBin 卡 BIN，6～8 位数字；6 位时只匹配 6 位号段
     * @param versionNo 指定版本号；null 表示当前生效版本
     * @return 匹配结果；所查版本不存在（或没有生效版本）时为 null
     */
    CardBinMatchView findMatch(String cardBin, Integer versionNo);
}
