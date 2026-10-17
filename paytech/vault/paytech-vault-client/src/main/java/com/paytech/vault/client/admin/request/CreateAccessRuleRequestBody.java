package com.paytech.vault.client.admin.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 新建服务访问规则；数据类型须存在，OPERATIONS_MANUAL 为保留用途。
 */
@Getter
@Setter
public class CreateAccessRuleRequestBody {
    /**
     * 调用服务名
     * <li>小写字母开头，1..128 位小写字母、数字或连字符。</li>
     */
    private String callerService;

    /**
     * 动作
     * <li>PROTECT 或 REVEAL。</li>
     */
    private String action;

    /**
     * 数据类型编码
     */
    private String dataTypeCode;

    /**
     * 访问用途编码
     */
    private String purposeCode;

    /**
     * 变更理由
     * <li>必填，1..500 个字符，写入策略变更记录。</li>
     */
    private String reason;
}
