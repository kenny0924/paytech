package com.paytech.operations.console.bff.service.model;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 币种页面模型：列表、新建草稿、修改草稿与启停
 * <li>字段与 Platform 币种合同一一对应；Platform 为唯一 Owner，启用后小数位与舍入方式冻结由 Platform 校验。</li>
 * <li>写操作回执复用 {@link ConfigurationManagementModels.ConfigurationMutationResponseBody}。</li>
 *
 * @author Claude
 */
public final class CurrencySpecManagementModels {

    /** 只承载类型，禁止创建实例。 */
    private CurrencySpecManagementModels() {
    }

    /** 一条币种规则。 */
    @Getter
    @Setter
    public static class CurrencySpecResponseBody {
        /** ISO 4217 三位大写币种代码。 */
        private String currency;

        /**
         * 最小货币单位位数
         * <li>0..8。</li>
         */
        private Integer minorUnit;

        /**
         * 舍入方式
         * <li>当前只有 HALF_UP。</li>
         */
        private String roundingMode;

        /**
         * 状态
         * <li>DRAFT、ACTIVE 或 INACTIVE；只有 DRAFT 可修改小数位与舍入方式。</li>
         */
        private String status;

        /**
         * 乐观并发版本
         * <li>修改与启停时作为 expectedVersion 回传。</li>
         */
        private Integer version;

        /** 最后更新时间，UTC。 */
        private Instant updateTime;
    }

    /** 全部币种规则，按币种代码升序，不分页。 */
    @Getter
    @Setter
    public static class CurrencySpecListResponseBody {
        /** 币种规则。 */
        private List<CurrencySpecResponseBody> items;
    }

    /** 新建 DRAFT 币种规则。 */
    @Getter
    @Setter
    public static class CreateCurrencySpecRequestBody {
        /**
         * ISO 4217 三位币种代码
         * <li>大写字母，创建后不可变。</li>
         */
        @NotBlank
        @Pattern(regexp = "[A-Z]{3}")
        private String currency;

        /**
         * 最小货币单位位数
         * <li>0..8。</li>
         */
        @NotNull
        @Min(0)
        @Max(8)
        private Integer minorUnit;

        /**
         * 舍入方式
         * <li>当前只有 HALF_UP。</li>
         */
        @NotBlank
        @Pattern(regexp = "HALF_UP")
        private String roundingMode;

        /**
         * 操作原因
         * <li>必填，最多 500 字符。</li>
         */
        @NotBlank
        @Size(max = 500)
        private String reason;
    }

    /** 修改 DRAFT 币种规则的小数位与舍入方式。 */
    @Getter
    @Setter
    public static class UpdateCurrencySpecRequestBody {
        /**
         * 最小货币单位位数
         * <li>0..8。</li>
         */
        @NotNull
        @Min(0)
        @Max(8)
        private Integer minorUnit;

        /**
         * 舍入方式
         * <li>当前只有 HALF_UP。</li>
         */
        @NotBlank
        @Pattern(regexp = "HALF_UP")
        private String roundingMode;

        /**
         * 页面读取的原版本
         * <li>正整数；版本不符由 Platform 返回并发冲突。</li>
         */
        @NotNull
        @Positive
        private Integer expectedVersion;

        /**
         * 操作原因
         * <li>必填，最多 500 字符。</li>
         */
        @NotBlank
        @Size(max = 500)
        private String reason;
    }

    /** 启用或停用币种规则。 */
    @Getter
    @Setter
    public static class ChangeCurrencySpecStatusRequestBody {
        /**
         * 目标状态
         * <li>ACTIVE 或 INACTIVE；允许的转换由 Platform 校验。</li>
         */
        @NotBlank
        @Pattern(regexp = "ACTIVE|INACTIVE")
        private String status;

        /**
         * 页面读取的原版本
         * <li>正整数；版本不符由 Platform 返回并发冲突。</li>
         */
        @NotNull
        @Positive
        private Integer expectedVersion;

        /**
         * 操作原因
         * <li>必填，最多 500 字符。</li>
         */
        @NotBlank
        @Size(max = 500)
        private String reason;
    }
}
