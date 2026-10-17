package com.paytech.client.master.integration;

import java.util.Collection;

import com.paytech.client.master.core.dal.dataobject.ClientPersonIdentity;
import com.paytech.client.master.domain.onboarding.ClientAgreementPartyUsage;

/** commercial 合同参与方引用查询端口；只能在数据库事务外调用。 */
public interface ClientAgreementPartyClient {
    /**
     * 查询人员是否已被合同参与方引用。
     *
     * @param persons 待查人员；为空时不发起调用
     * @return 引用结果；commercial 不可用或响应不合法时返回失败关闭结果
     */
    ClientAgreementPartyUsage usage(Collection<ClientPersonIdentity> persons);
}
