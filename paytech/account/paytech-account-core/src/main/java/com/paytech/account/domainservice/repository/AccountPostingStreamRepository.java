package com.paytech.account.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.core.dal.dataobject.AccountPostingStream;

/** t_account_posting_stream：入账流序号行读写。 */
public interface AccountPostingStreamRepository {

    /** 插入流；同一流已被并发创建时以并发冲突失败。 */
    void insert(AccountPostingStream stream);

    /** 以排他锁读取一条流，不存在返回空；必须在事务内调用。 */
    AccountPostingStream lock(AccountEntityType entityType, String entityId, String currency, int shardNo);

    /** 读取主体与币种下的全部流。 */
    List<AccountPostingStream> findByEntity(AccountEntityType entityType, String entityId, String currency);

    /** 读取一组主体在同一币种下的全部流；空集合返回空列表。 */
    List<AccountPostingStream> findByEntities(AccountEntityType entityType, List<String> entityIds, String currency);

    /** 以原序号为条件分配下一个序号，成功后同步原对象；序号已变化时以并发冲突失败。 */
    void advance(AccountPostingStream stream, Instant postedTime);
}
