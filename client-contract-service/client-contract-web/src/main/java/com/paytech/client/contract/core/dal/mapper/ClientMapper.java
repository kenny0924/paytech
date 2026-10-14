package com.paytech.client.contract.core.dal.mapper;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.Client;

/** t_client 的持久化查询。 */
public interface ClientMapper {
    /** 按 Client 身份读取客户；不存在时返回 null。 */
    Client findById(@Param("clientId") long clientId);

    /** PENDING 条件更新为 ACTIVE，返回影响行数。 */
    int activate(@Param("clientId") long clientId, @Param("now") java.time.Instant now);
}
