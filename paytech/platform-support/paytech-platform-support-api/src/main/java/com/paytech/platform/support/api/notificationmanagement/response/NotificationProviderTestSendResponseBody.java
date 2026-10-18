package com.paytech.platform.support.api.notificationmanagement.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 测试发送结果；ACCEPTED 只表示服务商受理，不表示已送达。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationProviderTestSendResponseBody {

    /**
     * 结果分类
     * <li>ACCEPTED：服务商已受理；REJECTED：服务商拒绝；UNKNOWN：结果未知（如超时）。</li>
     */
    private String outcome;

    /**
     * 有界结果码
     * <li>如 SES_ACCEPTED、SES_CREDENTIAL_REJECTED、SES_SENDER_NOT_VERIFIED。</li>
     */
    private String resultCode;
}
