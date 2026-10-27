package com.paytech.platform.support.referencedata.service.processor.request;

import java.util.List;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 查询行政区；条件都可选，字段约束由公共 Processor 触发。 */
@Getter
@AllArgsConstructor
public class ListCountrySubdivisionsRequest {

    /**
     * 国家代码
     * <li>已去除空白并转大写；为空不限制。</li>
     */
    @Pattern(regexp = "[A-Z]{2}", message = "countryCode 必须是两位大写 ISO 3166-1 alpha-2 代码")
    private final String countryCode;

    /** 状态；为空不限制。 */
    private final ReferenceDataStatus status;

    /**
     * 行政区代码
     * <li>已去除空白并转大写；为空不限制，最多 50 个。</li>
     */
    @Size(max = 50, message = "codes 最多 50 个")
    private final List<@Pattern(regexp = "[A-Z]{2}-[A-Z0-9]{1,3}", message = "codes 必须是 ISO 3166-2 行政区代码")
        String> codes;
}
