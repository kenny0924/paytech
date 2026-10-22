package com.paytech.platform.support.dataquery.template;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 条件块 {{#if}} … {{#elseif}} … {{#else}} … {{/if}}；按顺序取第一个成立的分支。 */
@Getter
@AllArgsConstructor
public final class IfNode implements TemplateNode {

    /** {{#if 的位置。 */
    private final SourcePosition position;

    /** 分支，至少一个；带 #else 时它是最后一个。 */
    private final List<IfBranch> branches;

    /**
     * 是否带 #else 分支。
     *
     * @return true 表示总会命中某个分支
     */
    public boolean hasElse() {
        return branches.getLast().getCondition() == null;
    }

    /**
     * 可能的走向数：每个分支一种，无 #else 时再加“都不成立”一种。
     *
     * @return 走向数
     */
    public int outcomeCount() {
        return hasElse() ? branches.size() : branches.size() + 1;
    }
}
