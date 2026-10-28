package com.paytech.client.master.api.onboarding.response;

import lombok.Getter;
import lombok.Setter;

/** CM-6 声明记录；只追加，同类型以最新一条为准。 */
@Getter
@Setter
public class DeclarationResponseBody {
    /** 声明记录 ID。 */
    private String declarationId;

    /** 申请 ID。 */
    private String applicationId;

    /** 声明类型。 */
    private String declarationType;

    /** 声明取值 TRUE 或 FALSE。 */
    private String declaredValue;

    /** 条款版本，可空。 */
    private String termsVersion;

    /** 作出声明的 Platform CUSTOMER Account ID，十进制字符串。 */
    private String declaredByAccountId;

    /** 声明 UTC 时间（ISO-8601，微秒）。 */
    private String declaredAt;

    /**
     * 声明针对的申请资料版本，十进制字符串
     * <li>2026-10-27 之前的存量声明没有记录，按 "0" 返回。</li>
     */
    private String draftRevision;

    /**
     * 声明是否仍针对申请当前资料
     * <li>资料版本等于申请当前 draftRevision，且声明配置了文档版本时 termsVersion 等于当前文档版本，为 true。</li>
     * <li>false 表示声明后资料或文档已变化，须重新确认；取值 TRUE 且 current 为 true 的声明才满足提交要求。</li>
     */
    private boolean current;
}
