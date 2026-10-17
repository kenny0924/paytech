package com.paytech.platform.support.accesscontrol.integration.mail;

import com.paytech.platform.support.accesscontrol.domain.authentication.ClientEmailChallengePolicy.LinkMaterial;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengePurpose;

/** 邮件出站端口；只在本地提交成功后直接发送，不持久化可消费的秘密。 */
public interface AuthenticationEmailSender {
    /** 发送固定站点的验证或恢复链接，不接受浏览器提供回调地址。 */
    void send(String canonicalEmail, ClientChallengePurpose purpose, LinkMaterial link);
}
