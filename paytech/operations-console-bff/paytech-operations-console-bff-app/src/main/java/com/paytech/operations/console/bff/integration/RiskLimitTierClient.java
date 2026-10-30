package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.risk.decision.api.control.request.DecideClientTierChangeRequestBody;
import com.paytech.risk.decision.api.control.request.RequestClientTierChangeRequestBody;
import com.paytech.risk.decision.api.control.request.WithdrawClientTierChangeRequestBody;
import com.paytech.risk.decision.api.control.response.ClientTierChangeListResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierChangeResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierDetailResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierSummaryBody;
import com.paytech.risk.decision.api.control.response.TierQuotaListResponseBody;
import com.paytech.risk.decision.api.control.response.TierStatisticsResponseBody;

/**
 * risk-decision Client 限额档位的出站端口（REQ-04 L1—L11）；单次调用，不自动重试
 * <li>写命令的 Owner 字段错误投影到本次转发给 Risk 的 Body 路径，不传播远端文案。</li>
 * <li>回执须对应请求的 Client 或申请，否则按上游无效响应处理。存量迁移 L12 不经 BFF。</li>
 */
public interface RiskLimitTierClient {
    /** L1 档位分页；筛选均可为空。 */
    PagingResponseBody<ClientTierSummaryBody> list(OperatorContext operator, String tier, Boolean pendingApproval,
        Boolean observationDue, String keyword, int page, int size);

    /** L2 档位详情；无档位时 tier 为 null。 */
    ClientTierDetailResponseBody detail(OperatorContext operator, String clientId);

    /** L3 变更历史。 */
    ClientTierChangeListResponseBody history(OperatorContext operator, String clientId);

    /** L4 提交变更申请。 */
    ClientTierChangeResponseBody request(OperatorContext operator, String clientId, RequestClientTierChangeRequestBody body);

    /** L5 撤回。 */
    ClientTierChangeResponseBody withdraw(OperatorContext operator, String changeId, WithdrawClientTierChangeRequestBody body);

    /**
     * L6—L9 审批或驳回
     *
     * @param operator 当前操作人
     * @param changeId 申请 ID
     * @param approve true 批准、false 驳回
     * @param elevated true 走高级审批接口
     * @param body 期望版本与意见
     * @return 处理后的申请
     */
    ClientTierChangeResponseBody decide(OperatorContext operator, String changeId, boolean approve, boolean elevated,
        DecideClientTierChangeRequestBody body);

    /** L10 四档 × 币种额度。 */
    TierQuotaListResponseBody quotas(OperatorContext operator);

    /** L11 列表统计。 */
    TierStatisticsResponseBody statistics(OperatorContext operator);
}
