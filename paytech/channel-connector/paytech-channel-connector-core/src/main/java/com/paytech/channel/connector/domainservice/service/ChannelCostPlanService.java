package com.paytech.channel.connector.domainservice.service;

import java.time.Instant;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlan;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.channel.connector.core.enums.ChannelCostPlanStatus;
import com.paytech.channel.connector.domain.ChannelCostPlanSummaryView;
import com.paytech.channel.connector.domain.ChannelCostPlanView;
import com.paytech.channel.connector.domain.ChannelCostVersionContext;
import com.paytech.channel.connector.domain.ChannelCostVersionView;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

/**
 * 渠道成本方案与版本的本地保存、状态迁移与查询
 * <li>写入事务统一先锁方案行、再锁目标版本行；状态迁移以原状态为条件，当前版本切换以方案版本为条件。</li>
 * <li>只操作本服务数据库，不调用外部依赖。</li>
 */
public interface ChannelCostPlanService {

    /** 写入 DRAFT 方案；Channel 或方案代码重复时按并发冲突拒绝。 */
    void createPlan(ChannelCostPlan plan);

    /** 读取方案；不存在时业务拒绝。 */
    ChannelCostPlan loadPlan(String planCode);

    /** 读取方案、版本、适用范围与成本组成；方案或版本不存在时业务拒绝。 */
    ChannelCostVersionContext loadVersion(String planCode, int costVer);

    /**
     * 读取方案当前版本指针指向的版本
     *
     * @return 方案尚无当前版本时为空；指针无对应版本属于数据不变量破坏
     */
    ChannelCostPlanVer findCurrentVersion(ChannelCostPlan plan);

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
     * <li>未到生效时间：版本 APPROVED，当前指针不变，由到期切换 Job 在生效时间到达后切换。</li>
     */
    void approveVersion(ChannelCostVersionContext context, long checkerId, Instant approvedTime);

    /** 一个事务内锁方案与版本，PENDING_APPROVAL → REJECTED 并写入复核人。 */
    void rejectVersion(ChannelCostVersionContext context, long checkerId);

    /**
     * 一个事务内锁方案与版本，把已到生效时间的 APPROVED 版本切换为当前版本
     * <li>版本 APPROVED → ACTIVE；原当前版本 ACTIVE → INACTIVE 且失效时间为新版本生效时间；方案指针以方案版本 CAS 切换。</li>
     * <li>任一条件更新行数不符按并发冲突拒绝，整个事务回滚。</li>
     */
    void activateVersion(ChannelCostVersionContext context);

    /**
     * 锁方案与版本，作废未到生效时间的 APPROVED 版本
     * <li>必须在调用方 Processor 的事务内执行；CANCEL 变更记录由 Processor 在同一事务中写入。</li>
     * <li>版本 APPROVED → INACTIVE、失效时间等于生效时间；条件为 status = APPROVED 且 effective_from 晚于作废时刻，
     * 行数不为 1 时按并发冲突拒绝并整体回滚。</li>
     * <li>方案当前版本指针不指向 APPROVED 版本，不需要改动。</li>
     *
     * @param context 已校验可作废的版本上下文
     * @param now 作废时刻，与 CANCEL 变更记录的创建时间相同
     */
    void cancelVersion(ChannelCostVersionContext context, Instant now);

    /**
     * 分页查询方案列表项
     *
     * @param channelId 可空 Channel 筛选
     * @param status 可空方案状态筛选
     * @param paging 已校验的分页参数
     * @return 按方案更新时间、主键降序的分页结果
     */
    PagingResponseBody<ChannelCostPlanSummaryView> findPage(Long channelId, ChannelCostPlanStatus status,
        PagingRequestBody paging);

    /** 查询方案与全部版本摘要；不存在时业务拒绝。 */
    ChannelCostPlanView getPlan(String planCode);

    /** 查询版本详情；方案或版本不存在时业务拒绝。 */
    ChannelCostVersionView getVersion(String planCode, int costVer);
}
