package com.paytech.ledger.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.FinanceEntity;
import com.paytech.ledger.domain.FinanceEntityQuery;

/** t_finance_entity：财务主体持久化。 */
@Mapper
public interface FinanceEntityMapper {

    /** 插入一行。 */
    int insert(FinanceEntity record);

    /** 按主键读取。 */
    FinanceEntity selectById(@Param("id") long id);

    /** 按过滤条件分页读取财务主体，按主键升序。 */
    List<FinanceEntity> selectPage(@Param("query") FinanceEntityQuery query, @Param("size") int size,
        @Param("offset") int offset);

    /** 统计符合过滤条件的财务主体数量。 */
    long countPage(@Param("query") FinanceEntityQuery query);
}
