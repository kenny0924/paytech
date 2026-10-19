package com.paytech.commercial.api.term.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** DRAFT 版本价格集合的整体替换：原价格、组件与档位全部删除后按本请求重建。 */
@Getter
@Setter
public class ReplaceCommercialTermPricesRequestBody {
    /** 方案 BCR 根当前修订版本（CAS 令牌）；不等于当前值时 409。 */
    @NotBlank(message = "expectedChangeVersion 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "expectedChangeVersion 格式不正确")
    private String expectedChangeVersion;


    /**
     * 完整价格集合
     * <li>最多 1000 条（stage3 新增 5 的读取上限）；可为空列表，表示清空。</li>
     * <li>同一 OE + 币种内画像代码与优先级各自唯一。</li>
     */
    @NotNull(message = "prices 必填")
    @Size(max = 1000, message = "prices 最多 1000 条")
    @Valid
    private List<CommercialTermPriceRequestBody> prices;
}
