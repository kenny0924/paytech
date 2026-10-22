package com.paytech.platform.support.dataquery.engine;

import java.util.List;

import com.paytech.platform.support.dataquery.template.RenderedQueryViolation;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.sf.jsqlparser.statement.select.Select;

/** 只读校验结果：通过时带语法树供分页改写，未通过时带全部拒绝原因。 */
@Getter
@AllArgsConstructor
public final class SqlInspection {

    /** 语法树；未通过时为 null。 */
    private final Select select;

    /** 拒绝原因，下标指向被校验的 SQL；通过时为空。 */
    private final List<RenderedQueryViolation> violations;

    /**
     * 是否通过。
     *
     * @return true 表示只读校验通过
     */
    public boolean passed() {
        return violations.isEmpty();
    }
}
