package com.paytech.platform.support.test.base;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.platform.support.api.dataquery.request.JointQueryKeyValues;
import com.paytech.platform.support.api.dataquery.request.JointQueryLoadedSubject;
import com.paytech.platform.support.api.dataquery.request.JointQueryStepRequestBody;
import com.paytech.platform.support.api.dataquery.response.JointQueryNode;
import com.paytech.platform.support.api.dataquery.response.JointQueryNodeColumn;
import com.paytech.platform.support.api.dataquery.response.JointQueryStepResponseBody;
import com.paytech.platform.support.dataquery.core.enums.JointQueryNodeStatus;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;

/**
 * 联合查询入口测试的公共调用：链路表就绪检查、设置 / 预览 / 单步三条执行路径，以及按页面方式逐层驱动的循环。
 * <li>页面循环只在测试侧模拟（visited、层数上限），服务端每次只执行一层。</li>
 */
public abstract class JointQueryApiTestBase extends DataQueryApiTestBase {

    /** 联合查询接口前缀。 */
    protected static final String JOINT = EXECUTION + "/joint-query";

    /** 页面层数上限，与配置 data-query.joint.max-depth 相同。 */
    protected static final int MAX_DEPTH = 6;

    /** 链路表就绪检查；数据源与模板由各测试类按需插入。 */
    @BeforeEach
    protected final void setUpJointQueryMocks() {
        fixture.assertJointReady();
    }

    /** 以给定调用方调用联合查询接口。 */
    protected PlatformResponseChecker joint(String caller, Operator operator, HttpMethod method, String path,
        Object body) {
        HttpHeaders headers = headers(operator);
        if (caller != null) {
            headers.set("X-Caller-Service", caller);
        }
        return call(method, JOINT + path, headers, body);
    }

    /** 以登记的调用方执行一层。 */
    protected PlatformResponseChecker step(Operator operator, JointQueryStepRequestBody body) {
        return joint(CALLER, operator, HttpMethod.POST, "/steps", body);
    }

    /** 以登记的调用方执行一层并要求成功。 */
    protected JointQueryStepResponseBody stepOk(Operator operator, JointQueryStepRequestBody body) {
        return step(operator, body).assertOk().as(JointQueryStepResponseBody.class);
    }

    /** 单键类型的一层请求。 */
    protected static JointQueryStepRequestBody stepBody(int depth, String keyType, String... values) {
        JointQueryStepRequestBody body = new JointQueryStepRequestBody();
        body.setDepth(depth);
        body.setKeys(new ArrayList<>(List.of(key(keyType, values))));
        return body;
    }

    /** 一个键类型及其值。 */
    protected static JointQueryKeyValues key(String keyType, String... values) {
        JointQueryKeyValues key = new JointQueryKeyValues();
        key.setKeyType(keyType);
        key.setValues(new ArrayList<>(Arrays.asList(values)));
        return key;
    }

    /**
     * 按页面方式从入口逐层执行：每层扣除已访问的 (键类型, 值) 后把剩余发现键作为下一层输入，没有新键或到达层数上限时停止。
     *
     * @return 每层响应
     */
    protected List<JointQueryStepResponseBody> runChain(Operator operator, String keyType, String... values) {
        Map<String, Set<String>> visited = new HashMap<>();
        visited.put(keyType, new HashSet<>(List.of(values)));
        List<JointQueryKeyValues> keys = List.of(key(keyType, values));
        List<JointQueryStepResponseBody> steps = new ArrayList<>();
        for (int depth = 0; depth < MAX_DEPTH && !keys.isEmpty(); depth++) {
            JointQueryStepRequestBody body = new JointQueryStepRequestBody();
            body.setDepth(depth);
            body.setKeys(keys);
            JointQueryStepResponseBody step = stepOk(operator, body);
            steps.add(step);
            keys = unvisited(step.getDiscoveredKeys(), visited);
        }
        return steps;
    }

    /**
     * 同 {@link #runChain}，并按页面方式维护已加载对象（technique 8.7）：每层把已加载对象作为 loadedSubjects 发送，
     * 再从本层 SUCCEEDED 节点中键类型等于该节点行主体键类型的列收集非空值。
     *
     * @return 每层响应
     */
    protected List<JointQueryStepResponseBody> runChainSkippingLoaded(Operator operator, String keyType,
        String... values) {
        Map<String, Set<String>> visited = new HashMap<>();
        visited.put(keyType, new HashSet<>(List.of(values)));
        Map<String, Set<String>> loaded = new LinkedHashMap<>();
        List<JointQueryKeyValues> keys = List.of(key(keyType, values));
        List<JointQueryStepResponseBody> steps = new ArrayList<>();
        for (int depth = 0; depth < MAX_DEPTH && !keys.isEmpty(); depth++) {
            JointQueryStepRequestBody body = new JointQueryStepRequestBody();
            body.setDepth(depth);
            body.setKeys(keys);
            body.setLoadedSubjects(loaded.entrySet().stream()
                .map(entry -> loadedSubject(entry.getKey(), entry.getValue().toArray(String[]::new))).toList());
            JointQueryStepResponseBody step = stepOk(operator, body);
            steps.add(step);
            step.getNodes().forEach(node -> collectLoaded(node, loaded));
            keys = unvisited(step.getDiscoveredKeys(), visited);
        }
        return steps;
    }

    /** 一个已加载对象项。 */
    protected static JointQueryLoadedSubject loadedSubject(String keyType, String... values) {
        JointQueryLoadedSubject subject = new JointQueryLoadedSubject();
        subject.setKeyType(keyType);
        subject.setValues(new ArrayList<>(Arrays.asList(values)));
        return subject;
    }

    /** 成功节点中行主体键类型列的非空值记为已加载。 */
    static void collectLoaded(JointQueryNode node, Map<String, Set<String>> loaded) {
        if (!JointQueryNodeStatus.SUCCEEDED.getCode().equals(node.getStatus())) {
            return;
        }
        for (int column = 0; column < node.getColumns().size(); column++) {
            JointQueryNodeColumn header = node.getColumns().get(column);
            if (!node.getSubjectKeyType().equals(header.getKeyType())) {
                continue;
            }
            for (List<Object> row : node.getRows()) {
                if (row.get(column) != null) {
                    loaded.computeIfAbsent(header.getKeyType(), type -> new LinkedHashSet<>())
                        .add(String.valueOf(row.get(column)));
                }
            }
        }
    }

    /** 发现键扣除已访问值，并把剩余值记为已访问。 */
    static List<JointQueryKeyValues> unvisited(List<JointQueryKeyValues> discovered, Map<String, Set<String>> visited) {
        List<JointQueryKeyValues> next = new ArrayList<>();
        for (JointQueryKeyValues key : discovered) {
            Set<String> seen = visited.computeIfAbsent(key.getKeyType(), type -> new HashSet<>());
            List<String> fresh = key.getValues().stream().filter(seen::add).toList();
            if (!fresh.isEmpty()) {
                next.add(key(key.getKeyType(), fresh.toArray(String[]::new)));
            }
        }
        return next;
    }

    /** 所有层的节点。 */
    protected static List<JointQueryNode> nodes(List<JointQueryStepResponseBody> steps) {
        return steps.stream().flatMap(step -> step.getNodes().stream()).toList();
    }

    /** 按模板编码找节点，要求恰好一个。 */
    protected static JointQueryNode node(List<JointQueryNode> nodes, String templateCode) {
        List<JointQueryNode> matched = nodes.stream().filter(node -> node.getTemplateCode().equals(templateCode))
            .toList();
        if (matched.size() != 1) {
            throw new AssertionError("模板 " + templateCode + " 应恰好执行一次，实际 " + matched.size());
        }
        return matched.getFirst();
    }

    /** 某一层发现的键类型的值。 */
    protected static List<String> discovered(JointQueryStepResponseBody step, String keyType) {
        return step.getDiscoveredKeys().stream().filter(key -> key.getKeyType().equals(keyType)).findFirst()
            .map(JointQueryKeyValues::getValues).orElse(List.of());
    }
}
