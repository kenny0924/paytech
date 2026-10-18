package com.paytech.consumer.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.client.master.api.storedcard.ClientStoredCardSettingApi;
import com.paytech.client.master.api.storedcard.response.ClientStoredCardSettingResponseBody;
import com.paytech.consumer.test.mocker.external.ClientMasterStoredCardSettingMocker;

import lombok.AllArgsConstructor;

/** 在独立 Mock 服务继承 client-master 正式只读合同（无 Context Path），由场景配置 DTO。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockClientStoredCardSettingApi implements ClientStoredCardSettingApi {
    private final ClientMasterStoredCardSettingMocker mocker;

    @Override
    public ClientStoredCardSettingResponseBody storedCardSetting(String clientId) {
        var attributes = (ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();
        attributes.getResponse().setHeader("X-Request-Id", ClientMasterStoredCardSettingMocker.REQUEST_ID);
        return mocker.answer(clientId);
    }
}
