package com.paytech.client.contract.domain.onboarding;

import java.time.Instant;
import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.contract.core.dal.dataobject.ClientClassification;
import com.paytech.client.contract.core.dal.dataobject.ClientClassificationProfile;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** O03/O04 与 I02 交付共用：审核中的申请、经营资料分类归属与本任务分类行。 */
@Getter
@AllArgsConstructor
public class ClientClassificationContext {
    /** Risk 主审核任务 ID。 */
    private final long caseId;
    /** 审核中的申请。 */
    private final ClientApplicationSubmission application;
    /** 经营资料分类归属。 */
    private final ClientClassificationProfile profile;
    /** 分类行：O03 为待写入的新行，其余为已保存行。 */
    private final List<ClientClassification> classifications;
    /** 操作者 Account ID；交付读取时为空。 */
    private final Long actorId;
    /** 服务端时间；交付读取时为空。 */
    private final Instant now;

    /** 分类是否已为本任务确认。 */
    public boolean confirmed() {
        return profile.confirmedFor(caseId);
    }
}
