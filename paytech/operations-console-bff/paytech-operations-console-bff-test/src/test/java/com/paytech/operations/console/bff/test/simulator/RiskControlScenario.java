package com.paytech.operations.console.bff.test.simulator;

import java.util.*;

import tools.jackson.databind.json.JsonMapper;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator.Request;
import com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator.SemanticFailure;
import com.paytech.risk.decision.api.control.request.AddRiskListEntryRequestBody;
import com.paytech.risk.decision.api.control.request.CreateRiskListRequestBody;
import com.paytech.risk.decision.api.control.response.RiskListEntryResponseBody;
import com.paytech.risk.decision.api.control.response.RiskListResponseBody;
import com.paytech.risk.decision.api.request.CreateExecutionReleaseRequestBody;
import com.paytech.risk.decision.api.request.StartShadowRunRequestBody;
import com.paytech.risk.decision.api.response.ExecutionReleaseListItemResponseBody;
import com.paytech.risk.decision.api.response.ExecutionReleaseResponseBody;
import com.paytech.risk.decision.api.response.ShadowRun;
import com.paytech.risk.decision.api.response.StaticCheck;

/** 名单与执行发布的外部 Risk 场景：响应由请求和场景内状态派生，名单成员只回传脱敏键。 */
final class RiskControlScenario {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String LISTS = "/risk-lists/v1/lists";
    private static final String RELEASES = "/decision-releases/v1/executions";
    private static final String TIME = RiskDecisionConfigurationSimulator.TIME;
    private final Map<String, RiskListResponseBody> lists = new TreeMap<>();
    private final List<Entry> entries = new ArrayList<>();
    private final List<ExecutionReleaseResponseBody> releases = new ArrayList<>();
    private final Map<String, String> releaseCreators = new HashMap<>();

    /** 主体原值只保存在场景内存，用于精确查询；响应永不包含。 */
    private record Entry(String listCode, String subjectValue, RiskListEntryResponseBody wire) { }

    void reset() { lists.clear(); entries.clear(); releases.clear(); releaseCreators.clear(); }

    boolean known(String method, String path) {
        return Set.of("GET", "POST").contains(method) && path.equals(LISTS)
            || Set.of("GET", "POST").contains(method) && path.matches(LISTS + "/[A-Z][A-Z0-9_]{0,63}/entries")
            || method.equals("POST") && path.matches(LISTS + "/[A-Z][A-Z0-9_]{0,63}/entries/[1-9][0-9]*/deactivate")
            || Set.of("GET", "POST").contains(method) && path.equals(RELEASES)
            || method.equals("GET") && path.matches(RELEASES + "/[1-9][0-9]*")
            || method.equals("POST") && path.matches(RELEASES + "/[1-9][0-9]*/(validate|shadow-runs|activate)");
    }

    Object dispatch(Request request) {
        var q = RiskDecisionConfigurationSimulator.query(request.query());
        int page = Integer.parseInt(q.getOrDefault("page", "1")), size = Integer.parseInt(q.getOrDefault("size", "10"));
        String path = request.path(), actor = request.header("X-Gateway-Actor-Subject");
        if (path.startsWith(LISTS)) return lists(request, path, q, page, size, actor);
        return releases(request, path, q, page, size, actor);
    }

    private Object lists(Request request, String path, Map<String, String> q, int page, int size, String actor) {
        if (path.equals(LISTS) && request.method().equals("POST")) {
            var body = JSON.readValue(request.body(), CreateRiskListRequestBody.class);
            if (lists.containsKey(body.getListCode())) throw new SemanticFailure(400, "BUSINESS_CHECK_FAILED");
            var list = new RiskListResponseBody();
            list.setListId(Integer.toString(70001 + lists.size())); list.setListCode(body.getListCode()); list.setListName(body.getListName());
            list.setSubjectType(body.getSubjectType()); list.setPurpose(body.getPurpose()); list.setActive(true);
            lists.put(list.getListCode(), list);
            return list;
        }
        if (path.equals(LISTS)) return page(new ArrayList<>(lists.values()), page, size);
        String code = path.split("/")[4];
        var list = lists.get(code);
        if (list == null) throw new SemanticFailure(400, "BUSINESS_CHECK_FAILED");
        if (path.endsWith("/deactivate")) {
            String entryId = path.split("/")[6];
            var entry = entries.stream().filter(e -> e.wire().getEntryId().equals(entryId) && e.listCode().equals(code)
                && e.wire().getStatus().equals("ACTIVE")).findFirst().orElseThrow(() -> new SemanticFailure(400, "BUSINESS_CHECK_FAILED"));
            entry.wire().setStatus("RELEASED"); entry.wire().setDeactivatedBy(actor);
            entry.wire().setDeactivatedReason(JSON.readTree(request.body()).path("reason").asString());
            return entry.wire();
        }
        if (request.method().equals("POST")) {
            var body = JSON.readValue(request.body(), AddRiskListEntryRequestBody.class);
            if (entries.stream().anyMatch(e -> e.listCode().equals(code) && e.subjectValue().equals(body.getSubjectValue())
                && e.wire().getStatus().equals("ACTIVE"))) throw new SemanticFailure(409, "CONCURRENT_REQUEST");
            var wire = new RiskListEntryResponseBody();
            wire.setEntryId(Integer.toString(80001 + entries.size())); wire.setListId(list.getListId()); wire.setSubjectType(list.getSubjectType());
            wire.setMaskedSubjectKey(body.getSubjectValue().substring(0, Math.min(6, body.getSubjectValue().length())) + "***");
            wire.setSourceType("MANUAL"); wire.setStatus("ACTIVE");
            wire.setEffectiveFrom(body.getEffectiveFrom() == null ? TIME : body.getEffectiveFrom()); wire.setEffectiveTo(body.getEffectiveTo());
            wire.setReason(body.getReason()); wire.setCreatedBy(actor); wire.setCreateTime(TIME);
            entries.add(new Entry(code, body.getSubjectValue(), wire));
            return wire;
        }
        var selected = entries.reversed().stream().filter(e -> e.listCode().equals(code))
            .filter(e -> !q.containsKey("subjectValue") || e.subjectValue().equals(q.get("subjectValue")))
            .filter(e -> !q.containsKey("status") || e.wire().getStatus().equals(q.get("status")))
            .map(Entry::wire).toList();
        return page(selected, page, size);
    }

    private Object releases(Request request, String path, Map<String, String> q, int page, int size, String actor) {
        if (path.equals(RELEASES) && request.method().equals("POST")) {
            var body = JSON.readValue(request.body(), CreateExecutionReleaseRequestBody.class);
            if (!body.getPointCode().equals("PAYMENT_ATTEMPT_RISK")) throw new SemanticFailure(400, "BUSINESS_CHECK_FAILED");
            var release = new ExecutionReleaseResponseBody();
            release.setReleaseId(Integer.toString(90001 + releases.size())); release.setReleaseNo(Integer.toString(releases.size() + 1));
            release.setPointCode(body.getPointCode()); release.setFlowVersionId(body.getFlowVersionId()); release.setEnvironment("TEST");
            release.setStatus("DRAFT"); release.setVersion(1); release.setContentHash(RiskDecisionConfigurationSimulator.HASH);
            release.setChecks(List.of());
            releases.add(release); releaseCreators.put(release.getReleaseId(), actor);
            return release;
        }
        if (path.equals(RELEASES)) {
            var items = releases.reversed().stream().filter(r -> r.getPointCode().equals(q.get("pointCode")))
                .filter(r -> !q.containsKey("status") || r.getStatus().equals(q.get("status"))).map(this::item).toList();
            return page(items, page, size);
        }
        String id = path.split("/")[4];
        var release = releases.stream().filter(r -> r.getReleaseId().equals(id)).findFirst()
            .orElseThrow(() -> new SemanticFailure(404, "NOT_FOUND"));
        if (path.endsWith("/validate")) {
            if (release.getStatus().equals("DRAFT")) {
                release.setStatus("SHADOWING"); release.setVersion(release.getVersion() + 1);
                release.setChecks(RiskDecisionConfigurationSimulator.CHECKS.stream().map(code -> check(code)).toList());
            }
            return release;
        }
        var active = releases.stream().filter(r -> r.getStatus().equals("ACTIVE")).findFirst();
        if (path.endsWith("/shadow-runs")) {
            if (active.isEmpty() || !release.getStatus().equals("SHADOWING")) throw new SemanticFailure(400, "BUSINESS_CHECK_FAILED");
            var body = JSON.readValue(request.body(), StartShadowRunRequestBody.class);
            var run = new ShadowRun();
            run.setShadowRunId("95001"); run.setBaselineReleaseId(active.get().getReleaseId()); run.setStatus("COMPLETED");
            run.setSampleTarget(body.getSampleTarget()); run.setSampleCount(body.getSampleTarget());
            run.setAllowToReviewCount(4); run.setAllowToDenyCount(2); run.setDenyToAllowCount(1); run.setActionChangeCount(7);
            run.setFeatureMissingCount(0); run.setFailedCount(0); run.setTimedOutCount(0); run.setP95Millis(38); run.setP99Millis(71);
            run.setCheckStatus("PASSED");
            release.setShadowRun(run);
            return release;
        }
        if (path.endsWith("/activate")) {
            boolean shadowPassed = release.getShadowRun() != null && "PASSED".equals(release.getShadowRun().getCheckStatus());
            if (!release.getStatus().equals("SHADOWING") || active.isPresent() && !shadowPassed) throw new SemanticFailure(400, "BUSINESS_CHECK_FAILED");
            active.ifPresent(previous -> { previous.setStatus("SUPERSEDED"); previous.setSupersededTime(TIME); });
            release.setStatus("ACTIVE"); release.setActivatedTime(TIME); release.setVersion(release.getVersion() + 1);
            return release;
        }
        return release;
    }

    private ExecutionReleaseListItemResponseBody item(ExecutionReleaseResponseBody r) {
        var item = new ExecutionReleaseListItemResponseBody();
        item.setReleaseId(r.getReleaseId()); item.setReleaseNo(r.getReleaseNo()); item.setPointCode(r.getPointCode());
        item.setEnvironment(r.getEnvironment()); item.setStatus(r.getStatus()); item.setActive(r.getStatus().equals("ACTIVE"));
        item.setFlowId("50001"); item.setFlowCode("PAY_MAIN"); item.setFlowVersionId(r.getFlowVersionId()); item.setFlowVersionNo(1);
        item.setContentHash(r.getContentHash()); item.setCreatedBy(releaseCreators.get(r.getReleaseId())); item.setCreateTime(TIME);
        item.setActivatedBy(r.getActivatedTime() == null ? null : releaseCreators.get(r.getReleaseId()));
        item.setActivatedTime(r.getActivatedTime()); item.setSupersededTime(r.getSupersededTime());
        return item;
    }

    private static StaticCheck check(String code) {
        var check = new StaticCheck(); check.setCheckCode(code); check.setCheckStatus("PASSED");
        return check;
    }

    private static <T> PagingResponseBody<T> page(List<T> all, int page, int size) {
        return new PagingResponseBody<>(all.stream().skip((long) (page - 1) * size).limit(size).toList(), page, size, all.size());
    }
}
