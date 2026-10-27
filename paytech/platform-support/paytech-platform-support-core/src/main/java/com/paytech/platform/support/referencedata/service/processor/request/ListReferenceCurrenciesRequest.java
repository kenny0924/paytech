package com.paytech.platform.support.referencedata.service.processor.request;

import java.util.List;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 查询参考数据币种；条件都可选，字段约束由公共 Processor 触发。 */
@Getter
@AllArgsConstructor
public class ListReferenceCurrenciesRequest {

    /** 状态；为空不限制。 */
    private final ReferenceDataStatus status;

    /**
     * 币种代码
     * <li>已去除空白并转大写；为空不限制，最多 50 个。</li>
     */
    @Size(max = 50, message = "codes 最多 50 个")
    private final List<@Pattern(regexp = "[A-Z]{3}", message = "codes 必须是三位大写 ISO 4217 代码") String> codes;
}
