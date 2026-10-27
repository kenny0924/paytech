package com.paytech.client.master.core.dal.mapper;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.Client;
import com.paytech.client.master.domain.entityadmin.ClientSummaryView;

/** t_client 的持久化查询。 */
public interface ClientMapper {
    /** 按 Client 身份读取客户；不存在时返回 null。 */
    Client findById(@Param("clientId") long clientId);

    /** PENDING 条件更新为 ACTIVE，返回影响行数。 */
    /** 运营 Client 分页（筛选为空表示不限）。 */
    java.util.List<com.paytech.client.master.domain.onboardingadmin.ClientAdminView> pageForAdmin(@Param("status") String status,
        @Param("jurisdictionCode") String jurisdictionCode, @Param("responsiblePartnerId") Long responsiblePartnerId,
        @Param("limit") int limit, @Param("offset") int offset);

    /** 与分页相同筛选条件下的总数。 */
    long countForAdmin(@Param("status") String status, @Param("jurisdictionCode") String jurisdictionCode,
        @Param("responsiblePartnerId") Long responsiblePartnerId);

    /** 运营 Client 详情行；不存在为 null。 */
    com.paytech.client.master.domain.onboardingadmin.ClientAdminView findAdminView(@Param("clientId") long clientId);

    /** ACTIVE 条件转为 SUSPENDED 并记录挂起时间，返回影响行数。 */
    int suspend(@Param("clientId") long clientId, @Param("now") java.time.Instant now);

    int activate(@Param("clientId") long clientId, @Param("now") java.time.Instant now);

    /** 读取 Client 主体摘要；不存在时返回 null。 */
    ClientSummaryView findSummary(@Param("clientId") long clientId);
}
