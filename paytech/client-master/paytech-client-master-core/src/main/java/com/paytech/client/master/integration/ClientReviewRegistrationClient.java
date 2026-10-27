package com.paytech.client.master.integration;

import com.paytech.client.master.domain.onboarding.ClientReviewSourceView;

/** compliance I01 审核登记、I02 分类交付与 I03 撤回出站端口；只能在数据库事务外调用。 */
public interface ClientReviewRegistrationClient {
    /**
     * 按提交编号登记审核任务
     *
     * @param source 提交事实
     * @return Risk 主审核任务 ID
     *         <li>Risk 明确拒绝抛出 FailException；网络或依赖不可用抛出其他运行时异常，调用方用同一提交编号重试。</li>
     */
    long register(ClientReviewSourceView source);

    /**
     * 把已确认的本任务分类交给 Risk
     *
     * @param source 审核中的申请、确认人与分类
     *         <li>Risk 明确拒绝抛出 FailException（任务已结束为 CLIENT_REVIEW_CLOSED）；网络或依赖不可用抛出其他运行时异常。</li>
     */
    void deliverClassifications(com.paytech.client.master.domain.onboarding.ClientClassificationContext source);

    /**
     * I03 撤回申请当前绑定的审核案件（主案件与进行中的子案件）
     *
     * @param caseId 申请撤回时绑定的审核案件
     * @param applicationId 申请 ID
     * @param accountId 发起撤回的 CUSTOMER 账号
     *         <li>已撤回幂等成功；案件已结束抛出 CLIENT_REVIEW_CLOSED 的 FailException；网络或依赖不可用抛出其他运行时异常。</li>
     */
    void withdraw(long caseId, long applicationId, long accountId);
}
