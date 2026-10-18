package com.paytech.ledger.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.domain.LedgerBookQuery;
import com.paytech.ledger.domain.view.LedgerBookView;

/** t_ledger_book：账簿持久化。 */
@Mapper
public interface LedgerBookMapper {

    /** 插入一行。 */
    int insert(LedgerBook record);

    /** 按主键读取。 */
    LedgerBook selectById(@Param("id") long id);

    /** 按主键读取账簿与其科目表代码，不存在返回空。 */
    LedgerBookView selectView(@Param("id") long id);

    /** 按主体类型、主体标识和币种读取唯一账簿。 */
    LedgerBook selectByEntity(@Param("entityType") LedgerEntityType entityType, @Param("entityId") String entityId,
        @Param("currency") String currency);

    /** 按过滤条件分页读取账簿及其科目表代码，按主键升序。 */
    List<LedgerBookView> selectPage(@Param("query") LedgerBookQuery query, @Param("size") int size,
        @Param("offset") int offset);

    /** 统计符合过滤条件的账簿数量。 */
    long countPage(@Param("query") LedgerBookQuery query);

    /** 按主键升序读取主体的全部币种账簿。 */
    List<LedgerBook> selectAllByEntity(@Param("entityType") LedgerEntityType entityType, @Param("entityId") String entityId);
}
