package com.paytech.data.domainservice.impl;

import com.paytech.data.domain.whitelist.WhitelistedTable;
import com.paytech.foundation.common.error.ErrorException;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 白名单文件结构 {@code services: [{service, database, tables: [{name, maskedColumns, excludedColumns}]}]} 的解析与校验。
 *
 * <p>
 * 规则：只接受已知键；服务名为小写短横线；库名以 {@code mor_} 开头且一个库只属于一个服务；表名以 {@code t_} 开头且
 * {@code 库.表} 全文件唯一；列名非空、表内不重复，同一列不能既屏蔽又排除。
 *
 * @author Claude
 */
final class WhitelistDocumentParser {

    private static final Pattern SERVICE_NAME = Pattern.compile("^[a-z][a-z0-9-]*$");

    private static final Pattern DATABASE_NAME = Pattern.compile("^mor_[a-z0-9_]+$");

    private static final Pattern TABLE_NAME = Pattern.compile("^t_[a-z0-9_]+$");

    private static final Set<String> ROOT_KEYS = Set.of("services");

    private static final Set<String> SERVICE_KEYS = Set.of("service", "database", "tables");

    private static final Set<String> TABLE_KEYS = Set.of("name", "maskedColumns", "excludedColumns");

    private final String location;

    private final Set<String> qualifiedNames = new HashSet<>();

    private final Map<String, String> databaseOwners = new HashMap<>();

    WhitelistDocumentParser(String location) {
        this.location = location;
    }

    List<WhitelistedTable> parse(InputStream input) {
        Map<String, Object> root = asMap(readYaml(input), "文件根");
        requireKnownKeys(root, ROOT_KEYS, "文件根");
        List<Object> services = asNonEmptyList(root.get("services"), "services");
        List<WhitelistedTable> tables = new ArrayList<>();
        for (int index = 0; index < services.size(); index++) {
            tables.addAll(parseService(asMap(services.get(index), "services[" + index + "]"), "services[" + index + "]"));
        }
        return List.copyOf(tables);
    }

    private Object readYaml(InputStream input) {
        try {
            return new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
        } catch (YAMLException exception) {
            throw new ErrorException("CDC 白名单不是合法 YAML: " + location, exception);
        }
    }

    private List<WhitelistedTable> parseService(Map<String, Object> entry, String path) {
        requireKnownKeys(entry, SERVICE_KEYS, path);
        String service = requireName(entry.get("service"), SERVICE_NAME, path + ".service");
        String database = requireName(entry.get("database"), DATABASE_NAME, path + ".database");
        String previousOwner = databaseOwners.putIfAbsent(database, service);
        if (previousOwner != null && !previousOwner.equals(service)) {
            throw invalid(path + ".database", "库 " + database + " 已属于服务 " + previousOwner + "，不能再登记给 " + service);
        }
        List<Object> tableEntries = asNonEmptyList(entry.get("tables"), path + ".tables");
        List<WhitelistedTable> tables = new ArrayList<>();
        for (int index = 0; index < tableEntries.size(); index++) {
            String tablePath = path + ".tables[" + index + "]";
            tables.add(parseTable(service, database, asMap(tableEntries.get(index), tablePath), tablePath));
        }
        return tables;
    }

    private WhitelistedTable parseTable(String service, String database, Map<String, Object> entry, String path) {
        requireKnownKeys(entry, TABLE_KEYS, path);
        String name = requireName(entry.get("name"), TABLE_NAME, path + ".name");
        if (!qualifiedNames.add(database + "." + name)) {
            throw invalid(path + ".name", "表重复登记: " + database + "." + name);
        }
        List<String> masked = parseColumns(entry.get("maskedColumns"), path + ".maskedColumns");
        List<String> excluded = parseColumns(entry.get("excludedColumns"), path + ".excludedColumns");
        for (String column : masked) {
            if (excluded.contains(column)) {
                throw invalid(path, "列 " + column + " 不能同时屏蔽与排除");
            }
        }
        return new WhitelistedTable(service, database, name, masked, excluded);
    }

    private List<String> parseColumns(Object value, String path) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> items)) {
            throw invalid(path, "必须是列名列表");
        }
        List<String> columns = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof String column) || column.isBlank()) {
                throw invalid(path, "列名不能为空");
            }
            if (columns.contains(column)) {
                throw invalid(path, "列名重复: " + column);
            }
            columns.add(column);
        }
        return columns;
    }

    private String requireName(Object value, Pattern pattern, String path) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalid(path, "不能为空");
        }
        if (!pattern.matcher(text).matches()) {
            throw invalid(path, "取值 " + text + " 不符合 " + pattern.pattern());
        }
        return text;
    }

    private void requireKnownKeys(Map<String, Object> entry, Set<String> allowed, String path) {
        for (String key : entry.keySet()) {
            if (!allowed.contains(key)) {
                throw invalid(path, "未知字段 " + key + "，允许 " + allowed);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value, String path) {
        if (!(value instanceof Map<?, ?> map)) {
            throw invalid(path, "必须是对象");
        }
        for (Object key : map.keySet()) {
            if (!(key instanceof String)) {
                throw invalid(path, "字段名必须是字符串");
            }
        }
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    private List<Object> asNonEmptyList(Object value, String path) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw invalid(path, "必须是非空列表");
        }
        return (List<Object>) list;
    }

    private ErrorException invalid(String path, String detail) {
        return new ErrorException("CDC 白名单非法: " + location + " " + path + " " + detail);
    }
}
