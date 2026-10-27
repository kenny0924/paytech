package com.paytech.client.master.domainservice.repository;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.Client;
import com.paytech.client.master.domain.entityadmin.ClientSummaryView;

/** t_client 的本地仓储；不负责其他表的加载或事务。 */
public interface ClientRepository {
    /** 按 Client 身份读取客户；不存在时返回 null。 */
    Client findById(long clientId);

    /** 入驻通过后 PENDING 条件转为 ACTIVE；状态已变化时抛出并发冲突。 */
    void activate(long clientId, java.time.Instant now);

    /** 运营 Client 分页（筛选为空表示不限），按 Client ID 倒序。 */
    java.util.List<com.paytech.client.master.domain.onboardingadmin.ClientAdminView> pageForAdmin(String status, String jurisdictionCode,
        Long responsiblePartnerId, int limit, int offset);

    /** 与分页相同筛选条件下的总数。 */
    long countForAdmin(String status, String jurisdictionCode, Long responsiblePartnerId);

    /** 运营 Client 详情行；不存在为 null。 */
    com.paytech.client.master.domain.onboardingadmin.ClientAdminView findAdminView(long clientId);

    /** 资格撤销后 ACTIVE 条件转为 SUSPENDED；状态已变化时抛出并发冲突。 */
    void suspend(long clientId, java.time.Instant now);

    /** 读取 Client 主体摘要（Client、Party 类型与法定名称）；不存在时返回 null。 */
    ClientSummaryView findSummary(long clientId);
}
