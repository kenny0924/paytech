package com.paytech.client.master.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.PartyProfile;

/** t_party_profile 的本地仓储；不负责其他表的加载或事务。 */
public interface PartyProfileRepository {
    /** 读取 Client 本身法律主体的资料，供候选查询判断主体是否已批准。 */
    List<PartyProfile> findForCheckout(long clientId);

    /** 按资料 ID 读取资料；ids 为空时返回空列表。 */
    List<PartyProfile> findByIds(java.util.Collection<Long> ids);

    /** 按资料 ID 读取参与方资料与筛查用姓名；空集合不访问数据库。 */
    List<com.paytech.client.master.domain.checkout.PartyProfileView> findViewsByIds(java.util.Collection<Long> ids);

    /**
     * 首次批准：写批准时间与内容摘要并清空草稿归属
     * <li>只更新当前资料中 approved_time 为空的一行；影响行数不是 1 时抛出，由调用方事务整体回滚。</li>
     *
     * @param partyId 主主体
     * @param contentHash 资料内容 SHA-256 小写十六进制
     * @param now 批准时间
     */
    void markApproved(long partyId, String contentHash, Instant now);
}
