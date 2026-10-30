package com.paytech.risk.decision.api.control.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 存量 Client 迁移为 STANDARD（L12，内部接口）
 * <li>Client 列表由发布执行人从 commercial（有 ENABLED 支付方式开通项的 CLIENT 合同）与 client-master 只读导出。</li>
 * <li>已有档位的 Client 跳过；每个 Client 一个短事务。</li>
 */
@Getter
@Setter
public class MigrateClientTiersRequestBody {

    /**
     * 发布执行人的 Operations 账号 ID
     * <li>记为迁移变更记录的申请人与档位行的建档人（迁移不经四眼，执行人即批准人）。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String executedBy;

    /** 迁移理由，记入变更记录，1—1000 字符。 */
    @NotBlank
    @Size(max = 1000)
    private String reason;

    /** 待迁移的 Client，1—500 个。 */
    @NotEmpty
    @Size(max = 500)
    private List<@Valid TierMigrationClientBody> clients;
}
