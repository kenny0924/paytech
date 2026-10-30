package com.paytech.platform.support.dataquery.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.paytech.platform.support.dataquery.core.enums.QueryKeyRole;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 模板版本的联合查询配置（joint_json）：行主体键类型、键参数与列标注；与正文同版本保存，不可变。 */
@Getter
@AllArgsConstructor
public final class QueryJointConfig {

    /** 行主体键类型编码：结果每一行是什么，决定链路图泳道。 */
    private final String subjectKeyType;

    /** 键参数。 */
    private final QueryJointKeyParameter keyParameter;

    /** 列标注，按声明顺序；没有时为空列表。 */
    private final List<QueryJointColumn> columns;

    /**
     * 引用的全部键类型编码：行主体、键参数与列标注。
     *
     * @return 保持首次出现顺序的编码集合
     */
    public Set<String> referencedKeyTypes() {
        Set<String> codes = new LinkedHashSet<>();
        codes.add(subjectKeyType);
        codes.add(keyParameter.getKeyType());
        columns.forEach(column -> codes.add(column.getKeyType()));
        return codes;
    }

    /**
     * 是否引用某键类型。
     *
     * @param keyTypeCode 键类型编码
     * @return 行主体、键参数或任一列引用时为 true
     */
    public boolean references(String keyTypeCode) {
        return referencedKeyTypes().contains(keyTypeCode);
    }

    /**
     * 链路列的键类型，按列顺序去重。
     *
     * @return 键类型编码
     */
    public List<String> linkKeyTypes() {
        Set<String> codes = new LinkedHashSet<>();
        columns.stream().filter(column -> column.getKeyRole() == QueryKeyRole.LINK)
            .forEach(column -> codes.add(column.getKeyType()));
        return List.copyOf(codes);
    }

    /**
     * 按结果列名查找标注（不区分大小写）。
     *
     * @param columnName 结果列名
     * @return 标注；没有时为 null
     */
    public QueryJointColumn column(String columnName) {
        String name = columnName.toLowerCase(Locale.ROOT);
        return columns.stream().filter(column -> column.getName().toLowerCase(Locale.ROOT).equals(name)).findFirst()
            .orElse(null);
    }
}
