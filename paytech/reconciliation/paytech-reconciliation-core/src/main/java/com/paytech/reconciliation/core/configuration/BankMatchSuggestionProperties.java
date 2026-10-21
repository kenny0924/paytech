package com.paytech.reconciliation.core.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import lombok.Getter;
import lombok.Setter;

/** 银行匹配建议（BANK_MATCH_SUGGEST，设计 F2 自动建议规则）的配置。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties("reconciliation.bank-match")
public class BankMatchSuggestionProperties {
    /**
     * 起息日与预期日相差的工作日上限 N（±N）
     * <li>默认 3，取值 0–30；只排除周六、周日（切片 1 不接节假日日历）。每次建议任务执行时读取，已生成的建议不重算。</li>
     */
    @NotNull
    @Min(0)
    @Max(30)
    private Integer suggestionWindowBusinessDays = 3;
}
