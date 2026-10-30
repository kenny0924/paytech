package com.paytech.payout.integration;

import com.paytech.payout.domain.BeneficiaryNotification;

/**
 * Platform 通知的出站端口（technique 8.3）
 * <li>收件人由 Platform 按 Owner 租户内具备指定权限的有效成员解析；payout 不持有任何邮箱。</li>
 * <li>只在补偿任务中、数据库事务外调用；同一 sourceEventKey 重复提交时 Platform 返回 409，调用方视为已受理。</li>
 */
public interface BeneficiaryNotificationClient {
    /**
     * 提交一次通知
     *
     * @param notification 模板、收件人判定与参数
     * @return Platform 受理的投递数；0 表示该租户没有符合条件的收件人
     */
    int submit(BeneficiaryNotification notification);
}
