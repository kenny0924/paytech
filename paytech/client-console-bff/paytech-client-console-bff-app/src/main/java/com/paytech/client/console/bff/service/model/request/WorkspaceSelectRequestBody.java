package com.paytech.client.console.bff.service.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** C02 进入工作区：只提交成员 ID，Tenant 由 Platform 从真实成员解析。 */
@Getter
@Setter
public class WorkspaceSelectRequestBody {

    /**
     * 目标工作区的成员 ID
     * <li>规范正整数；归属与状态由 Platform 判定。</li>
     */
    @NotNull
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String memberId;
}
