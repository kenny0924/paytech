package com.paytech.client.console.bff.service.processor.request;

import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.platform.support.api.clientinvitation.request.ClientInvitationProofRequestBody;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已登录账号查看邀请详情（I02）的内部请求。 */
@Getter
@AllArgsConstructor
public class ClientInvitationDetailsRequest {

    /**
     * 入站边界已解析的可信身份
     * <li>入站拦截器已要求账号上下文。</li>
     */
    @NotNull(message = "可信身份不能为空")
    private final ClientActor actor;

    /**
     * 链接证明
     * <li>入口已校验格式，原样交给 Platform 校验真实性。</li>
     */
    @NotNull(message = "链接证明不能为空")
    private final ClientInvitationProofRequestBody proof;
}
