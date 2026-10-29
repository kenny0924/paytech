package com.paytech.client.master.integration;

import java.util.List;

/** commercial 产品能力目录出站端口：只读 ACTIVE 的支付方式；只能在数据库事务外调用。 */
public interface ClientPaymentMethodCatalogClient {
    /**
     * 查询可供 Client 期望开通的支付方式
     *
     * @return commercial 中 capability_kind=PAYMENT_METHOD 且 ACTIVE 的 capability_code，保持 commercial 返回顺序（可开通项 ID 升序）、去重
     *         <li>不按产品与辖区过滤（目录无辖区维度）；不缓存，每次调用读取一次。</li>
     *         <li>commercial 不可用或响应不合法时失败关闭为 ONBOARDING_DEPENDENCY_UNAVAILABLE（503）。</li>
     */
    List<String> activePaymentMethodCodes();
}
