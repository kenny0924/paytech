package com.paytech.client.master.api.entityadmin.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 整体替换 Local Partner 的商业角色。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReplacePartnerRolesRequestBody {
    /**
     * 替换后的全部角色 Code
     * <li>OE_PROVIDER、REFERRER，非空且不重复；空数组、重复或其他取值返回 422 ENTITY_ATTRIBUTE_INVALID。</li>
     * <li>仍在提供 ACTIVE 经营主体时不能去掉 OE_PROVIDER（422 ENTITY_ATTRIBUTE_INVALID）。</li>
     */
    @NotNull(message = "roles 必填")
    private List<String> roles;

    /**
     * 读取详情时得到的 changeVersion
     * <li>与当前版本不一致返回 409 ENTITY_CHANGE_VERSION_CONFLICT。</li>
     */
    @NotNull(message = "expectedChangeVersion 必填")
    @Positive(message = "expectedChangeVersion 必须为正整数")
    private Long expectedChangeVersion;

    /**
     * 修改原因
     * <li>1—500 字符，写入审计快照。</li>
     */
    @NotBlank(message = "reason 必填")
    @Size(max = 500, message = "reason 最多 500 字符")
    private String reason;
}
