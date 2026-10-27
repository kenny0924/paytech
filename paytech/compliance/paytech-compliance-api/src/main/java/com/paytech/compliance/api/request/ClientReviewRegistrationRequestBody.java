package com.paytech.compliance.api.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * I01 v2：client-master 正式提交后登记审核案件的受审事实；按来源提交编号防重
 * <li>v2 起不再传证件原值与地址行：个人证件只传 Vault Token 与脱敏值；姓名、生日、国籍仅供筛查，compliance 不落库。</li>
 * <li>退回后重提用新的 sourceSubmissionId 并带 previousCaseId，compliance 新建案件并复用上一案件已接受的材料与有效期内通过的检查。</li>
 */
@Getter
@Setter
public class ClientReviewRegistrationRequestBody {

    /**
     * 来源提交编号
     * <li>client-master 正式提交时生成的 UUID；同一提交的重试复用，退回重提更换。</li>
     * <li>是业务防重身份，不是 HTTP Idempotency-Key。</li>
     */
    @NotBlank
    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    private String sourceSubmissionId;

    /** Client 申请 ID，正 BIGINT 十进制字符串。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String applicationId;

    /** 申请编号。 */
    @NotBlank
    @Size(max = 64)
    private String applicationNo;

    /** Client ID，正 BIGINT 十进制字符串。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String clientId;

    /**
     * 客户类型
     * <li>INDIVIDUAL 或 ORGANIZATION。</li>
     */
    @NotBlank
    @Pattern(regexp = "^(INDIVIDUAL|ORGANIZATION)$")
    private String clientType;

    /**
     * 申请冻结的辖区代码
     * <li>例如 SA、AE、AE-DIFC；用于解析程序（辖区 × 主体类型 × 角色 × 档次）。</li>
     */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9-]{1,15}$")
    private String jurisdictionCode;

    /** 组织类型代码（辖区目录），机构申请必填，个人申请可空。 */
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String organizationTypeCode;

    /**
     * 责任 PayFac 的 Local Partner ID
     * <li>由入驻码解析并冻结在申请上；平台自营为空。</li>
     */
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String responsiblePartnerId;

    /**
     * 上一轮被退回的主案件 ID
     * <li>首次提交为空；重提时必填，必须是同一申请最近一次被退回且未过期、未撤回的主案件。</li>
     */
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String previousCaseId;

    /** 发起申请的 Platform CUSTOMER Account ID，正 BIGINT 十进制字符串。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String requestedByAccountId;

    /**
     * 提交时间
     * <li>UTC 六位微秒 ISO-8601 字符串。</li>
     */
    @NotBlank
    @Size(max = 32)
    private String submittedAt;

    /** 经营资料。 */
    @NotNull
    @Valid
    private ClientReviewBusinessBody business;

    /**
     * 受审主体
     * <li>第一项为主主体，其余为提交时仍在范围内的关联人员；最多 101 项。</li>
     */
    @NotEmpty
    @Size(max = 101)
    @Valid
    private List<ClientReviewSubjectBody> subjects;

    /** 组织与关联人员的关系；个人申请为空列表。 */
    @NotNull
    @Size(max = 500)
    @Valid
    private List<ClientReviewRelationBody> relations;

    /** 材料引用；文件字节由 contentHash 固定。 */
    @NotNull
    @Size(max = 500)
    @Valid
    private List<ClientReviewDocumentBody> documents;

    /**
     * 扩展属性（辖区属性定义中非 Vault 级的值）
     * <li>可空；本期只做合同校验，不在 compliance 落库。</li>
     */
    @Size(max = 500)
    @Valid
    private List<ClientReviewAttributeBody> attributes;

    /**
     * Client 在提交前完成的声明
     * <li>可空；本期只做合同校验，不在 compliance 落库。</li>
     */
    @Size(max = 50)
    @Valid
    private List<ClientReviewDeclarationBody> declarations;
}
