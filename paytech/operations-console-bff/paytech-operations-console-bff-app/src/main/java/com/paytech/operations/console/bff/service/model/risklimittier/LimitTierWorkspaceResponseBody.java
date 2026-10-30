package com.paytech.operations.console.bff.service.model.risklimittier;

import java.util.List;

import com.paytech.risk.decision.api.control.response.ClientTierDetailResponseBody;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Client 档位工作台（技术方案 8.4）：档位详情、变更历史、有效承保与上线复核清单系统项
 * <li>risk-decision 档位与历史失败整页失败；承保、资格、合同来源失败只把对应系统项标为 UNAVAILABLE。</li>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LimitTierWorkspaceResponseBody {
    /** Client 摘要。 */
    private LimitTierClientBody client;

    /** 档位详情（待审批摘要带申请人显示名）；无档位为 null。 */
    private ClientTierDetailResponseBody tier;

    /** 变更历史。 */
    private LimitTierHistoryPageBody history;

    /** 有效承保摘要；没有或来源不可用为 null。 */
    private LimitTierUnderwritingBody underwriting;

    /** 上线复核清单系统部分。 */
    private LimitTierChecklistBody checklist;

    /** 当前操作人是否持有档位申请权限（按申请路由实时授权判断）。 */
    private boolean canRequest;

    /** 可申请的目标档位：无档位、已有待审批申请或无申请权限时为空集合。 */
    private List<String> requestableTargets;
}
