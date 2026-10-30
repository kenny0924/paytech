package com.paytech.platform.support.dataquery.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.api.enums.DataQueryErrorCode;
import com.paytech.platform.support.dataquery.core.configuration.DataQueryJointProperties;
import com.paytech.platform.support.dataquery.core.enums.JointQueryNodeStatus;
import com.paytech.platform.support.dataquery.core.enums.JointQueryTruncationRule;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyRole;
import com.paytech.platform.support.dataquery.core.enums.QueryPagingMode;
import com.paytech.platform.support.dataquery.domain.DataQueryResult;
import com.paytech.platform.support.dataquery.domain.JointQueryGraph;
import com.paytech.platform.support.dataquery.domain.JointQueryKey;
import com.paytech.platform.support.dataquery.domain.JointQueryStep;
import com.paytech.platform.support.dataquery.domain.JointQueryStepResult;
import com.paytech.platform.support.dataquery.domain.JointQueryStepResult.Node;
import com.paytech.platform.support.dataquery.domain.JointQueryTemplate;
import com.paytech.platform.support.dataquery.domain.QueryJointColumn;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeDetail;
import com.paytech.platform.support.dataquery.domain.QueryKeyValues;
import com.paytech.platform.support.dataquery.service.processor.RunDataQueryTemplateProcessor;
import com.paytech.platform.support.dataquery.service.processor.request.RunDataQueryTemplateRequest;

import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 联合查询一步的执行（technique 8.5）：为本层键生成任务、单步上限与预算、有界并发执行、链路值提取与截断。
 * <li>任务 = (模板, 触发键类型)：LIST 键参数一次绑定该键类型的全部值，标量键参数逐值一个任务；同一模板被不同键类型触发时分别执行。</li>
 * <li>已加载对象（technique 8.7）：depth &gt; 0 且未指定 templateCodes 时，“按自身 ID 查”的模板（触发键类型 = 行主体键类型）
 * 剔除已加载的值；全部剔除则不生成任务（不占单步执行数与预算），记入 alreadyLoaded，部分剔除只用剩余值。</li>
 * <li>每个任务经 {@link RunDataQueryTemplateProcessor} 执行模板当前 ACTIVE 版本，行数上限 maxRowsPerTemplate，
 * 超时上限 max(1, min(8, 剩余整秒))；剩余不足 1 秒不再启动，标 SKIPPED。单个任务失败只记在该节点。</li>
 * <li>访问被查库，只能在数据库事务外调用；不写 mor_platform，不登记审计。另记一条步骤元数据日志（不含键值）与指标
 * data.query.joint.step{result}。</li>
 */
@Component
public class JointQueryStepRunner {

    /** 跳过原因：单步预算用尽。 */
    public static final String STEP_BUDGET_EXHAUSTED = "STEP_BUDGET_EXHAUSTED";

    /** 跳过原因：单步执行数用尽。 */
    public static final String STEP_EXECUTION_LIMIT = "STEP_EXECUTION_LIMIT";

    /** 跳过原因：共享执行器队列已满。 */
    public static final String EXECUTOR_BUSY = "EXECUTOR_BUSY";

    /** 失败原因：未归类的系统异常。 */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    /** 日志与指标中的调用方标签。 */
    static final String CALLER_LABEL = "joint-query";

    /** 单次执行超时上限，与 RunDataQueryTemplateRequest.timeoutCapSeconds 的上限一致。 */
    static final int MAX_TIMEOUT_CAP_SECONDS = 8;

    /** 预算结束后等待已启动任务返回的余量。 */
    static final long COLLECT_GRACE_MILLIS = 500;

    private static final Logger log = LoggerFactory.getLogger(JointQueryStepRunner.class);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MeterRegistry meters;
    private final JointQueryExecutor executor;
    private final DataQueryJointProperties properties;
    private final RunDataQueryTemplateProcessor runProcessor;

    /**
     * 构造。
     *
     * @param meters 指标
     * @param executor 联合查询执行器
     * @param properties 护栏
     * @param runProcessor 单模板执行入口
     */
    public JointQueryStepRunner(MeterRegistry meters, JointQueryExecutor executor, DataQueryJointProperties properties,
        RunDataQueryTemplateProcessor runProcessor) {
        this.meters = meters;
        this.executor = executor;
        this.properties = properties;
        this.runProcessor = runProcessor;
    }

    /**
     * 执行一步。
     *
     * @param graph 本次请求现读的图
     * @param step 已校验的输入
     * @return 结果；本层没有参与模板时 nodes 为空
     */
    public JointQueryStepResult run(JointQueryGraph graph, JointQueryStep step) {
        long startedAt = System.nanoTime();
        long deadline = startedAt + properties.getStepBudget().toNanos();
        Plan plan = plan(graph, step);
        List<Task> tasks = plan.tasks();
        int limit = Math.min(tasks.size(), properties.getMaxExecutionsPerStep());
        Node[] nodes = new Node[tasks.size()];
        for (int index = limit; index < tasks.size(); index++) {
            nodes[index] = skipped(tasks.get(index), STEP_EXECUTION_LIMIT, "本步模板执行数已达上限，未执行");
        }
        Map<Integer, Future<Node>> running = submit(tasks.subList(0, limit), step, deadline, nodes);
        collect(tasks, running, deadline, nodes);
        JointQueryStepResult result = assemble(graph, step.getDepth(), List.of(nodes), tasks.size() - limit,
            plan.alreadyLoaded(), elapsedMillis(startedAt));
        observe(step, result);
        return result;
    }

    /** 本层每个键类型找参与模板，剔除已加载对象后生成任务：LIST 一个任务，标量逐值一个任务。 */
    Plan plan(JointQueryGraph graph, JointQueryStep step) {
        List<Task> tasks = new ArrayList<>();
        List<JointQueryStepResult.AlreadyLoaded> alreadyLoaded = new ArrayList<>();
        for (JointQueryKey key : step.getKeys()) {
            for (JointQueryTemplate template : graph.triggeredBy(key.getKeyType())) {
                if (step.getTemplateCodes() != null && !step.getTemplateCodes().contains(template.getTemplateCode())) {
                    continue;
                }
                List<String> values = unloaded(step, template, key, alreadyLoaded);
                if (values.isEmpty()) {
                    continue;
                }
                String nodeId = step.getDepth() + ":" + template.getTemplateCode() + ":" + key.getKeyType();
                if (template.isKeyParameterList()) {
                    tasks.add(new Task(nodeId, template, values));
                    continue;
                }
                for (int index = 0; index < values.size(); index++) {
                    tasks.add(new Task(nodeId + ":" + (index + 1), template, List.of(values.get(index))));
                }
            }
        }
        return new Plan(tasks, List.copyOf(alreadyLoaded));
    }

    /**
     * 一个 (模板, 键) 实际要执行的值：只有 depth &gt; 0、未指定 templateCodes 且模板“按自身 ID 查”时剔除已加载的值，
     * 被剔除的值记入 alreadyLoaded。
     *
     * @return 剩余值，保持输入顺序；可能为空
     */
    static List<String> unloaded(JointQueryStep step, JointQueryTemplate template, JointQueryKey key,
        List<JointQueryStepResult.AlreadyLoaded> alreadyLoaded) {
        Set<String> loaded = step.getLoadedSubjects().getOrDefault(key.getKeyType(), Set.of());
        boolean selfId = key.getKeyType().equals(template.getJoint().getSubjectKeyType());
        if (step.getDepth() == 0 || step.getTemplateCodes() != null || !selfId || loaded.isEmpty()) {
            return key.getValues();
        }
        List<String> skipped = key.getValues().stream().filter(loaded::contains).toList();
        if (!skipped.isEmpty()) {
            alreadyLoaded.add(new JointQueryStepResult.AlreadyLoaded(template, key.getKeyType(), skipped));
        }
        return key.getValues().stream().filter(value -> !loaded.contains(value)).toList();
    }

    /**
     * 在单步并发与预算内依次提交；剩余不足 1 秒、等不到并发名额或执行器已满的任务直接记为 SKIPPED。
     *
     * @return 任务下标到 Future
     */
    Map<Integer, Future<Node>> submit(List<Task> tasks, JointQueryStep step, long deadline, Node[] nodes) {
        Semaphore permits = new Semaphore(properties.getStepConcurrency());
        Map<Integer, Future<Node>> running = new LinkedHashMap<>();
        for (int index = 0; index < tasks.size(); index++) {
            Task task = tasks.get(index);
            if (!acquire(permits, deadline)) {
                nodes[index] = skipped(task, STEP_BUDGET_EXHAUSTED, "本步预算已用尽，未执行");
                continue;
            }
            int timeoutCap = (int) Math.max(1, Math.min(MAX_TIMEOUT_CAP_SECONDS,
                TimeUnit.NANOSECONDS.toSeconds(deadline - System.nanoTime())));
            try {
                running.put(index, executor.submit(() -> execute(task, step, timeoutCap, permits)));
            } catch (RejectedExecutionException failure) {
                permits.release();
                nodes[index] = skipped(task, EXECUTOR_BUSY, "执行器繁忙，未执行，请稍后重试");
            }
        }
        return running;
    }

    /** 在剩余至少 1 秒时取得并发名额；取得后剩余已不足 1 秒则归还；中断时恢复中断状态并放弃。 */
    static boolean acquire(Semaphore permits, long deadline) {
        long oneSecond = TimeUnit.SECONDS.toNanos(1);
        long usable = deadline - System.nanoTime() - oneSecond;
        try {
            if (usable < 0 || !permits.tryAcquire(usable, TimeUnit.NANOSECONDS)) {
                return false;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
        if (deadline - System.nanoTime() >= oneSecond) {
            return true;
        }
        permits.release();
        return false;
    }

    /** 等待已提交任务；预算结束仍未返回的任务取消并记为 DATA_QUERY_TIMEOUT。 */
    static void collect(List<Task> tasks, Map<Integer, Future<Node>> running, long deadline, Node[] nodes) {
        long collectDeadline = deadline + TimeUnit.MILLISECONDS.toNanos(COLLECT_GRACE_MILLIS);
        for (Map.Entry<Integer, Future<Node>> entry : running.entrySet()) {
            Task task = tasks.get(entry.getKey());
            try {
                nodes[entry.getKey()] = entry.getValue().get(Math.max(0, collectDeadline - System.nanoTime()),
                    TimeUnit.NANOSECONDS);
            } catch (TimeoutException timeout) {
                entry.getValue().cancel(true);
                nodes[entry.getKey()] = failed(task, DataQueryErrorCode.DATA_QUERY_TIMEOUT.getCode(),
                    "本步预算内未完成", 0);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                entry.getValue().cancel(true);
                nodes[entry.getKey()] = failed(task, INTERNAL_ERROR, "查询被中断", 0);
            } catch (ExecutionException failure) {
                nodes[entry.getKey()] = failed(task, INTERNAL_ERROR, "查询执行失败，请稍后重试", 0);
            }
        }
    }

    /**
     * 在执行器线程中执行一个任务；任何异常只记在该节点。
     *
     * @return 节点
     */
    Node execute(Task task, JointQueryStep step, int timeoutCap, Semaphore permits) {
        long startedAt = System.nanoTime();
        try {
            DataQueryResult result = runProcessor.process(new RunDataQueryTemplateRequest(
                task.template().getTemplateCode(), params(task), null, null, false, null, step.getOperatorAccountId(),
                timeoutCap, CALLER_LABEL, properties.getMaxRowsPerTemplate()));
            return succeeded(task, result, elapsedMillis(startedAt));
        } catch (FailException failure) {
            return failed(task, failure.getCode(), failure.getMessage(), elapsedMillis(startedAt));
        } catch (ErrorException failure) {
            log.warn("联合查询节点执行失败 template={} node={}", task.template().getTemplateCode(), task.nodeId(), failure);
            boolean coded = failure.getErrorCode() != null;
            return failed(task, coded ? failure.getErrorCode().getCode() : INTERNAL_ERROR,
                coded ? failure.getErrorCode().getMessage() : "查询执行失败，请稍后重试", elapsedMillis(startedAt));
        } catch (RuntimeException failure) {
            log.warn("联合查询节点执行异常 template={} node={}", task.template().getTemplateCode(), task.nodeId(), failure);
            return failed(task, INTERNAL_ERROR, "查询执行失败，请稍后重试", elapsedMillis(startedAt));
        } finally {
            permits.release();
        }
    }

    /** 键参数：LIST 传数组，标量传单值；键值一律为字符串（64 位 ID 不经过浮点）。 */
    static ObjectNode params(Task task) {
        ObjectNode params = JSON.createObjectNode();
        String name = task.template().getJoint().getKeyParameter().getName();
        if (task.template().isKeyParameterList()) {
            ArrayNode values = params.putArray(name);
            task.values().forEach(values::add);
        } else {
            params.put(name, task.values().getFirst());
        }
        return params;
    }

    /** 成功节点：结果列附标注，缺失的链路列记提示。 */
    static Node succeeded(Task task, DataQueryResult result, long elapsedMillis) {
        List<JointQueryStepResult.Column> columns = result.getColumns().stream().map(column -> {
            QueryJointColumn joint = task.template().getJoint().column(column.getName());
            return new JointQueryStepResult.Column(column.getName(), column.getLabel(),
                joint == null ? null : joint.getKeyType(), joint == null ? null : joint.getKeyRole());
        }).toList();
        List<String> warnings = task.template().getJoint().getColumns().stream()
            .filter(joint -> joint.getKeyRole() == QueryKeyRole.LINK)
            .filter(joint -> columns.stream().noneMatch(column -> column.getName().equalsIgnoreCase(joint.getName())))
            .map(joint -> "LINK_COLUMN_MISSING:" + joint.getName()).toList();
        boolean cursor = task.template().getPagingMode() == QueryPagingMode.CURSOR && result.isHasMore();
        return node(task).versionNo(result.getVersionNo()).status(JointQueryNodeStatus.SUCCEEDED)
            .truncated(result.isHasMore()).nextCursor(cursor ? result.getNextCursor() : null)
            .elapsedMillis(elapsedMillis).warnings(warnings).columns(columns).rows(result.getRows()).build();
    }

    /** 失败节点。 */
    static Node failed(Task task, String errorCode, String errorMessage, long elapsedMillis) {
        return node(task).status(JointQueryNodeStatus.FAILED).errorCode(errorCode).errorMessage(errorMessage)
            .elapsedMillis(elapsedMillis).build();
    }

    /** 跳过节点。 */
    static Node skipped(Task task, String errorCode, String errorMessage) {
        return node(task).status(JointQueryNodeStatus.SKIPPED).errorCode(errorCode).errorMessage(errorMessage)
            .elapsedMillis(0).build();
    }

    /** 节点公共部分；结果字段默认为空。 */
    static Node.NodeBuilder node(Task task) {
        return Node.builder().nodeId(task.nodeId()).template(task.template())
            .versionNo(task.template().getVersionNo()).triggerValues(task.values()).warnings(List.of())
            .columns(List.of()).rows(List.of());
    }

    /** 提取链路值、按键类型去重并截断，汇总跳过数。 */
    JointQueryStepResult assemble(JointQueryGraph graph, int depth, List<Node> nodes, int limitSkipped,
        List<JointQueryStepResult.AlreadyLoaded> alreadyLoaded, long elapsedMillis) {
        List<JointQueryStepResult.Link> links = new ArrayList<>();
        nodes.stream().filter(node -> node.getStatus() == JointQueryNodeStatus.SUCCEEDED)
            .forEach(node -> links.addAll(links(graph, node)));
        Map<String, Set<String>> discovered = new LinkedHashMap<>();
        links.forEach(link -> discovered.computeIfAbsent(link.getKeyType(), key -> new LinkedHashSet<>())
            .add(link.getValue()));
        List<JointQueryStepResult.Truncation> truncations = new ArrayList<>();
        List<JointQueryKey> keys = new ArrayList<>();
        discovered.forEach((keyType, values) -> {
            List<String> kept = values.stream().limit(properties.getMaxValuesPerKey()).toList();
            keys.add(new JointQueryKey(keyType, kept));
            if (values.size() > kept.size()) {
                truncations.add(new JointQueryStepResult.Truncation(JointQueryTruncationRule.MAX_VALUES_PER_KEY,
                    keyType, values.size() - kept.size()));
            }
        });
        if (limitSkipped > 0) {
            truncations.add(new JointQueryStepResult.Truncation(JointQueryTruncationRule.STEP_EXECUTION_LIMIT, null,
                limitSkipped));
        }
        int budgetSkipped = (int) nodes.stream().filter(node -> STEP_BUDGET_EXHAUSTED.equals(node.getErrorCode()))
            .count();
        if (budgetSkipped > 0) {
            truncations.add(new JointQueryStepResult.Truncation(JointQueryTruncationRule.STEP_BUDGET, null,
                budgetSkipped));
        }
        return new JointQueryStepResult(depth, nodes, List.copyOf(links), List.copyOf(keys), List.copyOf(truncations),
            alreadyLoaded, elapsedMillis);
    }

    /** 一个成功节点中 LINK 列的非空规范值；值不符合键类型（如 VARCHAR 列中的非数字）时跳过。 */
    static List<JointQueryStepResult.Link> links(JointQueryGraph graph, Node node) {
        List<JointQueryStepResult.Link> links = new ArrayList<>();
        for (int column = 0; column < node.getColumns().size(); column++) {
            JointQueryStepResult.Column header = node.getColumns().get(column);
            QueryKeyTypeDetail keyType = header.getKeyType() == null ? null : graph.keyType(header.getKeyType());
            if (header.getKeyRole() != QueryKeyRole.LINK || keyType == null || !keyType.active()) {
                continue;
            }
            for (int row = 0; row < node.getRows().size(); row++) {
                Object value = node.getRows().get(row).get(column);
                String normalized = value == null ? null
                    : QueryKeyValues.normalize(keyType.getValueType(), String.valueOf(value));
                if (normalized != null) {
                    links.add(new JointQueryStepResult.Link(node.getNodeId(), row, header.getName(),
                        keyType.getKeyTypeCode(), normalized));
                }
            }
        }
        return links;
    }

    /** 一条步骤元数据日志与指标；不含键值。 */
    void observe(JointQueryStep step, JointQueryStepResult result) {
        long failed = result.getNodes().stream().filter(node -> node.getStatus() == JointQueryNodeStatus.FAILED)
            .count();
        long skipped = result.getNodes().stream().filter(node -> node.getStatus() == JointQueryNodeStatus.SKIPPED)
            .count();
        String outcome = failed + skipped == 0 ? "SUCCEEDED" : "PARTIAL";
        log.info("联合查询单步 depth={} keyTypes={} tasks={} failed={} skipped={} alreadyLoaded={} result={} elapsedMs={}",
            step.getDepth(), step.getKeys().size(), result.getNodes().size(), failed, skipped,
            result.getAlreadyLoaded().size(), outcome, result.getElapsedMillis());
        meters.counter("data.query.joint.step", "result", outcome).increment();
    }

    /** 自某时刻起的毫秒数。 */
    static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    /**
     * 一个模板执行任务。
     *
     * @param nodeId 节点 ID
     * @param template 模板
     * @param values 绑定到键参数的值
     */
    record Task(String nodeId, JointQueryTemplate template, List<String> values) {
    }

    /**
     * 一步的执行计划。
     *
     * @param tasks 要执行的任务，按生成顺序
     * @param alreadyLoaded 因已加载而剔除的视图
     */
    record Plan(List<Task> tasks, List<JointQueryStepResult.AlreadyLoaded> alreadyLoaded) {
    }
}
