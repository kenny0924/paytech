package com.paytech.client.master.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.PartyProfile;

/** t_party_profile 的持久化查询与首次批准时间写入。 */
public interface PartyProfileMapper {
    /** 读取 Client 本身法律主体的资料，供候选查询判断主体是否已批准。 */
    List<PartyProfile> findForCheckout(@Param("clientId") long clientId);

    /** 按资料 ID 读取合同签署校验需要的资料；ids 必须非空。 */
    List<PartyProfile> findByIds(@Param("ids") java.util.Collection<Long> ids);

    /** 首次批准时写入该法律主体资料的批准时间；已写入的不覆盖，返回影响行数。 */
    int markApproved(@Param("partyId") long partyId, @Param("now") Instant now);
}
