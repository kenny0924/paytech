package com.paytech.operations.console.bff.service.model.risklimittier;

import com.paytech.risk.decision.api.control.response.ClientTierPendingChangeBody;

import lombok.Getter;
import lombok.Setter;

/** 待审批申请摘要页面模型：Risk 字段加申请人显示名；作为列表行与详情中 pendingChange 的运行时类型序列化。 */
@Getter
@Setter
public class LabeledTierPendingChangeBody extends ClientTierPendingChangeBody {
    /** 申请人显示名；查不到为 null。 */
    private String requestedByLabel;

    /**
     * 复制 Risk 摘要并附显示名
     *
     * @param source Risk 摘要
     * @param requestedByLabel 申请人显示名
     * @return 页面模型
     */
    public static LabeledTierPendingChangeBody of(ClientTierPendingChangeBody source, String requestedByLabel) {
        LabeledTierPendingChangeBody body = new LabeledTierPendingChangeBody();
        body.setChangeId(source.getChangeId());
        body.setFromTier(source.getFromTier());
        body.setToTier(source.getToTier());
        body.setApprovalLevel(source.getApprovalLevel());
        body.setRequestedBy(source.getRequestedBy());
        body.setRequestedTime(source.getRequestedTime());
        body.setRequestedByLabel(requestedByLabel);
        return body;
    }
}
