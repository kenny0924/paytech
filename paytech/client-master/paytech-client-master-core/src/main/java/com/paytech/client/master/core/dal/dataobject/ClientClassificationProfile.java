package com.paytech.client.master.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** Client 经营资料上的运营分类归属与确认（t_client_business_profile 的分类相关列）。 */
@Getter
@Setter
public class ClientClassificationProfile {
    /** 经营资料 ID。 */
    private long businessProfileId;
    /** Client ID。 */
    private long clientId;
    /**
     * 分类所属 Risk 主审核任务
     * <li>尚未设置为空；退回重提后由新任务的 O03 重新绑定。</li>
     */
    private Long classificationCaseId;
    /** 确认人 Account ID；未确认为空。 */
    private Long classificationConfirmedBy;
    /** 确认 UTC 时间；为空表示仍可修改。 */
    private Instant classificationConfirmedTime;
    /** 最近更新 UTC 时间。 */
    private Instant updateTime;

    /** 是否已为指定任务确认。 */
    public boolean confirmedFor(long caseId) {
        return Long.valueOf(caseId).equals(classificationCaseId) && classificationConfirmedTime != null;
    }
}
