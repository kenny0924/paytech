package com.paytech.client.contract.domain.onboarding;
import com.paytech.foundation.common.enums.CodeEnum;
/** 短回执引用只保存稳定坐标，满足 response_ref 的 64 字符边界。 */
public enum ClientPeopleOperation implements CodeEnum {
    CREATE_PERSON("CLIENT_PERSON_CREATE","PC","PERSON_CREATED"),

    SAVE_PERSON("CLIENT_PERSON_SAVE","PS","PERSON_SAVED"),

    CREATE_RELATION("CLIENT_RELATION_CREATE","RC","RELATION_CREATED"),

    SAVE_RELATION("CLIENT_RELATION_SAVE","RS","RELATION_SAVED"),

    REMOVE_RELATION("CLIENT_RELATION_REMOVE","RD","RELATION_REMOVED");
    private final String code,reference,result;
    ClientPeopleOperation(String code,String reference,String result) { this.code=code;this.reference=reference;this.result=result; }
    @Override public String getCode() { return code; }
    public String reference() { return reference; }
    public String result() { return result; }
}
