package com.paytech.client.master.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.ClientBusinessProfile;

/** t_client_business_profile 的持久化查询。 */
public interface ClientBusinessProfileMapper {
    /** 读取本 Client Checkout 范围内的 client_business_profile 记录，保留历史和未生效事实供业务判断。 */
    List<ClientBusinessProfile> findByClientId(@Param("clientId") long clientId);

    /** 某 Client 唯一经营资料的分类归属；不存在返回 null。 */
    com.paytech.client.master.core.dal.dataobject.ClientClassificationProfile findClassificationProfile(@Param("clientId") long clientId);

    /** 把分类绑定到指定任务并清除确认；本任务已确认时不更新，返回影响行数。 */
    int bindClassificationCase(@Param("row") com.paytech.client.master.core.dal.dataobject.ClientClassificationProfile row,
        @Param("caseId") long caseId, @Param("now") java.time.Instant now);

    /** 本任务未确认时写入确认人与时间，返回影响行数。 */
    int confirmClassification(@Param("row") com.paytech.client.master.core.dal.dataobject.ClientClassificationProfile row,
        @Param("actorId") long actorId, @Param("now") java.time.Instant now);
}
