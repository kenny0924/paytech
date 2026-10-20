package com.paytech.ledger.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.enums.ChartAccountStatus;

/** t_chart_account：会计科目读取与维护。 */
@Mapper
public interface ChartAccountMapper {

    /** 插入一行。 */
    int insert(ChartAccount record);

    /** 按主键读取。 */
    ChartAccount selectById(@Param("id") long id);

    /** 按主键升序读取一组科目。 */
    List<ChartAccount> selectByIds(@Param("ids") List<Long> ids);

    /** 按科目表和科目代码读取。 */
    ChartAccount selectByChartAndCode(@Param("chartId") long chartId, @Param("accountCode") String accountCode);

    /** 按科目表和科目编号读取。 */
    ChartAccount selectByChartAndNo(@Param("chartId") long chartId, @Param("accountNo") String accountNo);

    /** 读取科目表下的科目，可按是否可过账与启停状态过滤，按科目编号升序。 */
    List<ChartAccount> selectByChart(@Param("chartId") long chartId, @Param("postingAllowed") Boolean postingAllowed,
        @Param("accountStatus") ChartAccountStatus accountStatus);

    /** 读取直接下级科目，按科目编号升序。 */
    List<ChartAccount> selectChildren(@Param("parentAccountId") long parentAccountId);

    /**
     * 修改科目名称、编号与上级
     *
     * @return 受影响行数
     */
    int updateStructure(@Param("account") ChartAccount account, @Param("accountName") String accountName,
        @Param("accountNo") String accountNo, @Param("parentAccountId") Long parentAccountId, @Param("now") Instant now);

    /**
     * 以原状态为条件改变启停状态
     *
     * @return 受影响行数
     */
    int updateStatus(@Param("account") ChartAccount account, @Param("to") ChartAccountStatus to,
        @Param("now") Instant now);
}
