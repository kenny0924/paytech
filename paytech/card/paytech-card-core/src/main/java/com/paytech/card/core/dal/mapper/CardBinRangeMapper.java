package com.paytech.card.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.card.core.dal.dataobject.CardBinRange;
import com.paytech.card.domain.binlibrary.BinRangeFilter;
import com.paytech.card.domain.binlibrary.CardBinMatchView;

/** BIN 号段 SQL 映射 */
@Mapper
public interface CardBinRangeMapper {
    /** 批量插入同一版本的号段；同版本同前缀重复时由唯一索引拒绝。 */
    int insertRanges(@Param("ranges") List<CardBinRange> ranges);

    /** 删除某版本至多 limit 条号段（中断重入前清理残留），返回删除条数。 */
    int deleteRanges(@Param("versionId") long versionId, @Param("limit") int limit);

    /**
     * 版本左关联最长命中号段（一条 SQL，不缓存）
     *
     * @param prefixes 由长到短的候选前缀（8、7、6 位或只有 6 位）
     * @param versionNo 指定版本号；null 表示当前生效版本
     * @return 匹配结果；所查版本不存在时为 null
     */
    CardBinMatchView selectMatch(@Param("prefixes") List<String> prefixes, @Param("versionNo") Integer versionNo);

    /** 某版本内满足筛选条件的号段数。 */
    long countRanges(@Param("versionId") long versionId, @Param("filter") BinRangeFilter filter);

    /** 某版本内满足筛选条件的号段，按前缀升序分页。 */
    List<CardBinRange> selectRanges(@Param("versionId") long versionId, @Param("filter") BinRangeFilter filter,
        @Param("offset") int offset, @Param("limit") int limit);
}
