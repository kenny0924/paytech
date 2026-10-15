package com.paytech.client.contract.domain.onboarding;
import com.paytech.foundation.common.enums.CodeEnum;
/** 人员与关系写操作及其回执结果。 */
public enum ClientPeopleOperation implements CodeEnum {
    CREATE_PERSON("CLIENT_PERSON_CREATE","PERSON_CREATED"),

    SAVE_PERSON("CLIENT_PERSON_SAVE","PERSON_SAVED"),

    CREATE_RELATION("CLIENT_RELATION_CREATE","RELATION_CREATED"),

    SAVE_RELATION("CLIENT_RELATION_SAVE","RELATION_SAVED"),

    REMOVE_RELATION("CLIENT_RELATION_REMOVE","RELATION_REMOVED");
    private final String code,result;
    ClientPeopleOperation(String code,String result) { this.code=code;this.result=result; }
    @Override public String getCode() { return code; }
    public String result() { return result; }
}
