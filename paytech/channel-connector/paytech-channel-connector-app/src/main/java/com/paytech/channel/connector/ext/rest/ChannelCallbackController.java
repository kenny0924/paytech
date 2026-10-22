package com.paytech.channel.connector.ext.rest;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.channel.connector.api.callback.ChannelCallbackApi;
import com.paytech.channel.connector.api.enums.ChannelCallbackErrorCode;
import com.paytech.channel.connector.service.processor.ReceiveChannelCallbackProcessor;
import com.paytech.channel.connector.service.processor.request.ReceiveChannelCallbackRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.internalauth.api.CallerIdentity;
import com.paytech.foundation.internalauth.api.CallerIdentityHolder;

import lombok.AllArgsConstructor;

/**
 * 渠道支付通知入站（3DS 全链路 technique 3.3.4）：协议适配后委托 Processor，应答体与媒体类型取自渠道通知定义。
 * <li>只接受 Gateway 转发：携带已验证内部 Token 时调用方必须是 {@value #GATEWAY_CALLER}；internal-auth 关闭时依赖私网部署边界。</li>
 * <li>原始 Body 只交给 Processor 验签与解析，不记录日志（Web 日志对全部路径只记元数据）。</li>
 */
@RestController
@AllArgsConstructor
public class ChannelCallbackController implements ChannelCallbackApi {
    /** 唯一允许的内部调用方。 */
    static final String GATEWAY_CALLER = "api-gateway";

    final ReceiveChannelCallbackProcessor receiveChannelCallbackProcessor;

    /** 接收一条渠道支付通知。 */
    @Override
    public ResponseEntity<String> receive(String channelCode, String channelAccountId, HttpHeaders headers, byte[] body) {
        checkCaller();
        var request = new ReceiveChannelCallbackRequest();
        request.setChannelCode(channelCode);
        request.setChannelAccountId(Long.valueOf(channelAccountId));
        request.setHeaders(lowerCase(headers));
        request.setBody(body == null ? new byte[0] : body);
        var response = receiveChannelCallbackProcessor.process(request);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(response.getContentType())).body(response.getBody());
    }

    /** 已验证的内部调用方必须是 Gateway。 */
    static void checkCaller() {
        CallerIdentity identity = CallerIdentityHolder.current();
        if (identity != null && identity.verified() && !GATEWAY_CALLER.equals(identity.caller())) {
            throw new FailException(ChannelCallbackErrorCode.CHANNEL_CALLBACK_CALLER_FORBIDDEN);
        }
    }

    /** 请求头名称转小写，多值取第一个。 */
    static Map<String, String> lowerCase(HttpHeaders headers) {
        var result = new LinkedHashMap<String, String>();
        headers.forEach((name, values) -> {
            if (!values.isEmpty()) {
                result.putIfAbsent(name.toLowerCase(Locale.ROOT), values.getFirst());
            }
        });
        return result;
    }
}
