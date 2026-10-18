package com.paytech.commercial.domainservice.service;

import com.paytech.commercial.domain.CommercialCheckoutFacts;
import com.paytech.commercial.domain.CommercialCheckoutScope;

/** commercial 本地商务事实查询能力；不负责外部资格、client-master 调用或 HTTP。 */
public interface CheckoutQueryService {
    /**
     * 在一个只读快照内读取解析范围内的全部商务事实。
     *
     * @param scope client-master 返回的 Client、OE 与 Local Partner 范围
     * @return 商务原始事实；数据库不可用时抛出 OWNER_UNAVAILABLE
     */
    CommercialCheckoutFacts load(CommercialCheckoutScope scope);
}
