package com.paytech.platform.support.api.operations.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Operations 账号显示名查询结果。 */
@Getter
@Setter
public class OperationsAccountLabelListResponseBody {

    /**
     * 账号显示名
     * <li>按账号 ID 升序；只包含查询到且有显示名的 OPERATIONS 账号，最多 50 个。</li>
     */
    private List<OperationsAccountLabelResponseBody> items;
}
