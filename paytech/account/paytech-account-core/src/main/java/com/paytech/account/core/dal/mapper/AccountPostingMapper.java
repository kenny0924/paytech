package com.paytech.account.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.core.dal.dataobject.AccountPosting;

/** t_account_posting：账户入账批次。 */
@Mapper
public interface AccountPostingMapper {

    /** 插入一行。 */
    int insert(AccountPosting record);

    /** 按主键读取，不存在返回空。 */
    AccountPosting selectById(@Param("id") long id);

    /** 按主键读取一组批次。 */
    List<AccountPosting> selectByIds(@Param("ids") List<Long> ids);

    /** 按流与序号读取，不存在返回空。 */
    AccountPosting selectBySequence(@Param("entityType") AccountEntityType entityType, @Param("entityId") String entityId,
        @Param("currency") String currency, @Param("shardNo") int shardNo, @Param("postingSequence") long postingSequence);

    /** 读取一条流中序号不小于起点的批次，按序号升序，最多 limit 条。 */
    List<AccountPosting> selectFromSequence(@Param("entityType") AccountEntityType entityType,
        @Param("entityId") String entityId, @Param("currency") String currency, @Param("shardNo") int shardNo,
        @Param("fromSequence") long fromSequence, @Param("limit") int limit);

    /**
     * 首次发布确认时写入发布时间
     *
     * @return 受影响行数；已写入过为 0
     */
    int markPublished(@Param("id") long id, @Param("publishedTime") Instant publishedTime);
}
