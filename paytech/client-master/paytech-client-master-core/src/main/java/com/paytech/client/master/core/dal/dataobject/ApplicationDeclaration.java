package com.paytech.client.master.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 申请声明证据（t_application_declaration），不可变、只追加。 */
@Getter
@Setter
public class ApplicationDeclaration {
    /** 声明行 ID。 */
    private Long declarationId;
    /** 所属 Client 申请 ID。 */
    private Long clientApplicationId;
    /** 声明类型。 */
    private String declarationType;
    /** 声明取值 TRUE 或 FALSE。 */
    private String declaredValue;
    /** 声明账号。 */
    private Long declaredByAccountId;
    /** 声明 UTC 时间。 */
    private Instant declaredTime;
    /** 可信客户端 IP，可空。 */
    private String clientIp;
    /** User-Agent，可空。 */
    private String userAgent;
    /** 条款版本，可空。 */
    private String termsVersion;
    /**
     * 声明针对的申请资料版本
     * <li>2026-10-27 之前的存量行为空，按 0 解释。</li>
     */
    private Long draftRevision;
    /** 创建 UTC 时间。 */
    private Instant createTime;

    /** 含受保护值的引用，不输出到日志。 */
    @Override
    public String toString() {
        return "ApplicationDeclaration[redacted]";
    }
}
