package com.paytech.platform.support.api.authorization.request;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CLIENT 权限判断请求。 */
@Getter
@Setter
@NoArgsConstructor
public class ClientAuthorizationRequestBody {

    /**
     * 待判断的权限码
     * <li>1..10 个，大写字母、数字与下划线；重复项只判断一次。</li>
     */
    @NotEmpty
    @Size(max = 10)
    private List<@NotNull @Pattern(regexp = "[A-Z][A-Z0-9_]{0,127}") String> permissionCodes;

    /** 测试与 BFF 构造请求。 */
    public ClientAuthorizationRequestBody(List<String> permissionCodes) {
        this.permissionCodes = permissionCodes;
    }
}
