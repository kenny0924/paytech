package com.paytech.operations.console.bff.service.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 账务基础资料页面模型：财务主体、财务日历、会计期间、科目表、科目维护与币种
 * <li>字段与 ledger 合同一一对应，由 Client 适配器严格转换；Owner 新增字段即视为合同漂移。</li>
 * <li>金额、ID 与枚举均为字符串；时间为 UTC Instant，日期为 ISO 日期。</li>
 *
 * @author Claude
 */
public final class FinanceSetupModels {
    /** 科目编号：4、6、8 或 10 位数字，首位非 0；与 ledger 合同一致。 */
    private static final String ACCOUNT_NO = "[1-9][0-9]{3}([0-9]{2}){0,3}";

    /** 上级科目编号：只有 4、6、8 位科目可以有下级。 */
    private static final String PARENT_ACCOUNT_NO = "[1-9][0-9]{3}([0-9]{2}){0,2}";

    /** 只承载类型，禁止创建实例。 */
    private FinanceSetupModels() {
    }

    /** 财务主体。 */
    @Getter
    @Setter
    public static class FinanceEntityResponseBody {
        /** 财务主体主键。 */
        private String financeEntityId;

        /** 财务主体代码。 */
        private String entityCode;

        /** 对应的 CCT 法律 Party ID。 */
        private String legalPartyId;

        /** 显示名称。 */
        private String displayName;

        /**
         * 状态
         * <li>ACTIVE 或 CLOSED。</li>
         */
        private String status;

        /** 创建时间，UTC。 */
        private Instant createTime;
    }

    /** 新建财务主体。 */
    @Getter
    @Setter
    public static class CreateFinanceEntityRequestBody {
        /**
         * 财务主体代码
         * <li>1..64 个字符，全局唯一。</li>
         */
        @NotBlank(message = "财务主体代码必填")
        @Size(max = 64, message = "财务主体代码不超过 64 个字符")
        private String entityCode;

        /**
         * CCT 法律 Party ID
         * <li>正十进制整数字符串，全局唯一。</li>
         */
        @NotBlank(message = "法律 Party ID 必填")
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "法律 Party ID 必须是正十进制整数")
        private String legalPartyId;

        /**
         * 显示名称
         * <li>1..256 个字符。</li>
         */
        @NotBlank(message = "显示名称必填")
        @Size(max = 256, message = "显示名称不超过 256 个字符")
        private String displayName;
    }

    /** 新建财务主体结果。 */
    @Getter
    @Setter
    public static class CreateFinanceEntityResponseBody {
        /** 新财务主体主键。 */
        private String financeEntityId;
    }

    /** 财务日历。 */
    @Getter
    @Setter
    public static class FiscalCalendarResponseBody {
        /** 财务日历主键。 */
        private String fiscalCalendarId;

        /**
         * 主体类型
         * <li>OPERATING_ENTITY 或 FINANCE_ENTITY。</li>
         */
        private String entityType;

        /**
         * 主体标识
         * <li>OPERATING_ENTITY 为 entityNo，FINANCE_ENTITY 为财务主体主键。</li>
         */
        private String entityId;

        /** 日历代码。 */
        private String calendarCode;

        /** IANA 时区。 */
        private String timezone;

        /** 财年起始月，1..12。 */
        private Integer yearStartMonth;

        /** 创建时间，UTC。 */
        private Instant createTime;
    }

    /** 新建财务日历；每个主体只能有一个日历。 */
    @Getter
    @Setter
    public static class CreateFiscalCalendarRequestBody {
        /**
         * 主体类型
         * <li>OPERATING_ENTITY 或 FINANCE_ENTITY。</li>
         */
        @NotBlank(message = "主体类型必填")
        @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY", message = "主体类型只能为 OPERATING_ENTITY 或 FINANCE_ENTITY")
        private String entityType;

        /**
         * 主体标识
         * <li>1..64 个字符。</li>
         */
        @NotBlank(message = "主体标识必填")
        @Size(max = 64, message = "主体标识不超过 64 个字符")
        private String entityId;

        /**
         * 日历代码
         * <li>1..64 个字符。</li>
         */
        @NotBlank(message = "日历代码必填")
        @Size(max = 64, message = "日历代码不超过 64 个字符")
        private String calendarCode;

        /**
         * IANA 时区
         * <li>1..64 个字符，有效性由 ledger 判断。</li>
         */
        @NotBlank(message = "时区必填")
        @Size(max = 64, message = "时区不超过 64 个字符")
        private String timezone;

        /**
         * 财年起始月
         * <li>1..12。</li>
         */
        @NotNull(message = "财年起始月必填")
        @Min(value = 1, message = "财年起始月为 1..12")
        @Max(value = 12, message = "财年起始月为 1..12")
        private Integer yearStartMonth;
    }

    /** 新建财务日历结果。 */
    @Getter
    @Setter
    public static class CreateFiscalCalendarResponseBody {
        /** 新财务日历主键。 */
        private String fiscalCalendarId;
    }

    /** 会计期间。 */
    @Getter
    @Setter
    public static class AccountingPeriodResponseBody {
        /** 会计期间主键。 */
        private String accountingPeriodId;

        /** 所属财务日历主键。 */
        private String fiscalCalendarId;

        /** 财年。 */
        private Integer fiscalYear;

        /** 期号。 */
        private Integer periodNo;

        /** 起始日期（含）。 */
        private LocalDate startDate;

        /** 截止日期（含）。 */
        private LocalDate endDate;

        /**
         * 期间状态
         * <li>FUTURE、OPEN、CLOSING、CLOSED 等，由 ledger 定义。</li>
         */
        private String status;

        /** 重开次数。 */
        private Integer reopenCount;
    }

    /** 在日历下新建 FUTURE 会计期间。 */
    @Getter
    @Setter
    public static class CreateAccountingPeriodRequestBody {
        /**
         * 财年
         * <li>1900..9999。</li>
         */
        @NotNull(message = "财年必填")
        @Min(value = 1900, message = "财年为 1900..9999")
        @Max(value = 9999, message = "财年为 1900..9999")
        private Integer fiscalYear;

        /**
         * 期号
         * <li>1..999，同一财年内唯一。</li>
         */
        @NotNull(message = "期号必填")
        @Min(value = 1, message = "期号为 1..999")
        @Max(value = 999, message = "期号为 1..999")
        private Integer periodNo;

        /** 起始日期（含），ISO 日期。 */
        @NotNull(message = "起始日期必填")
        private LocalDate startDate;

        /**
         * 截止日期（含），ISO 日期
         * <li>不早于起始日期，由 ledger 校验。</li>
         */
        @NotNull(message = "截止日期必填")
        private LocalDate endDate;
    }

    /** 新建会计期间结果。 */
    @Getter
    @Setter
    public static class CreateAccountingPeriodResponseBody {
        /** 新会计期间主键。 */
        private String accountingPeriodId;
    }

    /** 开放会计期间结果。 */
    @Getter
    @Setter
    public static class OpenAccountingPeriodResponseBody {
        /** 会计期间主键，与请求一致。 */
        private String accountingPeriodId;

        /** 当前状态。 */
        private String status;
    }

    /** 科目表。 */
    @Getter
    @Setter
    public static class ChartOfAccountsResponseBody {
        /** 科目表主键。 */
        private String chartId;

        /** 科目表代码。 */
        private String chartCode;

        /** 科目表名称。 */
        private String chartName;

        /** 状态。 */
        private String status;
    }

    /** 全部科目表，不分页。 */
    @Getter
    @Setter
    public static class ChartOfAccountsListResponseBody {
        /** 科目表，按代码升序。 */
        private List<ChartOfAccountsResponseBody> items;
    }

    /** 科目。 */
    @Getter
    @Setter
    public static class ChartAccountResponseBody {
        /** 科目主键。 */
        private String chartAccountId;

        /** 所属科目表主键。 */
        private String chartId;

        /** 科目编号：一级 4 位，每下一级加 2 位。 */
        private String accountNo;

        /** 科目代码。 */
        private String accountCode;

        /** 科目名称。 */
        private String accountName;

        /** 科目类别。 */
        private String accountClass;

        /** 正常余额方向。 */
        private String normalSide;

        /** 是否可过账：true 为末级科目，false 为汇总科目。 */
        private Boolean postingAllowed;

        /** 上级科目主键；一级科目为空。 */
        private String parentChartAccountId;

        /** 级次，1..4。 */
        private Integer level;

        /**
         * 启停状态
         * <li>ACTIVE 或 INACTIVE。</li>
         */
        private String accountStatus;
    }

    /** 科目表下的科目，不分页。 */
    @Getter
    @Setter
    public static class ChartAccountListResponseBody {
        /** 科目，按科目编号升序（上级在其下级之前）。 */
        private List<ChartAccountResponseBody> items;
    }

    /** 新增科目。 */
    @Getter
    @Setter
    public static class CreateChartAccountRequestBody {
        /**
         * 科目编号
         * <li>4、6、8 或 10 位数字，首位 1..5；分级规则由 ledger 校验。</li>
         */
        @NotBlank(message = "科目编号必填")
        @Pattern(regexp = ACCOUNT_NO, message = "科目编号必须为 4、6、8 或 10 位数字")
        private String accountNo;

        /**
         * 科目代码
         * <li>大写字母开头，大写字母、数字与下划线，最长 64 个字符；科目表内唯一。</li>
         */
        @NotBlank(message = "科目代码必填")
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "科目代码必须为大写字母、数字与下划线，最长 64 个字符")
        private String accountCode;

        /**
         * 科目名称
         * <li>1..255 个字符。</li>
         */
        @NotBlank(message = "科目名称必填")
        @Size(max = 255, message = "科目名称不超过 255 个字符")
        private String accountName;

        /**
         * 科目类别
         * <li>ASSET、LIABILITY、EQUITY、REVENUE 或 EXPENSE；有上级时须与上级相同。</li>
         */
        @NotBlank(message = "科目类别必填")
        @Pattern(regexp = "ASSET|LIABILITY|EQUITY|REVENUE|EXPENSE", message = "科目类别非法")
        private String accountClass;

        /**
         * 正常余额方向
         * <li>DEBIT 或 CREDIT。</li>
         */
        @NotBlank(message = "余额方向必填")
        @Pattern(regexp = "DEBIT|CREDIT", message = "余额方向只能为 DEBIT 或 CREDIT")
        private String normalSide;

        /**
         * 是否可过账
         * <li>true 为末级科目，false 为汇总科目；创建后不可改。</li>
         */
        @NotNull(message = "科目类型必填")
        private Boolean postingAllowed;

        /**
         * 上级科目编号
         * <li>为空表示一级科目；上级须为启用的汇总科目。</li>
         */
        @Pattern(regexp = PARENT_ACCOUNT_NO, message = "上级科目编号必须为 4、6 或 8 位数字")
        private String parentAccountNo;
    }

    /** 新增科目结果。 */
    @Getter
    @Setter
    public static class CreateChartAccountResponseBody {
        /** 新科目主键。 */
        private String chartAccountId;
    }

    /** 修改科目：名称、编号与上级的完整新值。 */
    @Getter
    @Setter
    public static class UpdateChartAccountRequestBody {
        /**
         * 科目名称
         * <li>1..255 个字符。</li>
         */
        @NotBlank(message = "科目名称必填")
        @Size(max = 255, message = "科目名称不超过 255 个字符")
        private String accountName;

        /**
         * 科目编号
         * <li>科目表已被账簿采用时不能改变。</li>
         */
        @NotBlank(message = "科目编号必填")
        @Pattern(regexp = ACCOUNT_NO, message = "科目编号必须为 4、6、8 或 10 位数字")
        private String accountNo;

        /**
         * 上级科目编号
         * <li>显式为空表示一级科目；科目表已被账簿采用时不能改变。</li>
         */
        @Pattern(regexp = PARENT_ACCOUNT_NO, message = "上级科目编号必须为 4、6 或 8 位数字")
        private String parentAccountNo;
    }

    /** 科目变更记录；只读、不可变。 */
    @Getter
    @Setter
    public static class ChartAccountChangeResponseBody {
        /** 变更记录主键。 */
        private String changeId;

        /** 科目主键。 */
        private String chartAccountId;

        /** 变更后的科目编号。 */
        private String accountNo;

        /**
         * 变更类型
         * <li>CREATE、UPDATE、DISABLE 或 ENABLE。</li>
         */
        private String changeType;

        /** 变更前科目编号；CREATE 为空。 */
        private String beforeAccountNo;

        /** 变更后科目编号。 */
        private String afterAccountNo;

        /** 变更前上级科目编号。 */
        private String beforeParentAccountNo;

        /** 变更后上级科目编号。 */
        private String afterParentAccountNo;

        /** 变更前科目名称。 */
        private String beforeAccountName;

        /** 变更后科目名称。 */
        private String afterAccountName;

        /** 变更前启停状态。 */
        private String beforeAccountStatus;

        /** 变更后启停状态。 */
        private String afterAccountStatus;

        /** 操作人 Account ID。 */
        private String operatorId;

        /** 变更时间，UTC。 */
        private Instant changeTime;
    }

    /** 币种。 */
    @Getter
    @Setter
    public static class CurrencyResponseBody {
        /** ISO 4217 币种代码。 */
        private String currency;

        /** 小数位数。 */
        private Integer minorUnit;

        /** 舍入方式。 */
        private String roundingMode;

        /**
         * 状态
         * <li>DRAFT、ACTIVE 或 INACTIVE。</li>
         */
        private String status;
    }

    /** 币种列表，不分页。 */
    @Getter
    @Setter
    public static class CurrencyListResponseBody {
        /** 币种，按代码升序。 */
        private List<CurrencyResponseBody> items;
    }
}
