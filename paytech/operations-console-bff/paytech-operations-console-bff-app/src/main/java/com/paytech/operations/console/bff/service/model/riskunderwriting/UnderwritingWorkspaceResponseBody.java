package com.paytech.operations.console.bff.service.model.riskunderwriting;

import com.paytech.risk.decision.api.control.request.UnderwritingInputBody;
import com.paytech.risk.decision.api.control.response.UnderwritingPreviewResponseBody;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 承保工作台：输入快照（即将冻结）、来源可用性、自动承保判定与估算敞口预览，以及当前承保
 * <li>inputStatus：READY 可提交；DD_NOT_APPROVED 尽调未批准；CLASSIFICATION_NOT_CONFIRMED 分类未确认；
 * SOURCE_UNAVAILABLE 有来源读取失败（页面提示重试，不能提交）。</li>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnderwritingWorkspaceResponseBody {
    /** Client 主档摘要。 */
    private UnderwritingClientBody client;

    /** 由 Owner 读取组装的输入快照；缺失项为 null（页面显示"未采集"）。 */
    private UnderwritingInputBody input;

    /** 输入状态。 */
    private String inputStatus;

    /** 各来源读取结果。 */
    private UnderwritingSourcesBody sources;

    /** 自动承保判定与估算敞口；当前操作人没有维护权限时为 null。 */
    private UnderwritingPreviewResponseBody preview;

    /** 当前有效、进行中与最新版本。 */
    private UnderwritingCurrentPageBody current;

    /** 当前操作人是否持有承保维护权限（按预览路由授权判断）。 */
    private boolean canManage;

    /** 是否可提交：输入 READY 且有维护权限。 */
    private boolean canSubmit;
}
