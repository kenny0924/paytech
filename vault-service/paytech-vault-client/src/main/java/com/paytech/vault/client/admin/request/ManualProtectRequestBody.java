package com.paytech.vault.client.admin.request;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 人工加密请求；用途固定为 OPERATIONS_MANUAL，Token 模式取自数据类型。
 */
@Getter
@Setter
public class ManualProtectRequestBody {
    /**
     * 数据类型编码
     * <li>须为 ACTIVE 且开启人工加密。</li>
     */
    private String dataTypeCode;

    /**
     * 待加密的原值
     * <li>合法 UTF-8，不超过 32 KiB；不得写入日志。</li>
     */
    private String value;

    /**
     * 理由码
     * <li>CUSTOMER_VERIFICATION、DATA_REPAIR、CHANNEL_CONFIGURATION、INCIDENT_INVESTIGATION 或 OTHER。</li>
     */
    private String reasonCode;

    /**
     * 工单号
     * <li>1..64 位字母、数字或 . _ # -。</li>
     */
    private String ticketRef;

    /**
     * 本次路由允许的数据级别
     * <li>由 BFF 按路由填写：普通路由 [STANDARD]，密钥路由 [STANDARD, SECRET]；不接受页面传入。</li>
     */
    private List<String> allowedDataClasses;
}
