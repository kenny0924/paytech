package com.paytech.risk.control.service.processor.request;

import java.util.List;

import com.paytech.risk.control.domain.limittier.TierClientProfile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 存量 Client 迁移为 STANDARD 的 Owner 输入。 */
@Getter
@AllArgsConstructor
public class MigrateClientTiersRequest {
    /** 私网调用方服务名；须在迁移白名单内。 */
    private final String callerService;

    /** 发布执行人账号 ID。 */
    @Positive
    private final long executedBy;

    /** 迁移理由。 */
    @NotBlank
    private final String reason;

    /** 待迁移 Client。 */
    @NotEmpty
    private final List<TierClientProfile> clients;
}
