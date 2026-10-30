package com.paytech.client.master.domainservice.service;

import com.paytech.client.master.domain.classification.ClientBusinessClassificationView;

/** Client 主经营分类的只读能力；不调用外部依赖。 */
public interface ClientBusinessClassificationQueryService {
    /**
     * 读取 Client 已确认的主经营分类
     *
     * @param clientId Client ID，必须为正数
     * @return 分类；没有经营资料或未确认时 confirmed=false、两码为空
     *         <li>Client 不存在抛出 CLIENT_NOT_FOUND（404）。</li>
     */
    ClientBusinessClassificationView getBusinessClassification(long clientId);
}
