package com.paytech.reconciliation.integration;

import java.util.List;

import com.paytech.reconciliation.domain.MidProfile;

/**
 * channel-connector MID 查询端口（只读；technique §1.2「channel-connector」）
 * <li>调用方须在本地事务之外调用（事务公理）；channel-connector 不可用时抛公共服务不可用（503），不当作 MID 不存在。</li>
 */
public interface ChannelMidClient {

    /**
     * 读取 MID 的状态、经营主体、渠道编码与能力组币种
     * <li>先读详情，再按经营主体、状态与外部 MID 编码查列表摘要取渠道编码。</li>
     *
     * @param midId channel-connector MID 主键
     * @return MID 事实；channel-connector 返回业务拒绝（如 MID 不存在）时为 null
     */
    MidProfile findMid(long midId);

    /**
     * 列出 ACTIVE MID 及其能力组币种（未归属 MID 计算）
     *
     * @param entityNo 可选经营主体编号
     * @return 按 MID 主键升序
     */
    List<MidProfile> listActiveMids(String entityNo);
}
