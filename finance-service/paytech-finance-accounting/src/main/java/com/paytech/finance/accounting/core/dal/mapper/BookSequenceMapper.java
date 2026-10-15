package com.paytech.finance.accounting.core.dal.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.BookSequence;

/** t_book_sequence：账簿分录序号持久化。 */
@Mapper
public interface BookSequenceMapper {

    /** 插入账簿序号行。 */
    int insert(BookSequence record);

    /** 以排他锁读取账簿序号行。 */
    BookSequence selectByLedgerBookForUpdate(@Param("ledgerBookId") long ledgerBookId);

    /**
     * 在已持有行锁时把下一序号加一。
     *
     * @return 受影响行数
     */
    int advance(@Param("sequence") BookSequence sequence, @Param("now") Instant now);
}
