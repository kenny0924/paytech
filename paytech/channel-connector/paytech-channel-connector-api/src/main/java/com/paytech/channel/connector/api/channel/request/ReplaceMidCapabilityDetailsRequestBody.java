package com.paytech.channel.connector.api.channel.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 整组替换能力明细：同一事务内删除原明细并写入本次全部明细。 */
@Getter
@Setter
public class ReplaceMidCapabilityDetailsRequestBody {
    /**
     * 替换后的全部能力明细
     * <li>1–64 项，同一能力代码不能重复。</li>
     */
    @NotEmpty(message = "能力明细至少一项")
    @Size(max = 64, message = "能力明细最多 64 项")
    private List<@NotNull(message = "能力明细不能为空") @Valid MidCapabilityDetailRequestBody> details;
}
