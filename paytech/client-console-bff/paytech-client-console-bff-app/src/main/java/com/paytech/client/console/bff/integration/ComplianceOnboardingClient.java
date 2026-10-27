package com.paytech.client.console.bff.integration;

import com.paytech.client.console.bff.service.model.response.ReviewResult;

/** Compliance 客户可见审核结果的出站端口；材料要求已由 client-master 在采集 Schema 中合并。 */
public interface ComplianceOnboardingClient {

    /**
     * 读取申请最近一次审核的客户可见结果。
     *
     * @param applicationId 申请 ID
     * @param status client-master 当前申请状态（RETURNED、APPROVED、REJECTED 或 WITHDRAWN）
     * @return 与申请状态一致的审核结果；不一致说明投影尚未同步，按依赖不可用抛出
     */
    ReviewResult reviewResult(String applicationId, String status);
}
