package com.paytech.platform.support.accesscontrol.service.processor.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 批量查询 Operations 账号显示名；字段约束由公共 Processor 触发。 */
@Getter
@AllArgsConstructor
public class ListOperationsAccountLabelsRequest {

    /**
     * 调用方服务名
     * <li>已按内部调用身份解析；携带已验证内部 Token 时为 Token sub。</li>
     */
    @NotBlank(message = "X-Caller-Service 不能为空")
    private final String callerService;

    /**
     * 账号 ID
     * <li>1 到 50 个正整数，可重复。</li>
     */
    @NotEmpty(message = "accountIds 不能为空")
    @Size(max = 50, message = "accountIds 最多 50 个")
    private final List<@NotNull @Positive(message = "accountIds 必须是正整数") Long> accountIds;
}
