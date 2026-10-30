package com.paytech.payout.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.payout.test.mocker.external.PlatformNotificationMocker;
import com.paytech.platform.support.api.notification.NotificationCommandApi;
import com.paytech.platform.support.api.notification.request.SubmitNotificationRequestBody;
import com.paytech.platform.support.api.notification.response.SubmitNotificationResponseBody;

import lombok.AllArgsConstructor;

/**
 * 仅存在于独立测试 HTTP 服务的 Platform 通知接口（提供方 NotificationCommandApi）实现，委托 PlatformNotificationMocker
 * <li>与真实 Platform 一样挂在 /platform-support 上下文路径下；类上的 @RequestMapping 会取代接口类型级 @HttpExchange 的前缀，
 * 因此在这里写全 /platform-support/notifications/v1，方法路径 /requests 仍取自接口。</li>
 */
@RestController
@RequestMapping("/platform-support/notifications/v1")
@TestComponent
@AllArgsConstructor
public class MockPlatformNotificationController implements NotificationCommandApi {
    private final PlatformNotificationMocker platform;

    /** 提交通知。 */
    @Override
    public SubmitNotificationResponseBody submitNotification(String callerService, SubmitNotificationRequestBody body) {
        return platform.submit(callerService, body);
    }
}
