package com.paytech.vault.client.admin.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 人工解密结果；原值只在当前请求内存中使用，不得缓存、持久化或写入日志。
 */
@Getter
@Setter
public class ManualRevealResponseBody {
    /**
     * Token 所属数据类型编码
     */
    private String dataTypeCode;

    /**
     * 受保护的原值
     * <li>合法 UTF-8，不超过 32 KiB。</li>
     */
    private String value;
}
