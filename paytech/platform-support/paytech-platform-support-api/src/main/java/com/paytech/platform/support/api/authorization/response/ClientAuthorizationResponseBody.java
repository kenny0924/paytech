package com.paytech.platform.support.api.authorization.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CLIENT 权限判断结果。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientAuthorizationResponseBody {

    /** 请求中被授予的权限码；全部未授予时为空列表。 */
    private List<String> granted;
}
