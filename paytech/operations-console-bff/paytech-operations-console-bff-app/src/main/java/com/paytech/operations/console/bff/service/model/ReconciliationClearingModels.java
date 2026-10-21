package com.paytech.operations.console.bff.service.model;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/**
 * 对账清算账户页面模型
 * <li>字段与 reconciliation-api 合同一一对应，由 Client 适配器严格转换；Owner 新增字段即视为合同漂移。</li>
 * <li>操作人不在请求体中，由 BFF 从会话填入 X-Gateway-Actor-Subject / X-Gateway-Session-Id。</li>
 *
 * @author Claude
 */
public final class ReconciliationClearingModels {

    /** 只承载类型，禁止创建实例。 */
    private ReconciliationClearingModels() {
    }

    /** 新建清算账户请求。 */
    @Getter
    @Setter
    public static class CreateClearingAccountRequestBody {
        /**
         * 清算账户编码
         * <li>全局唯一，渠道账单文件头用它指明所属清算账户；创建后不可改。</li>
         */
        @NotBlank
        @Pattern(regexp = "[A-Z][A-Z0-9_-]{1,63}", message = "必须是大写字母开头的大写字母、数字、下划线或连字符，2–64 位")
        private String clearingAccountCode;

        /**
         * 渠道编码
         * <li>channel-connector 渠道目录中的编码；挂入的 MID 必须属于该渠道。</li>
         */
        @NotBlank
        @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "必须是大写字母开头的大写字母、数字或下划线，2–64 位")
        private String channelCode;

        /**
         * 与渠道签约的经营主体编号
         * <li>挂入的 MID 必须属于该主体。</li>
         */
        @NotBlank
        @Pattern(regexp = "[A-Z]{2}[0-9]{3}", message = "必须是两位大写字母加三位数字")
        private String entityNo;

        /**
         * 清算币种
         * <li>大写 ISO 4217；渠道账单币种必须与之相同。</li>
         */
        @NotBlank
        @Pattern(regexp = "[A-Z]{3}", message = "必须是大写 ISO 4217 三位代码")
        private String currency;

        /**
         * 到账银行账户主键
         * <li>可为空；非空时必须是同主体、同币种、用途 CHANNEL_SETTLEMENT 的 ACTIVE 银行账户。为空时账单不能对平。</li>
         */
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
        private String settlementCashAccountId;

        /**
         * 账单行未命中平台流水的等待天数
         * <li>期满转为 CHANNEL_LINE_UNMATCHED 异常；0–365。</li>
         */
        @NotNull
        @Min(0)
        @Max(365)
        private Integer unmatchedWaitDays;

        /**
         * 单笔平台流水实际渠道费与预估之差的容差
         * <li>主货币单位非负十进制字符串，不超过币种精度；超过开 COST_DEVIATION 异常。</li>
         */
        @NotBlank
        @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的非负普通十进制字符串")
        private String costToleranceAmount;

        /**
         * 预期银行流水到账宽限天数
         * <li>超过预期日该天数仍未到账视为逾期（超期检测在切片 2 使用）；0–365。</li>
         */
        @NotNull
        @Min(0)
        @Max(365)
        private Integer cashGraceDays;
    }

    /** 修改清算账户配置请求：整体替换三项配置与到账银行账户。 */
    @Getter
    @Setter
    public static class UpdateClearingAccountSettingsRequestBody {
        /**
         * 到账银行账户主键
         * <li>为空表示解除；非空时必须是同主体、同币种、用途 CHANNEL_SETTLEMENT 的 ACTIVE 银行账户。已生成的预期银行流水不随之改变。</li>
         */
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
        private String settlementCashAccountId;

        /**
         * 账单行未命中平台流水的等待天数
         * <li>0–365。</li>
         */
        @NotNull
        @Min(0)
        @Max(365)
        private Integer unmatchedWaitDays;

        /**
         * 实际渠道费与预估之差的容差
         * <li>主货币单位非负十进制字符串，不超过币种精度。</li>
         */
        @NotBlank
        @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的非负普通十进制字符串")
        private String costToleranceAmount;

        /**
         * 预期银行流水到账宽限天数
         * <li>0–365。</li>
         */
        @NotNull
        @Min(0)
        @Max(365)
        private Integer cashGraceDays;
    }

    /** 向清算账户挂入 MID 请求；币种取清算账户币种。 */
    @Getter
    @Setter
    public static class AddClearingAccountMidRequestBody {
        /**
         * channel-connector MID 主键
         * <li>必须 ACTIVE，渠道与经营主体等于清算账户，能力组含清算币种。</li>
         */
        @NotBlank
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
        private String midId;
    }

    /** 启用或停用清算账户请求。 */
    @Getter
    @Setter
    public static class ChangeClearingAccountStatusRequestBody {
        /**
         * 目标状态
         * <li>ACTIVE 或 INACTIVE；停用后不再接收新的平台流水和渠道账单，也不参与解析。</li>
         */
        @NotBlank
        @Pattern(regexp = "ACTIVE|INACTIVE", message = "只能为 ACTIVE 或 INACTIVE")
        private String status;
    }

    /** 清算账户详情：配置与已挂入的 MID。 */
    @Getter
    @Setter
    public static class ClearingAccountResponseBody {
        /** 清算账户主键 */
        private String clearingAccountId;

        /** 清算账户编码 */
        private String clearingAccountCode;

        /** 渠道编码 */
        private String channelCode;

        /** 经营主体编号 */
        private String entityNo;

        /** 清算币种 */
        private String currency;

        /**
         * 到账银行账户主键
         * <li>未配置为空。</li>
         */
        private String settlementCashAccountId;

        /** 账单行未命中等待天数 */
        private Integer unmatchedWaitDays;

        /**
         * 实际渠道费容差
         * <li>按币种精度补齐小数位。</li>
         */
        private String costToleranceAmount;

        /** 预期银行流水到账宽限天数 */
        private Integer cashGraceDays;

        /** 状态：ACTIVE、INACTIVE */
        private String status;

        /** 乐观锁版本 */
        private String version;

        /** 创建的 UTC 时间 */
        private Instant createTime;

        /** 最后更新的 UTC 时间 */
        private Instant updateTime;

        /** 已挂入的 MID，按挂入时间升序 */
        private List<Mid> mids;

        /** 清算账户所含的 MID。 */
        @Getter
        @Setter
        public static class Mid {
            /** channel-connector MID 主键 */
            private String midId;

            /** 挂入的 UTC 时间 */
            private Instant createTime;

            /**
             * 渠道侧外部 MID 编码
             * <li>BFF 聚合：只在清算账户详情中由 channel-connector MID 详情补齐；命令回执不含该字段。</li>
             */
            @JsonInclude(JsonInclude.Include.NON_NULL)
            private String externalMidCode;

            /**
             * MID 当前状态：DRAFT、ACTIVE、SUSPENDED、CLOSED
             * <li>BFF 聚合：只在清算账户详情中补齐；命令回执不含该字段。</li>
             */
            @JsonInclude(JsonInclude.Include.NON_NULL)
            private String midStatus;
        }
    }

    /** 清算账户列表项。 */
    @Getter
    @Setter
    public static class ClearingAccountSummaryResponseBody {
        /** 清算账户主键 */
        private String clearingAccountId;

        /** 清算账户编码 */
        private String clearingAccountCode;

        /** 渠道编码 */
        private String channelCode;

        /** 经营主体编号 */
        private String entityNo;

        /** 清算币种 */
        private String currency;

        /**
         * 到账银行账户主键
         * <li>未配置为空。</li>
         */
        private String settlementCashAccountId;

        /** 状态：ACTIVE、INACTIVE */
        private String status;

        /** 已挂入的 MID 数 */
        private Integer midCount;

        /** 最后更新的 UTC 时间 */
        private Instant updateTime;
    }

    /** 已激活但尚未归入清算账户的 MID + 币种列表。 */
    @Getter
    @Setter
    public static class UnassignedMidListResponseBody {
        /** 未归属项，按渠道编码、经营主体、MID 主键、币种升序 */
        private List<UnassignedMid> items;

        /** 一个未归属的 MID + 币种。 */
        @Getter
        @Setter
        public static class UnassignedMid {
            /** channel-connector MID 主键 */
            private String midId;

            /** 渠道侧外部 MID 编码 */
            private String externalMidCode;

            /** 渠道编码 */
            private String channelCode;

            /** 经营主体编号 */
            private String entityNo;

            /** 能力组中的币种 */
            private String currency;
        }
    }
}
