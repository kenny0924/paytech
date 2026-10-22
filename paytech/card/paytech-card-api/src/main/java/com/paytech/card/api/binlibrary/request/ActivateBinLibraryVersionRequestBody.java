package com.paytech.card.api.binlibrary.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/** 激活或回切 BIN 库版本请求 */
@Getter
@Setter
public class ActivateBinLibraryVersionRequestBody {
    /**
     * 操作人页面看到的当前生效版本号
     * <li>没有生效版本时为 null；与实际不符返回 409，防止两个运营人员同时切换。</li>
     */
    @Min(value = 1, message = "期望生效版本号无效")
    @Max(value = 999999999, message = "期望生效版本号无效")
    private Integer expectedActiveVersionNo;
}
