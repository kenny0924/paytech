package com.paytech.billing.domainservice.service;

import java.time.Instant;

import com.paytech.billing.core.dal.dataobject.ChannelCostPlan;
import com.paytech.billing.domain.ChannelCostVersionContext;
import com.paytech.billing.domain.view.ChannelCostPlanView;
import com.paytech.billing.domain.view.ChannelCostVersionView;

/**
 * 渠道成本方案与版本的本地保存、状态迁移与查询
 * <li>写入事务统一先锁方案行、再锁目标版本行；状态迁移以原状态为条件，当前版本切换以方案版本为条件。</li>
 * <li>只操作本模块数据库，不调用外部依赖。</li>
 */
public interface ChannelCostPlanService {

    /** 写入 DRAFT 方案；Channel 或方案代码重复时按并发冲突拒绝。 */
    void createPlan(ChannelCostPlan plan);

    /** 读取方案；不存在时业务拒绝。 */
    ChannelCostPlan loadPlan(String planCode);

    /** 读取方案、版本、适用范围、成本组成与范围币种精度；方案或版本不存在时业务拒绝。 */
    ChannelCostVersionContext loadVersion(String planCode, int costVer);

    /**
     * 一个事务内锁方案、复验无 DRAFT/PENDING_APPROVAL 版本、写入 DRAFT 版本及全部适用范围与成本组成
     * <li>已有待处理版本或版本号重复时按并发冲突拒绝。</li>
     */
    void createVersion(ChannelCostVersionContext context);

    /** 一个事务内锁方案与版本，DRAFT → PENDING_APPROVAL。 */
    void submitVersion(ChannelCostVersionContext context);

    /**
     * 一个事务内锁方案与版本并写入批准结果
     * <li>已到生效时间：版本 ACTIVE、原当前版本 INACTIVE 且失效时间为新版本生效时间、方案当前指针切换并置为 ACTIVE。</li>
     * <li>未到生效时间：版本 APPROVED，当前指针不变。</li>
     */
    void approveVersion(ChannelCostVersionContext context, long checkerId, Instant approvedTime);

    /** 一个事务内锁方案与版本，PENDING_APPROVAL → REJECTED 并写入复核人。 */
    void rejectVersion(ChannelCostVersionContext context, long checkerId);

    /** 查询方案与全部版本摘要；不存在时业务拒绝。 */
    ChannelCostPlanView getPlan(String planCode);

    /** 查询版本详情；方案或版本不存在时业务拒绝。 */
    ChannelCostVersionView getVersion(String planCode, int costVer);
}
