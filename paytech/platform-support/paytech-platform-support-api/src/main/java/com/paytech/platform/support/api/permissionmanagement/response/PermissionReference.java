package com.paytech.platform.support.api.permissionmanagement.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** 引用种类由请求 kind 决定，每页为同一种类型，不增加 JSON 类型字段。 */
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({@JsonSubTypes.Type(ResourceReference.class), @JsonSubTypes.Type(RouteReference.class),
    @JsonSubTypes.Type(RoleReference.class)})
public sealed interface PermissionReference permits ResourceReference, RouteReference, RoleReference {}
