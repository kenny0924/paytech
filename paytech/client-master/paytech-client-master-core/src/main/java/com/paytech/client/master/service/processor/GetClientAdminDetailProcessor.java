package com.paytech.client.master.service.processor;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.master.domain.onboardingadmin.ClientAdminDetail;
import com.paytech.client.master.domain.onboardingadmin.ClientAdminView;
import com.paytech.client.master.domainservice.service.ClientDraftService;
import com.paytech.client.master.domainservice.service.OnboardingAdminQueryService;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * CM-11 Client 详情：Client 行、申请历史与最近一份申请的资料快照（同一只读快照读取资料、人员、关系与属性）
 * <li>证件只含 Token 引用之外的脱敏值，联系方式不 Reveal；不调用外部服务。</li>
 */
@AllArgsConstructor
public class GetClientAdminDetailProcessor extends AbstractProcessor<Long, ClientAdminDetail> {
    private final OnboardingAdminQueryService queries;
    private final ClientDraftService drafts;
    private final TransactionTemplate reads;

    /** Client 不存在抛出 CLIENT_NOT_FOUND。 */
    @Override
    protected ClientAdminDetail processRequest(Long clientId) {
        ClientAdminView client = queries.client(clientId);
        var applications = queries.applications(clientId);
        var latest = client.getLatestApplicationId() == null ? null
            : reads.execute(status -> drafts.readForReview(client.getLatestApplicationId()));
        return new ClientAdminDetail(client, applications, latest);
    }
}
