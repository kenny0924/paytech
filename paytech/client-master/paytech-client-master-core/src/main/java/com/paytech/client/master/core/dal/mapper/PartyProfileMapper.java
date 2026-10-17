package com.paytech.client.master.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.PartyProfile;

/** t_party_profile 的持久化查询与首次批准时间写入。 */
public interface PartyProfileMapper {
    /** 读取本 Client Checkout 范围内的 party_profile 记录，保留历史和未生效事实供业务判断。 */
    List<PartyProfile> findForCheckout(@Param("clientId") long clientId);

    /** 首次批准时写入该法律主体资料的批准时间；已写入的不覆盖，返回影响行数。 */
    int markApproved(@Param("partyId") long partyId, @Param("now") Instant now);
}
