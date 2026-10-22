package com.paytech.platform.support.dataquery.template;

import java.util.List;

import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 值节点的输出方式，由引擎提供：JDBC 输出占位符并追加绑定值，以后的 ES / Mongo 输出转义后的 JSON 字面量。
 *
 * <p>语法树与渲染器与引擎无关；绑定器不得把值以未经转义的形式拼进正文。</p>
 */
public interface ValueBinder {

    /**
     * 输出一个值节点。
     *
     * @param value 已按声明转换的值（见 {@link ParameterBinder}），可为 null
     * @param bindings 本次渲染的绑定值，占位式绑定器按输出顺序追加
     * @return 写入正文的文本
     * @throws BizCheckFailException 值不能以该方式输出（如空集合），由渲染器补充正文位置
     */
    String bind(Object value, List<Object> bindings);
}
