package com.paytech.payout.core.configuration;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * 收款账户字段要求静态清单（R5、Q-1），由 classpath:beneficiary-requirements.yaml 导入
 * <li>过渡期来源：S3 由 channel-connector 出款账户能力取代后删除该文件与本配置。</li>
 * <li>按收款方式 × 开户国家 × 币种匹配；bankCountry 为 * 的条目只在没有精确国家条目时生效（SWIFT 任一 ISO 国家）。</li>
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties("payout.beneficiary-requirements")
public class BeneficiaryRequirementProperties {
    /** 清单条目；为空时所有组合都不支持提交 */
    @Valid
    private List<RouteRequirement> routes = new ArrayList<>();

    /** 一个收款方式 × 国家 × 币种集合的字段要求。 */
    @Getter
    @Setter
    public static class RouteRequirement {
        /** 收款方式：LOCAL、SWIFT */
        @NotBlank
        @Pattern(regexp = "LOCAL|SWIFT")
        private String railType;

        /** 开户国家；* 表示任一国家 */
        @NotBlank
        @Pattern(regexp = "[A-Z]{2}|\\*")
        private String bankCountry;

        /** 适用币种 */
        @NotEmpty
        private List<@Pattern(regexp = "[A-Z]{3}") String> currencies = new ArrayList<>();

        /** 字段要求，按展示顺序 */
        @NotEmpty
        @Valid
        private List<FieldRequirement> fields = new ArrayList<>();
    }

    /** 一个字段的要求。 */
    @Getter
    @Setter
    public static class FieldRequirement {
        /** 字段代码：holderName、iban、accountNumber、bankCode、swiftBic、bankName、accountType */
        @NotBlank
        @Pattern(regexp = "holderName|iban|accountNumber|bankCode|swiftBic|bankName|accountType")
        private String code;

        /** 是否单独必填 */
        private boolean required;

        /** 二选一分组；同组至少且至多填一个，无分组为空 */
        private String oneOfGroup;

        /** 规范化后整体匹配的正则；为空不校验格式 */
        private String pattern;

        /** 最大长度（规范化后） */
        @Positive
        private int maxLength;

        /** 前端文案 key */
        @NotBlank
        private String labelKey;
    }
}
