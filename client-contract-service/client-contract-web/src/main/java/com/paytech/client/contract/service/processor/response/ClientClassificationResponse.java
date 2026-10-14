package com.paytech.client.contract.service.processor.response;

import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.ClientClassification;
import com.paytech.client.contract.core.dal.dataobject.ClientClassificationProfile;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 审核任务的分类坐标；由入站 Converter 转为 Wire，不跨模块。 */
@Getter
@AllArgsConstructor
public class ClientClassificationResponse {
    /** Risk 主审核任务 ID。 */
    private final long caseId;
    /** CCT 申请 ID。 */
    private final long applicationId;
    /** 经营资料分类归属。 */
    private final ClientClassificationProfile profile;
    /** 本任务分类；未绑定本任务时为空列表。 */
    private final List<ClientClassification> items;

    /** 是否已为本任务确认。 */
    public boolean confirmed() {
        return profile.confirmedFor(caseId);
    }
}
