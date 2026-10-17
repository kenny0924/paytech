package com.paytech.vault.client.admin.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 维护后的数据类型与新策略版本。
 */
@Getter
@Setter
public class DataTypeResponseBody {
    /**
     * 保存后的数据类型
     */
    private DataType dataType;

    /**
     * 本次变更产生的策略版本号
     * <li>Vault 在下一次定时刷新（默认 10 秒内）后生效。</li>
     */
    private int policyVersion;
}
