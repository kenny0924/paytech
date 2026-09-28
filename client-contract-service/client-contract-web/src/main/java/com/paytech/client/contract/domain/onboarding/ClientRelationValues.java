package com.paytech.client.contract.domain.onboarding;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.*;
import com.paytech.client.contract.core.dal.dataobject.ClientPartyRelation;
import com.paytech.client.contract.domain.enums.PartyRelationType;
import com.paytech.foundation.common.error.FailException;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.INVALID_DRAFT;
/** 校验草稿关系的闭合类型、数值和期间；不推断任何司法辖区的 UBO 门槛。 */
public record ClientRelationValues(PartyRelationType relationType,BigDecimal ownershipPercentage,
    Instant effectiveFrom,Instant effectiveTo) {
    private static final Set<String> FIELDS=Set.of("relationType","ownershipPercentage","effectiveFrom","effectiveTo");
    public static ClientRelationValues create(RelationFields fields) {
        if (fields==null) throw invalid();
        var values=new LinkedHashMap<String,String>();
        values.put("relationType",fields.relationType());values.put("ownershipPercentage",fields.ownershipPercentage());
        values.put("effectiveFrom",fields.effectiveFrom());values.put("effectiveTo",fields.effectiveTo());
        return complete(normalize(values));
    }
    public static Map<String,String> patch(RelationPatchRequestBody request) {
        if (request==null || request.dirtyFields()==null || request.values()==null || request.dirtyFields().isEmpty()
            || request.dirtyFields().size()>4 || new HashSet<>(request.dirtyFields()).size()!=request.dirtyFields().size()
            || !request.values().keySet().equals(new HashSet<>(request.dirtyFields()))) throw invalid();
        return normalize(request.values());
    }
    public static ClientRelationValues merge(ClientPartyRelation current,Map<String,String> patch) {
        var values=new TreeMap<String,String>();
        values.put("relationType",current.relationType().getCode());
        values.put("ownershipPercentage",decimal(current.ownershipPercentage()));
        values.put("effectiveFrom",date(current.effectiveFrom()));values.put("effectiveTo",date(current.effectiveTo()));
        values.putAll(patch);return complete(values);
    }
    public Map<String,String> normalized() {
        var values=new TreeMap<String,String>();values.put("relationType",relationType.getCode());
        values.put("ownershipPercentage",decimal(ownershipPercentage));values.put("effectiveFrom",date(effectiveFrom));
        values.put("effectiveTo",date(effectiveTo));return Collections.unmodifiableMap(values);
    }
    private static Map<String,String> normalize(Map<String,String> input) {
        if (!FIELDS.containsAll(input.keySet())) throw invalid();
        var result=new TreeMap<String,String>();
        input.forEach((field,raw) -> {
            String value=raw==null?null:raw.strip();if (value!=null && value.isEmpty()) value=null;
            try {
                if (value==null) { if (field.equals("relationType") || field.equals("effectiveFrom")) throw invalid(); }
                else if (field.equals("relationType")) value=PartyRelationType.valueOf(value).getCode();
                else if (field.equals("ownershipPercentage")) {
                    if (!value.matches("[0-9]{1,3}(\\.[0-9]{1,10})?")) throw invalid();
                    BigDecimal amount=new BigDecimal(value);
                    if (amount.signum()<=0 || amount.compareTo(new BigDecimal("100"))>0) throw invalid();
                    value=decimal(amount);
                } else if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || LocalDate.parse(value).getYear()<1000) throw invalid();
            } catch (IllegalArgumentException | DateTimeException invalid) { throw invalid(); }
            result.put(field,value);
        });
        return Collections.unmodifiableMap(result);
    }
    private static ClientRelationValues complete(Map<String,String> values) {
        if (values.get("relationType")==null || values.get("effectiveFrom")==null) throw invalid();
        var type=PartyRelationType.valueOf(values.get("relationType"));
        var percentage=values.get("ownershipPercentage")==null?null:new BigDecimal(values.get("ownershipPercentage"));
        if (percentage!=null && type!=PartyRelationType.OWNERSHIP && type!=PartyRelationType.UBO) throw invalid();
        var from=instant(values.get("effectiveFrom"));var to=instant(values.get("effectiveTo"));
        if (to!=null && !to.isAfter(from)) throw invalid();
        return new ClientRelationValues(type,percentage,from,to);
    }
    public static String date(Instant value) { return value==null?null:value.atOffset(ZoneOffset.UTC).toLocalDate().toString(); }
    public static String decimal(BigDecimal value) { return value==null?null:value.stripTrailingZeros().toPlainString(); }
    private static Instant instant(String value) { return value==null?null:LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC); }
    private static FailException invalid() { return new FailException(INVALID_DRAFT); }
}
