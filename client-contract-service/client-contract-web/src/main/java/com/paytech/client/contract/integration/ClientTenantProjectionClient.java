package com.paytech.client.contract.integration;

import com.paytech.client.contract.domain.onboarding.ClientProjectionModels.*;

/** Platform PLT-I17 的窄出站端口，所有调用必须在数据库事务之外。 */
public interface ClientTenantProjectionClient {
    /** 无投影时返回 null；服务不可用不得冒充不存在。 */
    Position find(long clientId);
    /** 提交原始来源身份，不生成新来源或自动更换申请人。 */
    Position project(Source source);
}
