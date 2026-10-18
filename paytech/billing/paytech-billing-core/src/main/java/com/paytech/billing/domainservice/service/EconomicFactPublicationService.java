package com.paytech.billing.domainservice.service;

import java.time.Instant;

import com.paytech.billing.domain.EconomicFactContext;

/**
 * EconomicFactRecorded 发布入口使用的本地读取与发布标记
 * <li>与登记服务分开：登记服务在批准事务中依赖补偿任务登记器，而登记器的 Handler 注册表依赖发布 Handler，合并会形成装配环。</li>
 * <li>只操作本模块数据库，不调用外部依赖。</li>
 */
public interface EconomicFactPublicationService {

    /**
     * 读取待发布事实的头、全部明细行与币种精度
     *
     * @param factId 经济事实 ID
     * @return 上下文；事实不存在返回空
     */
    EconomicFactContext loadContext(long factId);

    /**
     * 首次发布确认后写入 published_at
     *
     * @return 本次是否写入；补偿重试再次发布时为 false
     */
    boolean markPublished(EconomicFactContext context, Instant now);
}
