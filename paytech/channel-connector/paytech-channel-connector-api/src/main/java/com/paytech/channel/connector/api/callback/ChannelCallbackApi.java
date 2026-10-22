package com.paytech.channel.connector.api.callback;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Pattern;

/** 渠道支付结果通知入站（3DS 全链路 technique 3.3.4）：只接受 Gateway CHANNEL_CALLBACK 路由的原样转发。 */
@HttpExchange("/channel-callbacks/v1")
public interface ChannelCallbackApi {

    /**
     * 接收一条渠道支付结果通知
     *
     * @param channelCode
     *            渠道编码（例如 PAYERMAX），须为启用的支付渠道且配置了通知定义
     * @param channelAccountId
     *            渠道技术账号 ID，正十进制 long 字符串，须属于该渠道且有 Vault 配置
     * @param headers
     *            请求头；签名头名称取自渠道通知定义（PayerMax 为 sign）
     * @param body
     *            渠道原始 Body 字节，按原文验签，不保存、不进日志
     * @return 验签通过（含重复通知、无法映射的通知）返回 200 与通知定义的应答体
     *         <li>渠道、账号或通知定义不可用返回 404，不入库；验签失败入库留痕后返回 401 Problem（CHANNEL_CALLBACK_SIGNATURE_INVALID）。</li>
     *         <li>已验证的内部调用方不是 api-gateway 时返回 403。</li>
     */
    @PostExchange("/{channelCode}/{channelAccountId}")
    ResponseEntity<String> receive(
        @PathVariable("channelCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "渠道编码格式无效") String channelCode,
        @PathVariable("channelAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}", message = "渠道账号 ID 必须为无前导零的正整数字符串")
        @DecimalMax(value = "9223372036854775807", message = "渠道账号 ID 超出有效范围") String channelAccountId,
        @RequestHeader HttpHeaders headers, @RequestBody byte[] body);
}
