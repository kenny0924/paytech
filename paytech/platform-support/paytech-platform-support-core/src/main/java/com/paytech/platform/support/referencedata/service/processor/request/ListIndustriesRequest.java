package com.paytech.platform.support.referencedata.service.processor.request;

import java.util.List;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 查询行业；条件都可选，字段约束由公共 Processor 触发。 */
@Getter
@AllArgsConstructor
public class ListIndustriesRequest {

    /**
     * 层级
     * <li>1 为大类，2 为细类；为空不限制。</li>
     */
    @Min(value = 1, message = "level 只能是 1 或 2")
    @Max(value = 2, message = "level 只能是 1 或 2")
    private final Integer level;

    /**
     * 上级大类编码
     * <li>已去除空白并转大写；为空不限制。</li>
     */
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "parentCode 格式不合法")
    private final String parentCode;

    /** 状态；为空不限制。 */
    private final ReferenceDataStatus status;

    /**
     * 行业编码
     * <li>已去除空白并转大写；为空不限制，最多 50 个。</li>
     */
    @Size(max = 50, message = "codes 最多 50 个")
    private final List<@Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "codes 中的行业编码格式不合法") String> codes;
}
