package com.paytech.client.master.core.dal.mapper;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.Client;
import com.paytech.client.master.domain.entityadmin.ClientSummaryView;

/** t_client 的持久化查询。 */
public interface ClientMapper {
    /** 按 Client 身份读取客户；不存在时返回 null。 */
    Client findById(@Param("clientId") long clientId);

    /** PENDING 条件更新为 ACTIVE，返回影响行数。 */
    int activate(@Param("clientId") long clientId, @Param("now") java.time.Instant now);

    /** 读取 Client 主体摘要；不存在时返回 null。 */
    ClientSummaryView findSummary(@Param("clientId") long clientId);
}
