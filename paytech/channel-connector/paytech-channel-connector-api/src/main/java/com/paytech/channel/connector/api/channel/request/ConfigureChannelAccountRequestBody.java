package com.paytech.channel.connector.api.channel.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 写入渠道技术账号的参数与凭据
 * <li>整体替换：服务端用本次完整内容生成新的 Vault REFERENCE Token，旧 Token 不回收。</li>
 * <li>键为 [A-Za-z][A-Za-z0-9_.-]{0,63}，值 1–8192 字符且不能全为空白，各最多 64 项；与执行时的账号配置读取规则一致。</li>
 * <li>凭据值不进入日志、异常信息、变更记录或任何响应；本对象不提供打印字段的 toString。</li>
 */
@Getter
@Setter
public class ConfigureChannelAccountRequestBody {
    /** 渠道账号参数，例如 outletId */
    @NotNull(message = "参数集合必填")
    @Size(max = 64, message = "参数最多 64 项")
    private Map<@Pattern(regexp = "[A-Za-z][A-Za-z0-9_.-]{0,63}", message = "参数名称不符合约束") String,
        @NotBlank(message = "参数值不能为空") @Size(max = 8192, message = "参数值最长 8192 字符") String> parameters;

    /** 渠道 API 密钥等凭据 */
    @NotNull(message = "凭据集合必填")
    @Size(max = 64, message = "凭据最多 64 项")
    private Map<@Pattern(regexp = "[A-Za-z][A-Za-z0-9_.-]{0,63}", message = "凭据名称不符合约束") String,
        @NotBlank(message = "凭据值不能为空") @Size(max = 8192, message = "凭据值最长 8192 字符") String> credentials;

    /** 只输出类型名，避免凭据进入日志。 */
    @Override
    public String toString() {
        return "ConfigureChannelAccountRequestBody[redacted]";
    }
}
