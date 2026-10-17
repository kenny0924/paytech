package com.paytech.platform.support.api.clientwebhook.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 客户新建 Webhook 端点；新建没有版本，规则与运营代配置相同，没有操作原因字段。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientCreateWebhookEndpointRequestBody {

    /** HTTPS 接收地址，最长 1000 字符；保存时解析 DNS，全部地址必须是公网地址。 */
    @NotBlank
    @Size(max = 1000)
    private String url;

    /** 订阅事件类型：非空、不重复，只能是 V1 公开事件类型。 */
    @NotEmpty
    @Size(max = 8)
    private List<@NotNull String> eventTypes;
}
