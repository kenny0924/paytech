package com.paytech.vault.client.admin.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 数据类型列表与当前策略版本。
 */
@Getter
@Setter
public class DataTypeListResponseBody {
    /**
     * 当前策略版本号
     * <li>0 表示尚无任何策略变更。</li>
     */
    private int currentPolicyVersion;

    /**
     * 全部数据类型（含已停用）
     */
    private List<DataType> items;
}
