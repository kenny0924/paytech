package com.paytech.client.master.domain.onboardingadmin;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.master.domain.onboarding.ClientDraftView;

/**
 * 运营 Client 详情的本库事实
 *
 * @param summary 列表行
 * @param applications 申请历史，新在前
 * @param latest 最近一份申请的资料快照；没有申请时为空
 */
public record ClientAdminDetail(ClientAdminView summary, List<ClientApplicationSubmission> applications, ClientDraftView latest) {
    /** 含个人资料，不输出到日志。 */
    @Override
    public String toString() {
        return "ClientAdminDetail[redacted]";
    }
}
