package com.paytech.platform.support.workflow.domainservice.process;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.paytech.platform.support.workflow.core.common.WorkflowValueFormats;
import com.paytech.platform.support.workflow.core.enums.DataQueryCheckFailure;
import com.paytech.platform.support.workflow.domain.action.DataQueryOutput;
import com.paytech.platform.support.workflow.domain.runtime.DataQueryCheckOutcome;
import com.paytech.platform.support.workflow.domainservice.validator.DataQueryTemplateRules;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 按单行约定解读 DATA_QUERY 查询结果（决定 3.3，technique 10.1）。纯内存计算。
 * <li>0 行为未满足（无原因码）；多于 1 行或还有更多行为形状不符。列名不区分大小写。</li>
 * <li>satisfied 接受布尔或文本 1 / 0 / true / false，缺列、null 或其他取值为形状不符；missing 缺列或为空即无原因码，
 * 否则逗号分隔、逐项去空白，每项须为大写编码，最多 20 项、合计不超过 512 字符。</li>
 * <li>满足时只按声明读取 out_&lt;name&gt; 并按声明类型规整：任一缺列或类型不符整体判形状不符、不写输出，保证排他网关求值
 * 不会因类型抛错；未声明的 out_ 列忽略（B5）。</li>
 */
public final class DataQueryResultReader {

    /** 原因码上限：项数。 */
    static final int MAX_MISSING_ITEMS = 20;

    /** 原因码上限：合计字符数（与 check_detail 列长度一致）。 */
    static final int MAX_MISSING_LENGTH = 512;

    private static final Pattern REASON_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private static final Set<String> TRUE_TEXTS = Set.of("1", "true");

    private static final Set<String> BOOLEAN_TEXTS = Set.of("1", "0", "true", "false");

    /**
     * 解读查询结果。
     *
     * @param columnNames 结果列名，与行内值对齐
     * @param rows 数据行
     * @param hasMore 是否还有更多行
     * @param outputs 声明的输出
     * @return 满足（含规整后的输出）、未满足（含原因码）或形状不符的失败
     */
    public DataQueryCheckOutcome read(List<String> columnNames, List<List<Object>> rows, boolean hasMore,
        List<DataQueryOutput> outputs) {
        if (rows.isEmpty() && !hasMore) {
            return DataQueryCheckOutcome.unsatisfied(List.of());
        }
        if (rows.size() != 1 || hasMore) {
            return shapeInvalid();
        }
        Map<String, Object> row = row(columnNames, rows.get(0));
        Boolean satisfied = bool(row, DataQueryTemplateRules.SATISFIED_COLUMN);
        List<String> missing = missing(row.get(DataQueryTemplateRules.MISSING_COLUMN));
        if (satisfied == null || missing == null) {
            return shapeInvalid();
        }
        if (!satisfied) {
            return DataQueryCheckOutcome.unsatisfied(missing);
        }
        ObjectNode values = outputs(row, outputs);
        return values == null ? shapeInvalid() : DataQueryCheckOutcome.satisfied(values);
    }

    /**
     * 按小写列名取值；同名列取第一列。
     *
     * @param columnNames 列名
     * @param values 行值
     * @return 小写列名到值
     */
    static Map<String, Object> row(List<String> columnNames, List<Object> values) {
        Map<String, Object> row = new HashMap<>();
        for (int index = 0; index < Math.min(columnNames.size(), values.size()); index++) {
            row.putIfAbsent(columnNames.get(index).toLowerCase(Locale.ROOT), values.get(index));
        }
        if (columnNames.size() != values.size()) {
            row.clear();
        }
        return row;
    }

    /**
     * satisfied：布尔或 1 / 0 / true / false 文本。
     *
     * @param row 行
     * @param column 列名
     * @return 布尔；缺列、null 或其他取值为 null
     */
    static Boolean bool(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text && BOOLEAN_TEXTS.contains(text.strip().toLowerCase(Locale.ROOT))) {
            return TRUE_TEXTS.contains(text.strip().toLowerCase(Locale.ROOT));
        }
        return null;
    }

    /**
     * missing：缺列、null 或空白为无原因码；否则逗号分隔的大写编码。
     *
     * @param value 列值
     * @return 原因码；不合法为 null
     */
    static List<String> missing(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof String text)) {
            return null;
        }
        List<String> codes = new ArrayList<>();
        for (String item : text.split(",")) {
            String code = item.strip();
            if (code.isEmpty()) {
                continue;
            }
            if (!REASON_CODE.matcher(code).matches()) {
                return null;
            }
            codes.add(code);
        }
        boolean tooMany = codes.size() > MAX_MISSING_ITEMS || String.join(",", codes).length() > MAX_MISSING_LENGTH;
        return tooMany ? null : codes;
    }

    /**
     * 声明的输出按类型规整为 JSON。
     *
     * @param row 行
     * @param outputs 声明的输出
     * @return 输出对象；任一缺列或类型不符为 null
     */
    static ObjectNode outputs(Map<String, Object> row, List<DataQueryOutput> outputs) {
        ObjectNode values = JsonNodeFactory.instance.objectNode();
        for (DataQueryOutput output : outputs) {
            String column = output.column().toLowerCase(Locale.ROOT);
            if (!row.containsKey(column)) {
                return null;
            }
            JsonNode value = normalize(output, row.get(column));
            if (value == null) {
                return null;
            }
            values.set(output.name(), value);
        }
        return values;
    }

    /**
     * 按声明类型规整一个值；null 写 JSON null。
     *
     * @param output 输出声明
     * @param value 列值（String、Boolean 或 null）
     * @return JSON 值；类型不符为 null
     */
    static JsonNode normalize(DataQueryOutput output, Object value) {
        JsonNodeFactory nodes = JsonNodeFactory.instance;
        if (value == null) {
            return nodes.nullNode();
        }
        String text = value instanceof Boolean flag ? flag.toString() : value instanceof String string ? string : null;
        if (text == null) {
            return null;
        }
        return switch (output.type()) {
            case TEXT -> nodes.stringNode(text);
            case NUMBER -> WorkflowValueFormats.isDecimal(text) ? nodes.stringNode(text) : null;
            case BOOLEAN -> BOOLEAN_TEXTS.contains(text.toLowerCase(Locale.ROOT))
                ? nodes.booleanNode(TRUE_TEXTS.contains(text.toLowerCase(Locale.ROOT))) : null;
            case ENUM -> output.enumValues().contains(text) ? nodes.stringNode(text) : null;
            case DATE -> WorkflowValueFormats.isDate(text) ? nodes.stringNode(text) : null;
            default -> null;
        };
    }

    /** 形状不符。 */
    static DataQueryCheckOutcome shapeInvalid() {
        return DataQueryCheckOutcome.failed(DataQueryCheckFailure.RESULT_SHAPE_INVALID);
    }
}
