package com.paytech.operations.console.bff.service.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 渠道控制台（渠道与账号、MID 与能力、渠道成本）的页面模型，是 channel-connector 管理合同的 BFF 投影
 * <li>字段与 channel-connector 合同一一对应，由 Client 适配器严格转换；Owner 新增字段即视为合同漂移（502）。</li>
 * <li>请求不含操作人字段：渠道配置命令的操作人经 X-Gateway-Actor-Subject 传递；成本命令的 makerId / checkerId / operatorId
 * 由 BFF 从会话填入 Owner 请求体。</li>
 * <li>BFF 只做与 Owner 一致的结构性约束；业务规则（状态迁移、归属、Platform 接入方、Maker-Checker）由 channel-connector 执行。</li>
 *
 * @author Claude
 */
public final class ChannelModels {
    /** 正十进制主键。 */
    static final String ID = "[1-9][0-9]{0,18}";

    /** 渠道、账号与 MID 的目标状态。 */
    static final String TARGET_STATUS = "ACTIVE|SUSPENDED|CLOSED";

    /** OE 编号：两位大写字母加三位数字。 */
    static final String ENTITY_NO = "[A-Z]{2}[0-9]{3}";

    /** 大写代码：大写字母开头的大写字母、数字或下划线，最长 64 位。 */
    static final String CODE = "[A-Z][A-Z0-9_]{0,63}";

    /** 主货币单位金额：最多 16 位整数、8 位小数。 */
    static final String AMOUNT = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?";

    /** 账号参数与凭据的名称。 */
    static final String ENTRY_KEY = "[A-Za-z][A-Za-z0-9_.-]{0,63}";

    /** 只承载类型，禁止创建实例。 */
    private ChannelModels() {
    }

    // ---------- 渠道 ----------

    /** 创建 DRAFT 渠道。 */
    @Getter
    @Setter
    public static class CreateChannelRequestBody {
        /**
         * 渠道编码
         * <li>创建后不可修改；激活时须与 Platform 接入方编码一致。</li>
         */
        @NotBlank(message = "渠道编码必填")
        @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "渠道编码必须是大写字母开头的大写字母、数字或下划线，2–64 位")
        private String channelCode;

        /** 渠道名称 */
        @NotBlank(message = "渠道名称必填")
        @Size(max = 255, message = "渠道名称最长 255 字符")
        private String channelName;
    }

    /** 修改渠道名称。 */
    @Getter
    @Setter
    public static class UpdateChannelRequestBody {
        /** 渠道名称 */
        @NotBlank(message = "渠道名称必填")
        @Size(max = 255, message = "渠道名称最长 255 字符")
        private String channelName;
    }

    /** 渠道、技术账号与 MID 共用的状态变更。 */
    @Getter
    @Setter
    public static class ChangeChannelStatusRequestBody {
        /** 目标状态：ACTIVE、SUSPENDED、CLOSED */
        @NotBlank(message = "目标状态必填")
        @Pattern(regexp = TARGET_STATUS, message = "目标状态只能为 ACTIVE、SUSPENDED 或 CLOSED")
        private String targetStatus;

        /**
         * 原因
         * <li>可空；写入变更记录，不得填写凭据。</li>
         */
        @Size(max = 512, message = "原因最长 512 字符")
        private String reason;
    }

    /** 渠道详情。 */
    @Getter
    @Setter
    public static class ChannelResponseBody {
        /** 渠道 ID */
        private String channelId;

        /** 渠道编码 */
        private String channelCode;

        /** 渠道名称 */
        private String channelName;

        /** 状态：DRAFT、ACTIVE、SUSPENDED、CLOSED */
        private String status;

        /** 创建时间（UTC） */
        private Instant createTime;

        /** 更新时间（UTC） */
        private Instant updateTime;
    }

    /** 渠道列表项。 */
    @Getter
    @Setter
    public static class ChannelSummaryResponseBody {
        /** 渠道 ID */
        private String channelId;

        /** 渠道编码 */
        private String channelCode;

        /** 渠道名称 */
        private String channelName;

        /** 状态 */
        private String status;

        /** 技术账号数 */
        private long accountCount;

        /** MID 数 */
        private long midCount;

        /** 更新时间（UTC） */
        private Instant updateTime;
    }

    // ---------- 技术账号 ----------

    /** 创建 DRAFT 技术账号。 */
    @Getter
    @Setter
    public static class CreateChannelAccountRequestBody {
        /** 所属渠道 ID */
        @NotBlank(message = "渠道 ID 必填")
        @Pattern(regexp = ID, message = "渠道 ID 必须是正十进制整数字符串")
        private String channelId;

        /** OE 编号，创建后不可修改 */
        @NotBlank(message = "OE 编号必填")
        @Pattern(regexp = ENTITY_NO, message = "OE 编号必须是两位大写字母加三位数字")
        private String entityNo;

        /** 账号名称 */
        @NotBlank(message = "账号名称必填")
        @Size(max = 128, message = "账号名称最长 128 字符")
        private String accountName;

        /** 备注，可空 */
        @Size(max = 512, message = "备注最长 512 字符")
        private String remark;
    }

    /** 修改技术账号名称与备注。 */
    @Getter
    @Setter
    public static class UpdateChannelAccountRequestBody {
        /** 账号名称 */
        @NotBlank(message = "账号名称必填")
        @Size(max = 128, message = "账号名称最长 128 字符")
        private String accountName;

        /** 备注，可空 */
        @Size(max = 512, message = "备注最长 512 字符")
        private String remark;
    }

    /**
     * 整组写入技术账号参数与凭据
     * <li>值为明文，仅经 BFF 转发给 channel-connector，由其在 Vault Protect；BFF 对该路径只记元数据日志。</li>
     */
    @Getter
    @Setter
    public static class ConfigureChannelAccountRequestBody {
        /** 非敏感参数，最多 64 项 */
        @NotNull(message = "参数集合必填")
        @Size(max = 64, message = "参数最多 64 项")
        private Map<@Pattern(regexp = ENTRY_KEY, message = "参数名称不符合约束") String,
            @NotBlank(message = "参数值不能为空") @Size(max = 8192, message = "参数值最长 8192 字符") String> parameters;

        /** 凭据，最多 64 项 */
        @NotNull(message = "凭据集合必填")
        @Size(max = 64, message = "凭据最多 64 项")
        private Map<@Pattern(regexp = ENTRY_KEY, message = "凭据名称不符合约束") String,
            @NotBlank(message = "凭据值不能为空") @Size(max = 8192, message = "凭据值最长 8192 字符") String> credentials;
    }

    /** 技术账号详情；不含 Token 全文、参数或凭据。 */
    @Getter
    @Setter
    public static class ChannelAccountResponseBody {
        /** 技术账号 ID */
        private String channelAccountId;

        /** 所属渠道 ID */
        private String channelId;

        /** OE 编号 */
        private String entityNo;

        /** 账号名称 */
        private String accountName;

        /** 状态 */
        private String status;

        /** 备注 */
        private String remark;

        /** 是否已写入配置 */
        private boolean configured;

        /** Vault Token 末 4 位；未配置为空 */
        private String vaultTokenSuffix;

        /** 当前绑定本账号的 MID 数 */
        private long boundMidCount;

        /** 创建时间（UTC） */
        private Instant createTime;

        /** 更新时间（UTC） */
        private Instant updateTime;
    }

    /** 技术账号列表项。 */
    @Getter
    @Setter
    public static class ChannelAccountSummaryResponseBody {
        /** 技术账号 ID */
        private String channelAccountId;

        /** 所属渠道 ID */
        private String channelId;

        /** OE 编号 */
        private String entityNo;

        /** 账号名称 */
        private String accountName;

        /** 状态 */
        private String status;

        /** 是否已写入配置 */
        private boolean configured;

        /** Vault Token 末 4 位；未配置为空 */
        private String vaultTokenSuffix;

        /** 更新时间（UTC） */
        private Instant updateTime;
    }

    // ---------- MID ----------

    /** 创建 DRAFT MID。 */
    @Getter
    @Setter
    public static class CreateChannelMidRequestBody {
        /** 所属渠道 ID */
        @NotBlank(message = "渠道 ID 必填")
        @Pattern(regexp = ID, message = "渠道 ID 必须是正十进制整数字符串")
        private String channelId;

        /** 当前技术账号 ID，须属于该渠道且 OE 一致 */
        @NotBlank(message = "技术账号 ID 必填")
        @Pattern(regexp = ID, message = "技术账号 ID 必须是正十进制整数字符串")
        private String channelAccountId;

        /** 渠道侧 MID，同渠道内唯一 */
        @NotBlank(message = "外部 MID 必填")
        @Size(max = 128, message = "外部 MID 最长 128 字符")
        private String externalMidCode;

        /** OE 编号 */
        @NotBlank(message = "OE 编号必填")
        @Pattern(regexp = ENTITY_NO, message = "OE 编号必须是两位大写字母加三位数字")
        private String entityNo;
    }

    /** MID 改绑技术账号。 */
    @Getter
    @Setter
    public static class RebindChannelMidAccountRequestBody {
        /** 目标技术账号 ID，须同渠道同 OE 且未关闭 */
        @NotBlank(message = "技术账号 ID 必填")
        @Pattern(regexp = ID, message = "技术账号 ID 必须是正十进制整数字符串")
        private String channelAccountId;
    }

    /** MID 详情，含能力条件组与明细。 */
    @Getter
    @Setter
    public static class ChannelMidResponseBody {
        /** MID ID */
        private String midId;

        /** 所属渠道 ID */
        private String channelId;

        /** 当前技术账号 ID */
        private String channelAccountId;

        /** 渠道侧 MID */
        private String externalMidCode;

        /** OE 编号 */
        private String entityNo;

        /** 状态 */
        private String status;

        /** 首次激活时间（UTC）；未激活为空 */
        private Instant activatedAt;

        /** 关闭时间（UTC）；未关闭为空 */
        private Instant closedAt;

        /** 创建时间（UTC） */
        private Instant createTime;

        /** 更新时间（UTC） */
        private Instant updateTime;

        /** 能力条件组 */
        private List<MidCapabilityResponseBody> capabilities;
    }

    /** MID 列表项。 */
    @Getter
    @Setter
    public static class ChannelMidSummaryResponseBody {
        /** MID ID */
        private String midId;

        /** 所属渠道 ID */
        private String channelId;

        /** 渠道编码 */
        private String channelCode;

        /** 当前技术账号 ID */
        private String channelAccountId;

        /** 当前技术账号名称 */
        private String accountName;

        /** 渠道侧 MID */
        private String externalMidCode;

        /** OE 编号 */
        private String entityNo;

        /** 状态 */
        private String status;

        /** 能力组总数 */
        private Integer capabilityGroupCount;

        /** 启用的能力组数 */
        private Integer enabledCapabilityGroupCount;

        /** 首次激活时间（UTC） */
        private Instant activatedAt;

        /** 更新时间（UTC） */
        private Instant updateTime;
    }

    // ---------- 能力 ----------

    /** 一条能力明细输入：按目录类型恰好填写一个值。 */
    @Getter
    @Setter
    public static class MidCapabilityDetailEntry {
        /** 能力代码，须在能力目录中注册 */
        @NotBlank(message = "能力代码必填")
        @Size(max = 128, message = "能力代码最长 128 字符")
        private String capabilityCode;

        /** BOOLEAN 类型的值 */
        private Boolean booleanValue;

        /** DECIMAL 类型的值，最多 8 位小数的十进制字符串 */
        @Pattern(regexp = "-?(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "数值必须是最多 8 位小数的十进制字符串")
        private String decimalValue;

        /** CODE 类型的值，须在目录允许值内 */
        @Size(min = 1, max = 128, message = "代码值长度为 1–128 字符")
        private String codeValue;
    }

    /** 创建 ENABLED 能力条件组。 */
    @Getter
    @Setter
    public static class CreateMidCapabilityRequestBody {
        /** MID ID */
        @NotBlank(message = "MID ID 必填")
        @Pattern(regexp = ID, message = "MID ID 必须是正十进制整数字符串")
        private String midId;

        /** 支付方式，精确目录代码 */
        @NotBlank(message = "支付方式必填")
        @Pattern(regexp = "[A-Z0-9][A-Z0-9_.-]{0,63}", message = "支付方式必须是精确目录代码")
        private String paymentMethodCode;

        /** 支付体系，目录代码或 * */
        @NotBlank(message = "支付体系必填")
        @Pattern(regexp = "\\*|[A-Z0-9][A-Z0-9_.-]{0,63}", message = "支付体系必须是目录代码或 *")
        private String schemeCode;

        /** 国家，两位大写代码或 * */
        @NotBlank(message = "国家必填")
        @Pattern(regexp = "\\*|[A-Z]{2}", message = "国家必须是两位大写代码或 *")
        private String countryCode;

        /** 币种，三位大写代码或 * */
        @NotBlank(message = "币种必填")
        @Pattern(regexp = "\\*|[A-Z]{3}", message = "币种必须是三位大写代码或 *")
        private String currency;

        /** MCC，四位数字 */
        @NotBlank(message = "MCC 必填")
        @Pattern(regexp = "[0-9]{4}", message = "MCC 必须是四位数字")
        private String mccCode;

        /** 能力明细，1–64 项 */
        @NotEmpty(message = "能力明细至少一项")
        @Size(max = 64, message = "能力明细最多 64 项")
        private List<@NotNull(message = "能力明细不能为空") @Valid MidCapabilityDetailEntry> details;
    }

    /** 整组替换能力明细。 */
    @Getter
    @Setter
    public static class ReplaceMidCapabilityDetailsRequestBody {
        /** 替换后的全部明细，1–64 项 */
        @NotEmpty(message = "能力明细至少一项")
        @Size(max = 64, message = "能力明细最多 64 项")
        private List<@NotNull(message = "能力明细不能为空") @Valid MidCapabilityDetailEntry> details;
    }

    /** 启用或停用能力条件组。 */
    @Getter
    @Setter
    public static class ChangeMidCapabilityStatusRequestBody {
        /** 目标状态：ENABLED、DISABLED */
        @NotBlank(message = "目标状态必填")
        @Pattern(regexp = "ENABLED|DISABLED", message = "目标状态只能为 ENABLED 或 DISABLED")
        private String targetStatus;
    }

    /** 能力条件组。 */
    @Getter
    @Setter
    public static class MidCapabilityResponseBody {
        /** 能力组 ID */
        private String midCapabilityId;

        /** MID ID */
        private String midId;

        /** 支付方式 */
        private String paymentMethodCode;

        /** 支付体系，可为 * */
        private String schemeCode;

        /** 国家，可为 * */
        private String countryCode;

        /** 币种，可为 * */
        private String currency;

        /** MCC */
        private String mccCode;

        /** 状态：ENABLED、DISABLED */
        private String status;

        /** 创建时间（UTC） */
        private Instant createTime;

        /** 更新时间（UTC） */
        private Instant updateTime;

        /** 明细 */
        private List<MidCapabilityDetailResponseBody> details;
    }

    /** 能力明细。 */
    @Getter
    @Setter
    public static class MidCapabilityDetailResponseBody {
        /** 能力代码 */
        private String capabilityCode;

        /** 值类型：BOOLEAN、DECIMAL、CODE */
        private String valueType;

        /** BOOLEAN 值 */
        private Boolean booleanValue;

        /** DECIMAL 值 */
        private String decimalValue;

        /** CODE 值 */
        private String codeValue;
    }

    /** 已注册的能力目录。 */
    @Getter
    @Setter
    public static class CapabilityDefinitionsResponseBody {
        /** 目录项 */
        private List<CapabilityDefinitionResponseBody> definitions;
    }

    /** 一个能力目录项。 */
    @Getter
    @Setter
    public static class CapabilityDefinitionResponseBody {
        /** 能力代码 */
        private String capabilityCode;

        /** 值类型：BOOLEAN、DECIMAL、CODE */
        private String valueType;

        /** CODE 类型的允许值；其他类型为空列表 */
        private List<String> allowedCodes;
    }

    // ---------- 变更记录 ----------

    /** 一条渠道配置或成本版本的人工变更记录。 */
    @Getter
    @Setter
    public static class ChannelConfigChangeResponseBody {
        /** 变更记录 ID */
        private String changeId;

        /** 对象类型：CHANNEL、CHANNEL_ACCOUNT、CHANNEL_MID、MID_CAPABILITY、CHANNEL_COST_VERSION */
        private String targetType;

        /** 对象 ID */
        private String targetId;

        /** 变更类型：CREATE、UPDATE、STATUS、CONFIGURATION、REBIND、CANCEL */
        private String changeType;

        /** 变更前状态；非状态变更为空 */
        private String fromStatus;

        /** 变更后状态；非状态变更为空 */
        private String toStatus;

        /** 操作者 Account ID */
        private String operatorId;

        /** 原因；未填写为空 */
        private String reason;

        /** 记录时间（UTC） */
        private Instant createTime;
    }

    // ---------- 渠道成本 ----------

    /** 为渠道创建成本方案（DRAFT，无当前版本）。 */
    @Getter
    @Setter
    public static class CreateChannelCostPlanRequestBody {
        /** 渠道 ID，每个渠道一个方案 */
        @NotBlank(message = "渠道 ID 必填")
        @Pattern(regexp = ID, message = "渠道 ID 必须是正十进制整数字符串")
        private String channelId;

        /** 方案代码 */
        @NotBlank(message = "方案代码必填")
        @Pattern(regexp = CODE, message = "方案代码必须是大写字母开头的大写字母、数字或下划线，最长 64 位")
        private String planCode;
    }

    /**
     * 创建 DRAFT 成本版本
     * <li>编制人由 BFF 以会话 Account ID 填入 Owner 的 makerId。</li>
     */
    @Getter
    @Setter
    public static class CreateChannelCostVersionRequestBody {
        /** 生效时间（带时区） */
        @NotNull(message = "生效时间必填")
        private Instant effectiveFrom;

        /** 完整适用范围，最多 200 个；是否允许为空由 channel-connector 判定 */
        @NotNull(message = "适用范围必填")
        @Size(max = 200, message = "适用范围最多 200 个")
        private List<@NotNull(message = "适用范围不能为空") @Valid ChannelCostScopeEntry> scopes;
    }

    /** 一个成本适用范围输入。 */
    @Getter
    @Setter
    public static class ChannelCostScopeEntry {
        /** 范围序号，版本内唯一 */
        @NotNull(message = "范围序号必填")
        private Integer scopeNo;

        /** MID ID，须属于方案渠道 */
        @NotBlank(message = "MID 必填")
        @Pattern(regexp = ID, message = "必须是正十进制整数字符串")
        private String midId;

        /** OE 编号，须与 MID 一致 */
        @NotBlank(message = "OE 编号必填")
        @Pattern(regexp = ENTITY_NO, message = "必须是两位大写字母加三位数字")
        private String entityNo;

        /** 渠道合同引用 */
        @NotBlank(message = "合同引用必填")
        @Size(max = 128, message = "合同引用最长 128 字符")
        private String channelAgreementRef;

        /** 国家，两位大写代码 */
        @NotBlank(message = "国家必填")
        @Pattern(regexp = "[A-Z]{2}", message = "必须是大写 ISO 3166-1 alpha-2 两位代码")
        private String countryCode;

        /** 币种，三位大写代码 */
        @NotBlank(message = "币种必填")
        @Pattern(regexp = "[A-Z]{3}", message = "必须是大写 ISO 4217 三位代码")
        private String currency;

        /** 支付方式 */
        @NotBlank(message = "支付方式必填")
        @Pattern(regexp = CODE, message = "必须是大写字母开头的大写字母、数字或下划线，最长 64 位")
        private String paymentMethodCode;

        /** 动作类型 */
        @NotBlank(message = "动作类型必填")
        @Pattern(regexp = CODE, message = "必须是大写字母开头的大写字母、数字或下划线，最长 64 位")
        private String actionType;

        /** 成本组成，最多 20 个；是否允许为空由 channel-connector 判定 */
        @NotNull(message = "成本组成必填")
        @Size(max = 20, message = "成本组成最多 20 个")
        private List<@NotNull(message = "成本组成不能为空") @Valid ChannelCostComponentEntry> components;
    }

    /** 一个成本组成输入。 */
    @Getter
    @Setter
    public static class ChannelCostComponentEntry {
        /** 组成代码，范围内唯一 */
        @NotBlank(message = "组成代码必填")
        @Pattern(regexp = CODE, message = "必须是大写字母开头的大写字母、数字或下划线，最长 64 位")
        private String componentCode;

        /** 组成类型目录代码 */
        @NotBlank(message = "组成类型必填")
        @Size(max = 64, message = "组成类型最长 64 字符")
        private String componentType;

        /** 计算基础目录代码 */
        @NotBlank(message = "计算基础必填")
        @Size(max = 64, message = "计算基础最长 64 字符")
        private String calculationBase;

        /** 费率，最多 10 位小数 */
        @Pattern(regexp = "(0|[1-9][0-9]{0,7})(\\.[0-9]{1,10})?", message = "必须是最多 10 位小数的普通十进制字符串")
        private String rateValue;

        /** 固定金额（主货币单位） */
        @Pattern(regexp = AMOUNT, message = "必须是主货币单位的普通十进制字符串")
        private String fixedAmount;

        /** 金额币种 */
        @Pattern(regexp = "[A-Z]{3}", message = "必须是大写 ISO 4217 三位代码")
        private String currency;

        /** 下限（主货币单位） */
        @Pattern(regexp = AMOUNT, message = "必须是主货币单位的普通十进制字符串")
        private String minAmount;

        /** 上限（主货币单位） */
        @Pattern(regexp = AMOUNT, message = "必须是主货币单位的普通十进制字符串")
        private String maxAmount;

        /** 舍入方式目录代码 */
        @NotBlank(message = "舍入方式必填")
        @Size(max = 64, message = "舍入方式最长 64 字符")
        private String roundingMode;
    }

    /**
     * 批准或驳回版本
     * <li>复核人由 BFF 以会话 Account ID 填入 Owner 的 checkerId；Owner 要求复核人不同于编制人。</li>
     */
    @Getter
    @Setter
    public static class ReviewChannelCostVersionRequestBody {
        /** 复核人确认的内容摘要，须等于版本当前摘要 */
        @NotBlank(message = "内容摘要必填")
        @Pattern(regexp = "[0-9a-f]{64}", message = "必须是 64 位小写十六进制")
        private String approvedContentHash;
    }

    /**
     * 作废未到生效时间的已批准版本
     * <li>作废人由 BFF 以会话 Account ID 填入 Owner 的 operatorId。</li>
     */
    @Getter
    @Setter
    public static class CancelChannelCostVersionRequestBody {
        /** 作废原因，写入变更记录 */
        @NotBlank(message = "作废原因必填")
        @Size(max = 512, message = "作废原因最长 512 字符")
        private String reason;
    }

    /** 成本方案列表项。 */
    @Getter
    @Setter
    public static class ChannelCostPlanSummaryResponseBody {
        /** 方案 ID */
        private String planId;

        /** 方案代码 */
        private String planCode;

        /** 渠道 ID */
        private String channelId;

        /** 渠道编码 */
        private String channelCode;

        /** 渠道名称 */
        private String channelName;

        /** 方案状态：DRAFT、ACTIVE、INACTIVE */
        private String status;

        /** 当前版本号；无当前版本为空 */
        private String currentCostVer;

        /** 当前版本生效时间（UTC） */
        private Instant currentEffectiveFrom;

        /** 待复核版本数 */
        private Integer pendingApprovalCount;

        /** 更新时间（UTC） */
        private Instant updateTime;
    }

    /** 成本方案详情，含全部版本摘要（版本号降序）。 */
    @Getter
    @Setter
    public static class ChannelCostPlanResponseBody {
        /** 方案代码 */
        private String planCode;

        /** 渠道 ID */
        private String channelId;

        /** 方案状态 */
        private String status;

        /** 当前版本号；无当前版本为空 */
        private String currentCostVer;

        /** 版本摘要 */
        private List<ChannelCostVersionSummaryResponseBody> versions;
    }

    /** 成本版本摘要。 */
    @Getter
    @Setter
    public static class ChannelCostVersionSummaryResponseBody {
        /** 版本主键；变更记录以它作为 CHANNEL_COST_VERSION 的 targetId */
        private String costVerId;

        /** 版本号 */
        private String costVer;

        /** 状态：DRAFT、PENDING_APPROVAL、APPROVED、ACTIVE、INACTIVE、REJECTED */
        private String status;

        /** 生效时间（UTC） */
        private Instant effectiveFrom;

        /** 失效时间（UTC）；未失效为空 */
        private Instant effectiveTo;

        /** 内容摘要 */
        private String contentHash;

        /** 编制人 */
        private String makerId;

        /** 复核人；未复核为空 */
        private String checkerId;

        /** 适用范围数 */
        private Integer scopeCount;

        /** 成本组成总数（全部范围合计） */
        private Integer componentCount;
    }

    /** 成本版本详情。 */
    @Getter
    @Setter
    public static class ChannelCostVersionResponseBody {
        /** 版本主键；变更记录以它作为 CHANNEL_COST_VERSION 的 targetId */
        private String costVerId;

        /** 方案代码 */
        private String planCode;

        /** 版本号 */
        private String costVer;

        /** 状态 */
        private String status;

        /** 生效时间（UTC） */
        private Instant effectiveFrom;

        /** 失效时间（UTC） */
        private Instant effectiveTo;

        /** 内容摘要 */
        private String contentHash;

        /** 编制人 */
        private String makerId;

        /** 复核人 */
        private String checkerId;

        /** 适用范围，按 scopeNo 排序 */
        private List<ChannelCostScopeResponseBody> scopes;
    }

    /** 成本适用范围。 */
    @Getter
    @Setter
    public static class ChannelCostScopeResponseBody {
        /** 范围序号 */
        private Integer scopeNo;

        /** MID ID */
        private String midId;

        /** OE 编号 */
        private String entityNo;

        /** 渠道合同引用 */
        private String channelAgreementRef;

        /** 国家 */
        private String countryCode;

        /** 币种 */
        private String currency;

        /** 支付方式 */
        private String paymentMethodCode;

        /** 动作类型 */
        private String actionType;

        /** 成本组成，按 componentCode 排序 */
        private List<ChannelCostComponentResponseBody> components;
    }

    /** 成本组成。 */
    @Getter
    @Setter
    public static class ChannelCostComponentResponseBody {
        /** 组成 ID */
        private String componentId;

        /** 组成代码 */
        private String componentCode;

        /** 组成类型 */
        private String componentType;

        /** 计算基础 */
        private String calculationBase;

        /** 费率 */
        private String rateValue;

        /** 固定金额 */
        private String fixedAmount;

        /** 金额币种 */
        private String currency;

        /** 下限 */
        private String minAmount;

        /** 上限 */
        private String maxAmount;

        /** 舍入方式 */
        private String roundingMode;
    }

    // ---------- 版本对比 ----------

    /**
     * 同一方案两个成本版本的对比
     * <li>范围按 MID、OE、国家、币种、支付方式、动作类型六维对齐；成对范围内的组成按 componentCode 对齐。</li>
     * <li>六维相同的范围在一个版本内出现多次时，按 scopeNo 顺序逐个配对。</li>
     */
    @Getter
    @Setter
    public static class ChannelCostVersionComparisonResponseBody {
        /** 基准版本摘要 */
        private ChannelCostVersionSummaryResponseBody base;

        /** 对比版本摘要 */
        private ChannelCostVersionSummaryResponseBody target;

        /** 只在对比版本出现的范围 */
        private List<ChannelCostScopeResponseBody> added;

        /** 只在基准版本出现的范围 */
        private List<ChannelCostScopeResponseBody> removed;

        /** 两版都有但合同引用或组成不同的范围 */
        private List<ChannelCostScopeChange> changed;

        /** 两版完全相同的范围（取对比版本） */
        private List<ChannelCostScopeResponseBody> unchanged;
    }

    /** 一个修改过的范围。 */
    @Getter
    @Setter
    public static class ChannelCostScopeChange {
        /** 基准版本中的范围 */
        private ChannelCostScopeResponseBody base;

        /** 对比版本中的范围 */
        private ChannelCostScopeResponseBody target;

        /** 范围自身的字段差异（六维之外只有 channelAgreementRef） */
        private List<ChannelFieldDifference> differences;

        /** 对比版本新增的组成 */
        private List<ChannelCostComponentResponseBody> addedComponents;

        /** 对比版本删除的组成 */
        private List<ChannelCostComponentResponseBody> removedComponents;

        /** 两版都有但字段不同的组成 */
        private List<ChannelCostComponentChange> changedComponents;
    }

    /** 一个修改过的组成。 */
    @Getter
    @Setter
    public static class ChannelCostComponentChange {
        /** 组成代码 */
        private String componentCode;

        /** 基准版本中的组成 */
        private ChannelCostComponentResponseBody base;

        /** 对比版本中的组成 */
        private ChannelCostComponentResponseBody target;

        /** 字段差异 */
        private List<ChannelFieldDifference> differences;
    }

    /** 一个字段的差异。 */
    @Getter
    @Setter
    public static class ChannelFieldDifference {
        /** 字段名 */
        private String field;

        /** 基准值；空为 null */
        private String baseValue;

        /** 对比值；空为 null */
        private String targetValue;
    }
}
