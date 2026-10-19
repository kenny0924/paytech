package com.paytech.account.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.core.dal.dataobject.AccountPosting;

/** t_account_posting：账户入账批次读写。 */
public interface AccountPostingRepository {

    /** 插入批次；同一序号、同一事实版本或同一移动已有批次时以并发冲突失败。 */
    void insert(AccountPosting posting);

    /** 按主键读取，不存在返回空。 */
    AccountPosting findById(long id);

    /** 按主键读取一组批次；空集合返回空列表。 */
    List<AccountPosting> findByIds(List<Long> ids);

    /** 按流与序号读取，不存在返回空。 */
    AccountPosting findBySequence(AccountEntityType entityType, String entityId, String currency, int shardNo,
        long postingSequence);

    /** 读取一条流中序号不小于起点的批次，按序号升序，最多 limit 条。 */
    List<AccountPosting> findFromSequence(AccountEntityType entityType, String entityId, String currency, int shardNo,
        long fromSequence, int limit);

    /** 首次发布确认时写入发布时间，成功后同步原对象；已写入过时不改变。 */
    void markPublished(AccountPosting posting, Instant publishedTime);
}
