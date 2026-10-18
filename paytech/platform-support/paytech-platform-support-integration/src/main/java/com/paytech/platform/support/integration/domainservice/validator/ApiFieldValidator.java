package com.paytech.platform.support.integration.domainservice.validator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.api.enums.ApiFieldDataType;
import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.integration.domain.FieldDefinition;
import com.paytech.platform.support.integration.domain.ValidationIssue;

/**
 * 字段组的静态规则（物理设计第 3 节、逻辑设计 3.1～3.3），标准 API 与原子 API 共用。
 *
 * <p>纯内存校验，不访问数据库；返回全部问题而不是在第一处失败，便于编辑器一次展示。
 */
@Component
public class ApiFieldValidator {

    /** 每个 API 每个方向的字段上限。 */
    public static final int MAX_FIELDS = 128;

    /** BODY 路径最多段数。 */
    public static final int MAX_SEGMENTS = 16;

    private static final Pattern BODY_SEGMENT = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$-]{0,63}(\\[])?");
    private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,64}");
    private static final Pattern QUERY_NAME = Pattern.compile("[A-Za-z0-9_.~-]{1,64}");
    private static final Pattern PATH_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

    /**
     * 校验一组字段。
     *
     * @param fields 同一 API、同一方向的字段，顺序即显示顺序
     * @param allowed 该方向允许的位置
     * @param container 请求体中的数组名，如 requestFields，用于问题定位
     * @return 发现的问题；为空表示通过
     */
    public List<ValidationIssue> validate(List<FieldDefinition> fields, Set<ApiFieldLocation> allowed,
        String container) {
        var issues = new ArrayList<ValidationIssue>();
        if (fields.size() > MAX_FIELDS) {
            issues.add(new ValidationIssue("FIELD_LIMIT_EXCEEDED", container, "每个方向最多 " + MAX_FIELDS + " 个字段"));
            return issues;
        }
        var seen = new HashSet<String>();
        for (int index = 0; index < fields.size(); index++) {
            var field = fields.get(index);
            String location = container + "[" + index + "]";
            checkField(field, allowed, location, issues);
            if (!seen.add(identity(field))) {
                issues.add(new ValidationIssue("FIELD_DUPLICATED", location + ".fieldPath", "同一位置的字段路径重复"));
            }
        }
        checkParents(fields, container, issues);
        return issues;
    }

    /** 位置、路径语法与类型的单字段规则。 */
    void checkField(FieldDefinition field, Set<ApiFieldLocation> allowed, String location,
        List<ValidationIssue> issues) {
        if (!allowed.contains(field.getLocation())) {
            issues.add(new ValidationIssue("FIELD_LOCATION_NOT_ALLOWED", location + ".fieldLocation",
                "该方向不允许字段位置 " + field.getLocation().getCode()));
            return;
        }
        if (!validPath(field)) {
            issues.add(new ValidationIssue("FIELD_PATH_INVALID", location + ".fieldPath", pathRule(field.getLocation())));
        }
        if (field.getLocation() != ApiFieldLocation.BODY && !scalar(field.getDataType())) {
            issues.add(new ValidationIssue("FIELD_TYPE_NOT_ALLOWED", location + ".dataType",
                "HEADER、QUERY、PATH 字段只能是标量类型"));
        }
    }

    /** 按位置检查路径语法；BODY 支持点分层级与 [] 数组元素。 */
    boolean validPath(FieldDefinition field) {
        String path = field.getPath();
        return switch (field.getLocation()) {
            case HEADER -> HEADER_NAME.matcher(path).matches();
            case QUERY -> QUERY_NAME.matcher(path).matches();
            case PATH -> PATH_NAME.matcher(path).matches();
            case BODY -> validBodyPath(path);
        };
    }

    /** BODY 路径最多 16 段，每段 1～64 字符，可选 [] 后缀。 */
    boolean validBodyPath(String path) {
        String[] segments = path.split("\\.", -1);
        if (segments.length > MAX_SEGMENTS) {
            return false;
        }
        for (String segment : segments) {
            if (!BODY_SEGMENT.matcher(segment).matches()) {
                return false;
            }
        }
        return true;
    }

    /** 子字段要求父容器已定义：普通父段为 OBJECT，[] 父段对应的容器为 ARRAY。 */
    void checkParents(List<FieldDefinition> fields, String container, List<ValidationIssue> issues) {
        Map<String, ApiFieldDataType> bodyTypes = new HashMap<>();
        fields.stream().filter(field -> field.getLocation() == ApiFieldLocation.BODY)
            .forEach(field -> bodyTypes.putIfAbsent(field.getPath(), field.getDataType()));
        for (int index = 0; index < fields.size(); index++) {
            var field = fields.get(index);
            if (field.getLocation() != ApiFieldLocation.BODY || !validBodyPath(field.getPath())) {
                continue;
            }
            String parent = parentContainer(field.getPath());
            if (parent == null) {
                continue;
            }
            ApiFieldDataType expected = parent.endsWith("[]") ? ApiFieldDataType.ARRAY : ApiFieldDataType.OBJECT;
            String parentPath = parent.endsWith("[]") ? parent.substring(0, parent.length() - 2) : parent;
            if (bodyTypes.get(parentPath) != expected) {
                issues.add(new ValidationIssue("FIELD_PARENT_MISSING", container + "[" + index + "].fieldPath",
                    "父字段 " + parentPath + " 必须先定义为 " + expected.getCode()));
            }
        }
    }

    /** 返回最后一段之前的容器路径；顶层非数组字段返回 null，顶层数组元素返回自身。 */
    String parentContainer(String path) {
        int dot = path.lastIndexOf('.');
        if (dot >= 0) {
            return path.substring(0, dot);
        }
        return path.endsWith("[]") ? path : null;
    }

    /** 同一位置路径唯一；HEADER 名称大小写不敏感。 */
    String identity(FieldDefinition field) {
        String path = field.getLocation() == ApiFieldLocation.HEADER ? field.getPath().toLowerCase(Locale.ROOT)
            : field.getPath();
        return field.getLocation().getCode() + ":" + path;
    }

    /** HEADER、QUERY、PATH 只承载标量值。 */
    boolean scalar(ApiFieldDataType type) {
        return type != ApiFieldDataType.OBJECT && type != ApiFieldDataType.ARRAY;
    }

    /** 各位置的路径规则说明。 */
    String pathRule(ApiFieldLocation location) {
        return switch (location) {
            case HEADER -> "HEADER 名称只能包含 HTTP Token 字符，最多 64 字符";
            case QUERY -> "QUERY 名称只能包含字母、数字及 _.~-，最多 64 字符";
            case PATH -> "PATH 变量名以字母开头，只能包含字母、数字或下划线，最多 64 字符";
            case BODY -> "BODY 路径最多 16 段，每段以字母、_ 或 $ 开头，1～64 字符，数组段以 [] 结尾";
        };
    }
}
