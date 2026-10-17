package com.paytech.commercial.domainservice.service;

import com.paytech.commercial.domain.*;

/** CCT 本地查询能力；不负责外部资格或 HTTP。 */
public interface CheckoutQueryService {
    /** 从同一读快照取得按优先级排列的本地商业候选及相关 Root。 */
    CheckoutResolutionContext load(CheckoutQuery query);
    /** 外部资格检查后复核原选择，变化时返回 CCT_CONTEXT_CHANGED。 */
    void verify(CheckoutResolutionContext context, CheckoutCandidateContext candidate);
}
