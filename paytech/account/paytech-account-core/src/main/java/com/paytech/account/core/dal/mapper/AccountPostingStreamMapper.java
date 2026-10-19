package com.paytech.account.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.core.dal.dataobject.AccountPostingStream;

/** t_account_posting_stream：入账流序号行。 */
@Mapper
public interface AccountPostingStreamMapper {

    /** 插入一行。 */
    int insert(AccountPostingStream record);

    /** 以排他锁读取一条流，不存在返回空；必须在事务内调用。 */
    AccountPostingStream selectForUpdate(@Param("entityType") AccountEntityType entityType,
        @Param("entityId") String entityId, @Param("currency") String currency, @Param("shardNo") int shardNo);

    /** 读取主体与币种下的全部流，按分片号排序。 */
    List<AccountPostingStream> selectByEntity(@Param("entityType") AccountEntityType entityType,
        @Param("entityId") String entityId, @Param("currency") String currency);

    /** 读取一组主体在同一币种下的全部流，按主体标识、分片号排序。 */
    List<AccountPostingStream> selectByEntities(@Param("entityType") AccountEntityType entityType,
        @Param("entityIds") List<String> entityIds, @Param("currency") String currency);

    /**
     * 以原序号为条件把 last_sequence 加一并记录入账时间
     *
     * @return 受影响行数
     */
    int advance(@Param("stream") AccountPostingStream stream, @Param("postedTime") Instant postedTime);
}
