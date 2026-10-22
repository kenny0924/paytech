package com.paytech.channel.connector.api.channel.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 渠道在本环境的一个出站地址。 */
@Getter
@Setter
public class ChannelEndpointBody {
    /**
     * 地址代码
     * <li>渠道内唯一，原子 API 的 transport.endpointCode 引用它。</li>
     */
    @NotBlank(message = "地址代码必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "编码只能由大写字母开头，包含大写字母、数字或下划线，最多 64 字符")
    private String endpointCode;

    /**
     * 基础地址
     * <li>协议受部署允许列表限制（默认只允许 https），必须有主机名，可带路径前缀，不允许用户信息、查询串和片段，最长 512 字符。</li>
     * <li>不能是回环、私网、链路本地、保留或组播 IP 字面量，也不能是 localhost（部署放行网段除外）。</li>
     */
    @NotBlank(message = "基础地址必填")
    @Size(max = 512, message = "基础地址最长 512 字符")
    private String baseUrl;

    /**
     * 是否沙箱地址
     * <li>可选，缺省为 false；只有沙箱地址允许沙箱验证运行。</li>
     */
    private Boolean sandbox;
}
