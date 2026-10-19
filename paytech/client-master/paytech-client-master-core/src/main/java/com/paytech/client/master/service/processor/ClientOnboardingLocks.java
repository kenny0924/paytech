package com.paytech.client.master.service.processor;

import java.util.List;

/**
 * 入驻申请写入口共用的资源锁 Key
 * <li>保持原注解锁 {@code prefix:key} 的完整逻辑 Key，滚动发布期间新旧实例仍对同一资源互斥。</li>
 * <li>等待与租约沿用 CCT 注解锁配置 {@code redis.coordination.resource-lock}，由装配类从配置注入。</li>
 */
final class ClientOnboardingLocks {

    /** 仅提供 Key。 */
    private ClientOnboardingLocks() { }

    /** 申请资源：草稿保存、人员、关系、材料与正式提交共用。 */
    static String application(long applicationId) {
        return "client-onboarding:application:" + applicationId;
    }

    /** 账号未完成申请名额：同一账号的新申请串行分配。 */
    static String accountSlot(long accountId) {
        return "client-onboarding:account-slot:" + accountId;
    }

    /** 人员（Party）资源：保存人员与以该人员为对象的新建关系共用。 */
    static String party(long partyId) {
        return "cct:party:" + partyId;
    }

    /** 申请与人员两把锁，由模板去重排序后在同一等待预算内获取。 */
    static List<String> applicationAndParty(long applicationId, long partyId) {
        return List.of(application(applicationId), party(partyId));
    }
}
