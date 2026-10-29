package com.paytech.risk.control.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.risk.control.core.dal.dataobject.ClientUnderwriting;
import com.paytech.risk.control.core.enums.UnderwritingStatus;
import com.paytech.risk.control.domain.underwriting.UnderwritingListFilter;
import com.paytech.risk.control.domain.underwriting.UnderwritingVerdict;

/** Client 承保版本表映射。 */
public interface ClientUnderwritingMapper {
    /** 插入版本行。 */
    int insert(@Param("row") ClientUnderwriting row);

    /** 按 ID 读取版本行。 */
    ClientUnderwriting selectById(@Param("underwritingId") long underwritingId);

    /** 读取 Client 占用进行中槽位的版本。 */
    ClientUnderwriting selectOpen(@Param("clientId") long clientId);

    /** 读取 Client 占用有效槽位的版本。 */
    ClientUnderwriting selectEffective(@Param("clientId") long clientId);

    /** 读取 Client 版本号最大的版本。 */
    ClientUnderwriting selectLatest(@Param("clientId") long clientId);

    /** 读取 Client 的版本历史，版本号倒序。 */
    List<ClientUnderwriting> selectHistory(@Param("clientId") long clientId, @Param("limit") int limit);

    /** 按筛选条件分页读取，更新时间倒序。 */
    List<ClientUnderwriting> selectPage(@Param("filter") UnderwritingListFilter filter, @Param("limit") int limit,
        @Param("offset") long offset);

    /** 统计同一筛选条件下的版本数。 */
    long count(@Param("filter") UnderwritingListFilter filter);

    /** 统计占用有效槽位的版本数。 */
    long countEffective();

    /** 统计某状态的版本数。 */
    long countByStatus(@Param("status") UnderwritingStatus status);

    /** 统计决定时间在 [from, to) 且处于给定状态之一的版本数。 */
    long countDecided(@Param("statuses") List<UnderwritingStatus> statuses, @Param("from") Instant from,
        @Param("to") Instant to);

    /** 以期望版本整体替换草稿内容。 */
    int updateDraft(@Param("row") ClientUnderwriting row, @Param("values") ClientUnderwriting values,
        @Param("now") Instant now);

    /** 以期望版本冻结草稿内容并置为 SUBMITTED。 */
    int markSubmitted(@Param("row") ClientUnderwriting row, @Param("values") ClientUnderwriting values,
        @Param("submittedBy") long submittedBy, @Param("now") Instant now);

    /** 以期望状态与版本写入决定；values 非空时同时冻结内容。 */
    int decide(@Param("row") ClientUnderwriting row, @Param("values") ClientUnderwriting values,
        @Param("verdict") UnderwritingVerdict verdict);

    /** 以期望版本把有效 APPROVED 置为 SUPERSEDED 并释放有效槽位。 */
    int supersede(@Param("row") ClientUnderwriting row, @Param("now") Instant now);
}
