package com.paytech.platform.support.dataquery.template;

import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渲染结果：正文与按出现顺序排列的绑定值；可把正文下标换算回模板位置。 */
@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class RenderedQuery {

    /** 渲染后的正文；JDBC 引擎下值以 ? 表示。 */
    private final String text;

    /** 绑定值，按占位符出现顺序，元素可为 null。 */
    private final List<Object> bindings;

    /** 输出来源标记，按 outputOffset 升序。 */
    @Getter(AccessLevel.NONE)
    private final List<SourceMark> marks;

    /**
     * 把正文下标换算回模板位置，用于把只读校验问题定位到模板行号。
     *
     * @param offset 正文下标；越界时按最近的标记处理
     * @return 模板位置；没有任何标记时为 null
     */
    public SourcePosition locate(int offset) {
        SourceMark found = null;
        for (SourceMark mark : marks) {
            if (mark.getOutputOffset() > offset) {
                break;
            }
            found = mark;
        }
        if (found == null) {
            return marks.isEmpty() ? null : marks.getFirst().getPosition();
        }
        if (!found.isLiteral()) {
            return found.getPosition();
        }
        int end = Math.min(offset, text.length());
        return found.getPosition().advance(text, found.getOutputOffset(), Math.max(end, found.getOutputOffset()));
    }
}
