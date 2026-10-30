package com.paytech.operations.console.bff.service.model.tax.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 税务查询页（财务 → 税务查询）的页面模型：标题状态、五步进度、一张提示卡、左栏金额与报价记录、右栏税票 / 登记 / 底稿 / 依据
 * <li>由 BFF 从 tax 订单查询结果组装；只读，金额与 ID 为十进制字符串，税率为小数字符串（例如 0.15），时间为 UTC。</li>
 * <li>状态与分类字段为稳定 Code，页面经 tax.* 语言包键显示；不返回界面文案。</li>
 */
@Getter
@Setter
public class TaxOrderPageResponseBody {

    /** 订单号 */
    private String orderNo;

    /**
     * 页面状态（标题处唯一徽标）
     * <li>NOT_MANAGED 不由平台计税；BLOCKED 当前报价被阻断；ESTIMATED 只有建单暂估；QUOTED 最终报价待支付；REGISTERED 已登记、税票未开具；
     * ISSUED 已登记且已开票。</li>
     */
    private String pageStatus;

    /** 当前报价冻结的业务模式（commercial BusinessMode Code，如 MOR / PSP） */
    private String businessMode;

    /** 买方 Client ID */
    private String buyerClientId;

    /**
     * 买方 Client 法定名称
     * <li>BFF 从 client-master 聚合；读取失败、超时或无权限时为 null，页面显示 Client ID。</li>
     */
    private String buyerClientName;

    /** 收单 Operating Entity 编号 */
    private String entityNo;

    /**
     * 收单 Operating Entity 法定名称
     * <li>BFF 从 client-master 聚合；读取失败时为 null。</li>
     */
    private String entityName;

    /** 纳税主体 Party ID；不由平台计税且责任方未确定（CLIENT 来源 OE）时为 null */
    private String taxSubjectPartyId;

    /**
     * 纳税主体名称
     * <li>纳税主体就是收单 Operating Entity（Party ID 相同）时为其法定名称；否则或读取失败为 null，页面显示 Party ID。</li>
     */
    private String taxSubjectName;

    /** 订单币种 */
    private String currency;

    /**
     * 谁操作纳税主体在该地区的税务（ManagedBy Code：SUBJECT_HOLDER、PLATFORM_OPERATIONS）
     * <li>取当前报价（已登记时取登记依据报价）引用的税务注册；没有注册或 tax 未给出时为 null。</li>
     */
    private String managedBy;

    /** 税务进度（MoR 五步；不由平台计税三步） */
    private List<Step> steps;

    /** 当前唯一需要注意的事项 */
    private Callout callout;

    /** 当前报价的金额 */
    private Amounts amounts;

    /**
     * 当前报价为何按该税率计税的说明输入
     * <li>仅当前报价 READY 且税额由 tax 计算时返回，否则为 null。</li>
     */
    private Explanation explanation;

    /** 当前报价的逐行明细（按行号升序） */
    private List<Line> lines;

    /** 报价记录（按 revision 倒序，第一条为当前报价） */
    private List<Quote> quotes;

    /** 当前报价的阻断原因；未阻断为空列表 */
    private List<BlockReason> blockReasons;

    /** 当前报价的匹配结果（被阻断时用于右栏"本次匹配"）；未阻断为 null */
    private Matching matching;

    /** 税票；尚无单据为 null */
    private Receipt receipt;

    /** 正式登记；尚未登记为 null */
    private Registration registration;

    /** 申报底稿；尚未登记为 null */
    private Reporting reporting;

    /** 计算依据（规则包、税务注册、取整）；当前报价没有税额组件时为 null */
    private Basis basis;

    /**
     * 不由平台计税的说明输入
     * <li>仅当前报价 NOT_MANAGED 时返回。</li>
     */
    private NotManaged notManaged;

    /** 进度中的一步。 */
    @Getter
    @Setter
    public static class Step {

        /** 步骤 Code：QUOTE、RECALCULATION、PAYMENT、REGISTRATION、REPORTING */
        private String key;

        /** 步骤状态：DONE、BLOCKED、ATTENTION（已发生但有待补事项）、PENDING、NOT_APPLICABLE、UNTRACKED（税务服务不记录） */
        private String state;

        /** 该步的金额（报价税额或收款总额）；不适用为 null */
        private String amount;

        /** 该步的证据国家（报价步骤）；不适用为 null */
        private String countryCode;

        /** 该步的单号（登记与开票步骤）；不适用为 null */
        private String documentNumber;

        /** 该步的申报币种（申报底稿步骤）；不适用为 null */
        private String reportingCurrency;

        /** 该步发生时间；未发生为 null */
        private Instant occurredAt;
    }

    /** 提示卡。 */
    @Getter
    @Setter
    public static class Callout {

        /**
         * 提示类型
         * <li>CLIENT_RESPONSIBLE、PAYMENT_BLOCKED、REPORTING_FX_MISSING、DOCUMENT_PENDING、REGISTERED、ESTIMATE_ONLY、AWAITING_PAYMENT。</li>
         */
        private String kind;

        /** 色调：INFO、SUCCESS、WARNING、DANGER、NEUTRAL */
        private String tone;

        /** 文案参数：税额；不适用为 null */
        private String taxAmount;

        /** 文案参数：申报币种；不适用为 null */
        private String reportingCurrency;

        /** 文案参数：报价到期时间；不适用为 null */
        private Instant expiresAt;
    }

    /** 当前报价金额。 */
    @Getter
    @Setter
    public static class Amounts {

        /** 当前计算 ID */
        private String calculationId;

        /** 报价序号 */
        private Integer revision;

        /** 阶段：PROVISIONAL、FINAL */
        private String stage;

        /** 计算状态：READY、BLOCKED、NOT_MANAGED */
        private String status;

        /** 税额来源：COMPUTED、DECLARED */
        private String taxAmountSource;

        /** 商品净额 */
        private String netAmount;

        /** 税额；BLOCKED 为 null */
        private String taxAmount;

        /** 消费者应付；BLOCKED 为 null */
        private String grossAmount;

        /** 最终报价可支付截止时间；其他为 null */
        private Instant expiresAt;
    }

    /** 计税说明输入。 */
    @Getter
    @Setter
    public static class Explanation {

        /** 是否存在多种辖区 / 税种 / 税率；为 true 时页面使用汇总说明，下列单一字段为 null */
        private boolean mixed;

        /** 辖区 */
        private String jurisdictionCode;

        /** 税种 */
        private String taxType;

        /** 处理类型（TaxTreatment） */
        private String treatment;

        /** 税率（小数）；无适用税率为 null */
        private String rate;

        /** 作为供应地依据的证据类型（DECLARED_COUNTRY / BILLING_COUNTRY）；没有为 null */
        private String evidenceType;

        /** 作为供应地依据的国家 */
        private String countryCode;

        /** 命中税务注册的方案 Code */
        private String schemeCode;

        /** 命中地区包 Code */
        private String packageCode;

        /** 命中地区包修订号 */
        private Integer policyRevision;
    }

    /** 逐行明细。 */
    @Getter
    @Setter
    public static class Line {

        /** 行号，从 1 开始 */
        private Integer lineNo;

        /** 行引用（Client 商品 ID） */
        private String lineRef;

        /** 税务品类 Code */
        private String categoryCode;

        /** 非敏感商品说明（Client 订单行名称）；未提供为 null，页面改显示行引用 */
        private String description;

        /** 数量 */
        private String quantity;

        /** 税基（行净额） */
        private String netAmount;

        /** 行税额；BLOCKED 为 null */
        private String taxAmount;

        /** 主组件税率（小数）；无组件或无适用税率为 null */
        private String rate;

        /** 主组件处理类型；无组件为 null */
        private String treatment;

        /** 主组件规则码；无组件为 null */
        private String ruleCode;

        /** 行组件数；大于 1 时页面标注"多个组件" */
        private int componentCount;
    }

    /** 一次报价。 */
    @Getter
    @Setter
    public static class Quote {

        /** 计算 ID */
        private String calculationId;

        /** 报价序号 */
        private Integer revision;

        /** 阶段 */
        private String stage;

        /** 计算状态 */
        private String status;

        /** 是否当前报价 */
        private boolean current;

        /** 税额；BLOCKED 为 null */
        private String taxAmount;

        /** 证据类型（作为供应地依据的一条；没有时取本阶段证据） */
        private String evidenceType;

        /** 证据国家 */
        private String countryCode;

        /** 上一次报价的证据国家；与本次相同或没有上一次时为 null */
        private String previousCountryCode;

        /** 相对上一次报价的税额变化（带符号）；任一税额缺失或没有上一次时为 null */
        private String taxDelta;

        /** 计算时间 */
        private Instant calculatedAt;
    }

    /** 阻断原因。 */
    @Getter
    @Setter
    public static class BlockReason {

        /** 原因码（TaxBlockReason） */
        private String code;

        /** tax 给出的字段路径；可空 */
        private String path;

        /** 路径指向的行号；不指向行为 null */
        private Integer lineNo;

        /** 路径指向行的税务品类；不指向行为 null */
        private String categoryCode;

        /** 当前报价作为依据的证据国家 */
        private String countryCode;
    }

    /** 被阻断报价的匹配结果。 */
    @Getter
    @Setter
    public static class Matching {

        /** 证据类型 */
        private String evidenceType;

        /** 证据国家 */
        private String countryCode;

        /** 市场准入是否命中（原因含 MARKET_NOT_ENABLED 时为 false） */
        private boolean marketMatched;

        /** 规则包是否命中（原因含 MARKET_NOT_ENABLED、NO_RULE、AMBIGUOUS_RULE、POLICY_INVALID 时为 false） */
        private boolean policyMatched;
    }

    /** 税票。 */
    @Getter
    @Setter
    public static class Receipt {

        /** 单据 ID（下载请求使用） */
        private String documentId;

        /** 单据类型（TaxDocumentKind） */
        private String documentKind;

        /** 单号；预留未开具时可能为 null */
        private String documentNumber;

        /** 开具状态（TaxDocumentIssueStatus） */
        private String issueStatus;

        /** 开具时间 */
        private Instant issuedAt;

        /** 是否可下载（ISSUED 且有文件地址） */
        private boolean downloadable;
    }

    /** 正式登记。 */
    @Getter
    @Setter
    public static class Registration {

        /** 税务交易 ID */
        private String transactionId;

        /** 登记时间 */
        private Instant registeredAt;

        /** 法定税点日期 yyyy-MM-dd */
        private String taxPointDate;

        /** 税点所在法定时区（来自税务注册）；未知为 null */
        private String legalTimezone;

        /** 来源事实类型 */
        private String sourceFactType;

        /** 来源事实 ID */
        private String sourceFactId;

        /** 依据报价序号 */
        private Integer calculationRevision;
    }

    /** 申报底稿。 */
    @Getter
    @Setter
    public static class Reporting {

        /** 底稿状态（TaxReportingStatus） */
        private String status;

        /** 待补原因（例如 FX_RATE_MISSING）；READY 为 null */
        private String blockReason;

        /** 申报频率（来自税务注册） */
        private String filingFrequency;

        /** 期间起始 yyyy-MM-dd；PENDING 为 null */
        private String periodStart;

        /** 期间结束 yyyy-MM-dd；PENDING 为 null */
        private String periodEnd;

        /** 申报截止日；PENDING 为 null */
        private String filingDueDate;

        /** 销项税额（订单币种） */
        private String taxAmount;

        /** 申报币种 */
        private String reportingCurrency;

        /** 申报币种税额；PENDING 为 null */
        private String reportingTaxAmount;
    }

    /** 计算依据。 */
    @Getter
    @Setter
    public static class Basis {

        /** 地区包 Code */
        private String packageCode;

        /** 地区包修订号 */
        private Integer policyRevision;

        /** 注册辖区 */
        private String jurisdictionCode;

        /** 注册方案 Code */
        private String schemeCode;

        /** 是否命中市场准入 */
        private boolean marketEnabled;

        /** 取整方式 */
        private String roundingMethod;

        /** 取整位数 */
        private Integer roundingScale;
    }

    /** 不由平台计税的说明。 */
    @Getter
    @Setter
    public static class NotManaged {

        /** 原因（TaxNotManagedReason）：SUBJECT_NOT_REGISTERED、TAX_RESPONSIBLE_UNRESOLVED */
        private String reason;

        /** 计算 ID（页面注明只保存以备追溯） */
        private String calculationId;

        /** 业务模式 */
        private String businessMode;

        /** 税务责任方 Party ID（PSP 下为 Client 侧主体）；TAX_RESPONSIBLE_UNRESOLVED 时为 null */
        private String taxSubjectPartyId;

        /** 买方 Client ID */
        private String buyerClientId;
    }
}
