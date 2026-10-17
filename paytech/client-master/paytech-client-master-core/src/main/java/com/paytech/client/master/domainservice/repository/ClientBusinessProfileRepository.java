package com.paytech.client.master.domainservice.repository;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.ClientBusinessProfile;

/** t_client_business_profile 的本地仓储；不负责其他表的加载或事务。 */
public interface ClientBusinessProfileRepository {
    /** 读取本 Client Checkout 范围内的 client_business_profile 记录，保留历史和未生效事实供业务判断。 */
    List<ClientBusinessProfile> findByClientId(long clientId);

    /** 某 Client 唯一经营资料的分类归属；不存在返回 null。 */
    com.paytech.client.master.core.dal.dataobject.ClientClassificationProfile findClassificationProfile(long clientId);

    /** 把分类绑定到指定任务并清除确认，成功后同步原对象；本任务已确认时抛出并发冲突。 */
    void bindClassificationCase(com.paytech.client.master.core.dal.dataobject.ClientClassificationProfile profile, long caseId,
        java.time.Instant now);

    /** 写入确认人与时间，成功后同步原对象；已确认或任务已变化时抛出并发冲突。 */
    void confirmClassification(com.paytech.client.master.core.dal.dataobject.ClientClassificationProfile profile, long actorId,
        java.time.Instant now);
}
