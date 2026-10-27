package com.paytech.platform.support.referencedata.service.processor.request;

import java.util.List;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 分页搜索 MCC；条件都可选，字段约束由公共 Processor 触发。 */
@Getter
@AllArgsConstructor
public class SearchMccsRequest {

    /**
     * 关键字
     * <li>已去除首尾空白，空白视为不限制；最多 64 字符。</li>
     */
    @Size(max = 64, message = "keyword 最多 64 字符")
    private final String keyword;

    /** 状态；为空不限制。 */
    private final ReferenceDataStatus status;

    /**
     * MCC 编码
     * <li>已去除空白；为空不限制，最多 50 个。</li>
     */
    @Size(max = 50, message = "codes 最多 50 个")
    private final List<@Pattern(regexp = "[0-9]{4}", message = "codes 必须是四位数字 MCC") String> codes;

    /** 已校验的分页。 */
    @NotNull
    private final PagingRequestBody paging;
}
