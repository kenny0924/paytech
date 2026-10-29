package com.paytech.risk.control.domainservice.service;

import java.util.Optional;

import com.paytech.risk.control.domain.underwriting.EffectiveUnderwriting;

/**
 * Client 承保结论的同进程读取能力，供 REQ-04 限额档位使用（"无承保 = 额度为零"由 REQ-04 解释）
 * <li>只读本库 t_client_underwriting，不发起外部调用，可在事务内或事务外调用。</li>
 */
public interface ClientUnderwritingQueryService {
    /**
     * 读取 Client 当前有效的 APPROVED 承保结论
     *
     * @param clientId Client ID
     * @return 有效结论摘要；没有有效 APPROVED 时为空
     */
    Optional<EffectiveUnderwriting> findEffective(long clientId);
}
