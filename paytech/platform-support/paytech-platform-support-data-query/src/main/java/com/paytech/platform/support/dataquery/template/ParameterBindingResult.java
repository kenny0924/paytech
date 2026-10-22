package com.paytech.platform.support.dataquery.template;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.paytech.foundation.common.error.InvalidParameterException;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 参数校验与转换结果：没有错误时 values 可用于渲染。 */
@Getter
@AllArgsConstructor
public final class ParameterBindingResult {

    /** 转换后的值，含系统参数；有错误时不完整，不得用于渲染。 */
    private final Map<String, Object> values;

    /** 字段级错误，按发现顺序。 */
    private final List<ParameterError> errors;

    /**
     * 要求没有错误。
     *
     * @return 转换后的值
     * @throws InvalidParameterException 存在字段错误，消息列出全部字段与原因
     */
    public Map<String, Object> requireValues() {
        if (!errors.isEmpty()) {
            throw new InvalidParameterException(errors.stream().map(error -> error.getField() + " " + error.getMessage())
                .collect(Collectors.joining("；")));
        }
        return values;
    }
}
