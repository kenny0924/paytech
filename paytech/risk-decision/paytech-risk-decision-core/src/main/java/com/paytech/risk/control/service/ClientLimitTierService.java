package com.paytech.risk.control.service;

import java.time.Instant;
import java.util.List;

import com.paytech.risk.control.core.dal.dataobject.ClientLimitTierChange;
import com.paytech.risk.control.domain.limittier.ClientTierChangeView;
import com.paytech.risk.control.domain.limittier.ClientTierDetailView;
import com.paytech.risk.control.domain.limittier.ClientTierPage;
import com.paytech.risk.control.domain.limittier.LimitTierApprovalContext;
import com.paytech.risk.control.domain.limittier.LimitTierEstablishContext;
import com.paytech.risk.control.domain.limittier.LimitTierListFilter;
import com.paytech.risk.control.domain.limittier.LimitTierStatistics;
import com.paytech.risk.control.domain.limittier.TierQuotaCatalog;

/**
 * Client 限额档位的本地保存与本库查询；调用方已完成归属、状态、版本与迁移规则校验
 * <li>查询经 LimitTierUsageReader 端口读取本库放行累计事件与风控常量，不发起外部调用。</li>
 */
public interface ClientLimitTierService {
    /**
     * 为尚无档位的 Client 建档：档位行、APPROVED 建档记录与 TIER 名单成员同一事务写入
     * <li>在调用方事务内执行时加入该事务（承保批准建档）；独立调用时自开短事务（存量迁移）。</li>
     *
     * @param context 已构造的三行
     * @return true 已建档；false 该 Client 已有档位（并发建档），三行均未写入
     */
    boolean establish(LimitTierEstablishContext context);

    /** 保存新的待审批申请；已有待审批申请时抛出 PENDING_EXISTS。 */
    void requestChange(ClientLimitTierChange change);

    /** 申请人撤回待审批申请。 */
    void withdraw(ClientLimitTierChange change, long actorAccountId, Instant now);

    /** 驳回待审批申请。 */
    void reject(ClientLimitTierChange change, long actorAccountId, String comment, Instant now);

    /** 批准申请：申请、档位行与 TIER 名单成员同一事务生效。 */
    void approve(LimitTierApprovalContext context);

    /**
     * 分页读取档位
     *
     * @param filter 筛选条件（含判断观察期到期的当前时间）
     * @param limit 每页条数
     * @param offset 偏移
     * @return 本页条目与总数；CRR 取当前有效承保，放行笔数只对本页 OBSERVATION 行计算
     */
    ClientTierPage findPage(LimitTierListFilter filter, int limit, long offset);

    /**
     * 读取 Client 档位详情
     *
     * @param clientId Client ID
     * @param now 当前时间，作为观察期与滚动窗口的终点
     * @return 档位、观察期进度、本档位按币种额度与已用；无档位时档位为空、额度为空集合
     */
    ClientTierDetailView findDetail(long clientId, Instant now);

    /** 按命名约定解析当前风控常量中的四档 × 币种额度。 */
    TierQuotaCatalog quotas();

    /** 读取 Client 的变更历史，申请时间倒序，最多 100 条。 */
    List<ClientTierChangeView> findHistory(long clientId);

    /** 列表顶部统计；观察期到期按 now 与目标天数判定。 */
    LimitTierStatistics statistics(Instant now);
}
