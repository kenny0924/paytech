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
 * 账务基础资料页面模型：财务主体、财务日历、会计期间、科目表与币种
 * <li>字段与 ledger 合同一一对应，由 Client 适配器严格转换；Owner 新增字段即视为合同漂移。</li>
 * <li>金额、ID 与枚举均为字符串；时间为 UTC Instant，日期为 ISO 日期。</li>
 *
 * @author Claude
 */
public final class FinanceSetupModels {

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

        /** 科目代码。 */
        private String accountCode;

        /** 科目名称。 */
        private String accountName;

        /** 科目类别。 */
        private String accountClass;

        /** 正常余额方向。 */
        private String normalSide;

        /** 是否可过账。 */
        private Boolean postingAllowed;
    }

    /** 科目表下的科目，不分页。 */
    @Getter
    @Setter
    public static class ChartAccountListResponseBody {
        /** 科目，按代码升序。 */
        private List<ChartAccountResponseBody> items;
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
