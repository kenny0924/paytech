package com.paytech.client.master.domainservice.service;

import com.paytech.client.master.domain.onboarding.ClientFileTarget;

/** 入驻文件目标的 CCT 资源授权边界。 */
public interface ClientFileTargetService {

    /** 核对原账号、申请和主体，区分可读的历史目标与可继续上传的当前目标。 */
    ClientFileTarget read(long accountId, long applicationId, long subjectPartyId);
}
