package com.paytech.operations.console.bff.service.model.binlibrary.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.paytech.card.api.binlibrary.response.BinLibraryVersionResponseBody;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** BIN 库版本页面模型：Card 版本字段原样平铺，另加导入人显示名 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LabeledBinLibraryVersionResponseBody {
    /** Card 版本详情，字段平铺到本对象 */
    @JsonUnwrapped
    private BinLibraryVersionResponseBody version;

    /**
     * 导入人显示名
     * <li>Platform 登录名或脱敏邮箱；导入人为空、不是账号 ID、Platform 未返回或查询失败时为 null，页面回退展示 createdBy。</li>
     */
    private String createdByLabel;
}
