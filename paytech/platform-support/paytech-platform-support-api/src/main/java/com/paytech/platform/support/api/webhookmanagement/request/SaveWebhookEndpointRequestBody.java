package com.paytech.platform.support.api.webhookmanagement.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 新建或修改 Webhook 端点的地址与订阅事件类型。 */
@Getter
@Setter
public class SaveWebhookEndpointRequestBody {

    /**
     * Client 的 HTTPS 接收地址
     * <li>绝对 https 地址，最长 1000 字符；不含 userinfo 与 fragment，主机名不能是 localhost。</li>
     * <li>保存时解析 DNS，全部地址都必须是公网地址。</li>
     */
    @NotBlank
    @Size(max = 1000)
    private String url;

    /**
     * 订阅的事件类型
     * <li>非空、不重复，只能是 V1 八种公开事件类型。</li>
     */
    @NotEmpty
    @Size(max = 8)
    private List<@NotNull String> eventTypes;

    /**
     * 调用方看到的当前版本
     * <li>新建时不传或为 0；修改时必须等于当前版本，不一致返回 409。</li>
     */
    @PositiveOrZero
    private int expectedVersion;

    /**
     * 操作原因
     * <li>1..500 字符，写入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private String reason;
}
