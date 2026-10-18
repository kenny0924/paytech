package com.paytech.client.master.domain.onboarding;

import java.time.Instant;
import java.util.List;

import com.paytech.client.master.core.dal.dataobject.ClientApplicationDocument;
import com.paytech.client.master.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.master.core.dal.dataobject.ClientDraftIdentity;
import com.paytech.client.master.core.dal.dataobject.ClientPartyRelation;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** C14 一次提交的完整上下文；检查全部通过后一次构造，贯穿保存与响应。 */
@Getter
@AllArgsConstructor
public class SubmitClientApplicationContext {
    /** 申请资料归属。 */
    private final ClientDraftIdentity identity;
    /** 申请提交坐标。 */
    private final ClientApplicationSubmission application;
    /** 受审主体，第一项为主主体。 */
    private final List<ClientSubmissionSubject> subjects;
    /** 提交范围内的关系。 */
    private final List<ClientPartyRelation> relations;
    /** 材料引用。 */
    private final List<ClientApplicationDocument> documents;
    /** 本次生成的提交编号。 */
    private final String submissionId;
    /** 本次提交时间。 */
    private final Instant now;
}
