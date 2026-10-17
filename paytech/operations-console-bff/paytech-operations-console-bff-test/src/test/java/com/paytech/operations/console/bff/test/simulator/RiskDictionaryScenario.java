package com.paytech.operations.console.bff.test.simulator;

import com.paytech.risk.api.decision.request.*;
import com.paytech.risk.api.decision.response.*;

import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import com.paytech.foundation.web.paging.PagingResponseBody;

/** 新字典和历史合同的有状态外部 Owner 场景，写入结果由当前版本计算。 */
final class RiskDictionaryScenario {
    private static final String DICTIONARIES = "/decision-dictionaries/v1/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final Map<String, Map<String, Object>> entries = new HashMap<>();
    /** 重置本 Simulator 的当前字典状态及损坏响应开关。 */
    void reset() { entries.clear(); }
    /** 识别本轮新增 Risk 路径，交由独立字典场景处理。 */
    boolean known(String method, String path) {
        return path.matches(DICTIONARIES + "(rejection-reasons|subject-tags)(/[A-Z][A-Z0-9_]{0,63}(/status|/references)?)?")
            && Set.of("GET", "POST", "PUT").contains(method)
            || method.equals("GET") && path.matches("/decision-rule-sets/v1/rule-sets/[1-9][0-9]*/(versions|release-references)");
    }
    /** 模拟字典当前值、版本冲突和历史引用响应，仅用于验证 BFF 外部边界。 */
    Object dispatch(RiskDecisionConfigurationSimulator.Request request) {
        Map<String,String> query=new HashMap<>(); if(request.query()!=null) for(String pair:request.query().split("&")){String[] parts=pair.split("=",2);query.put(parts[0],java.net.URLDecoder.decode(parts[1],java.nio.charset.StandardCharsets.UTF_8));}
        int page=Integer.parseInt(query.getOrDefault("page","1"));int size=Integer.parseInt(query.getOrDefault("size","10"));
        if(request.path().endsWith("/versions")) return new PagingResponseBody<>(List.of(historyItem()),page,size,1);
        if(request.path().endsWith("/release-references")) return releaseReferences(new PagingResponseBody<>(List.of(releaseReference()),page,size,1));
        String[] path=request.path().substring(DICTIONARIES.length()).split("/");boolean reason=path[0].equals("rejection-reasons");String codeKey=reason?"reasonCode":"tagCode";String dictionaryType=reason?"REJECTION_REASON":"SUBJECT_TAG";
        if(request.method().equals("GET")) {
            if(path.length==1){var items=entries.entrySet().stream().filter(e->e.getKey().startsWith(path[0]+":"))
                .map(Map.Entry::getValue).filter(e->!query.containsKey("status")||e.get("status").equals(query.get("status")))
                .sorted(Comparator.comparing(e->(String)e.get(codeKey))).toList();return new PagingResponseBody<>(items.stream().skip((long)(page-1)*size).limit(size).toList(),page,size,items.size());}
            if(path.length==3)return new PagingResponseBody<>(List.of(),page,size,0);
            return Objects.requireNonNull(entries.get(path[0]+":"+path[1]));
        }
        var body=JSON.readTree(request.body());String code=path.length==1?body.path(codeKey).asString():path[1];String key=path[0]+":"+code;
        Map<String,Object> entry=entries.get(key);String actor=request.header("X-Gateway-Actor-Subject");
        long version=entry==null?1:Long.parseLong(entry.get("version").toString())+1;
        if(entry==null){entry=new LinkedHashMap<>();entry.put(codeKey,code);entry.put("status","ACTIVE");entry.put("createdBy",actor);entry.put("createTime",RiskDecisionConfigurationSimulator.TIME);}
        else if(!body.path("expectedVersion").asString().equals(entry.get("version")))throw new IllegalStateException("场景 expectedVersion 不匹配");
        if(path.length==3)entry.put("status",body.path("status").asString());
        else {entry.put("name",body.path("name").asString());entry.put("description",body.path("description").isNull()?null:body.path("description").asString());if(reason)entry.put("defaultMessage",body.path("defaultMessage").asString());}
        entry.put("version",Long.toString(version));entry.put("updatedBy",actor);entry.put("updateTime",RiskDecisionConfigurationSimulator.TIME);entries.put(key,entry);
        return Map.of("dictionaryType",dictionaryType,"code",code,"version",Long.toString(version),"status",entry.get("status"),"changedBy",actor,"changedTime",RiskDecisionConfigurationSimulator.TIME);
    }
    /** 构造规则集唯一历史版本。 */
    private static RuleSetHistoryItemResponseBody historyItem() {
        var item=new RuleSetHistoryItemResponseBody();item.setRuleSetId("90001");item.setRuleSetVersionId("30001");item.setPointCode("CHECKOUT_PREPARE");
        item.setRuleSetCode("DEFAULT_RULES");item.setRuleSetName("规则集");item.setVersionNo(1);item.setStatus("FROZEN");item.setIsCurrent(true);
        item.setCreatedBy("10001");item.setChangeReason("初始版本");item.setCreateTime(RiskDecisionConfigurationSimulator.TIME);return item;
    }
    /** 构造规则集唯一未生效发布引用。 */
    private static RuleSetReleaseReferenceResponseBody releaseReference() {
        var reference=new RuleSetReleaseReferenceResponseBody();reference.setReleaseId("40001");reference.setReleaseNo("REL-1");reference.setEnvironmentCode("TEST");
        reference.setStatus("DRAFT");reference.setPointCode("CHECKOUT_PREPARE");reference.setPointVersionId("20001");reference.setPointVersionNo(1);
        reference.setRuleSetVersionId("30001");reference.setVersionNo(1);reference.setActivatedTime(null);reference.setSupersededTime(null);return reference;
    }
    /** 构造未被活动发布使用的规则集引用分页。 */
    private static RuleSetReleaseReferencesResponseBody releaseReferences(PagingResponseBody<RuleSetReleaseReferenceResponseBody> page) {
        var references=new RuleSetReleaseReferencesResponseBody();references.setRuleSetId("90001");references.setInUse(false);references.setReferences(page);return references;
    }
}
