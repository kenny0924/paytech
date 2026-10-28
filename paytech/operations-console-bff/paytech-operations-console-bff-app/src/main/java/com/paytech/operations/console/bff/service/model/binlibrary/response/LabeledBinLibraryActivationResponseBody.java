package com.paytech.operations.console.bff.service.model.binlibrary.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.paytech.card.api.binlibrary.response.BinLibraryActivationResponseBody;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** BIN 库激活记录页面模型：Card 激活字段原样平铺，另加操作人显示名 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LabeledBinLibraryActivationResponseBody {
    /** Card 激活或回切记录，字段平铺到本对象 */
    @JsonUnwrapped
    private BinLibraryActivationResponseBody activation;

    /**
     * 操作人显示名
     * <li>Platform 登录名或脱敏邮箱；操作人为空、不是账号 ID、Platform 未返回或查询失败时为 null，页面回退展示 operatorAccountId。</li>
     */
    private String operatorLabel;
}
