package com.paytech.platform.support.integration.domainservice.function;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.integration.domain.execution.ExecutionErrorException;
import com.paytech.platform.support.integration.domain.execution.ProtectedHandle;
import com.paytech.platform.support.integration.domain.execution.VerifyAccountMaterial;

/**
 * 沙箱验证运行中的 CHANNEL_ACCOUNT_CONFIG（沙箱验证技术方案第 2 节）：账号来自验证账号而不是 Vault，
 * 忽略 vaultToken 参数；与正式读取相同地核对 channelCode、channelAccountId、entityNo，返回结构相同
 * （parameters 为字符串，credentials 为受控句柄）。失败只报固定文字。
 */
@Component
public class VerifyAccountConfigFunction {

    /**
     * 以验证账号代替 Vault 读取结果。
     *
     * @param args 已按签名完成必填与类型检查的参数
     * @param account 验证账号材料
     * @return 写入 resources.&lt;id&gt; 的结果
     */
    public Map<String, Object> apply(Map<String, Object> args, VerifyAccountMaterial account) {
        if (!account.getChannelCode().equals(args.get("channelCode"))
            || !account.getChannelAccountId().equals(args.get("channelAccountId"))
            || !account.getEntityNo().equals(args.get("entityNo"))) {
            throw new ExecutionErrorException("验证账号身份与传入身份不一致");
        }
        var credentials = new LinkedHashMap<String, Object>();
        account.getCredentials().forEach((name, value) -> credentials.put(name, new ProtectedHandle(value)));
        var result = new LinkedHashMap<String, Object>();
        result.put(ChannelAccountConfigFunction.PARAMETERS,
            Collections.unmodifiableMap(new LinkedHashMap<String, Object>(account.getParameters())));
        result.put(ChannelAccountConfigFunction.CREDENTIALS, Collections.unmodifiableMap(credentials));
        return result;
    }
}
