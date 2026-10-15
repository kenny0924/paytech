package v1

import (
	"encoding/json"
	"time"
)

// DataTypeBody 是数据类型目录条目。
type DataTypeBody struct {
	DataTypeCode         string    `json:"dataTypeCode"`
	TokenMode            string    `json:"tokenMode"`
	DataClass            string    `json:"dataClass"`
	Description          string    `json:"description"`
	ManualProtectEnabled bool      `json:"manualProtectEnabled"`
	ManualRevealEnabled  bool      `json:"manualRevealEnabled"`
	Status               string    `json:"status"`
	Version              int       `json:"version"`
	UpdateTime           time.Time `json:"updateTime"`
}

// DataTypeListResponseBody 是数据类型列表与当前策略版本。
type DataTypeListResponseBody struct {
	CurrentPolicyVersion int            `json:"currentPolicyVersion"`
	Items                []DataTypeBody `json:"items"`
}

// CreateDataTypeRequestBody 新建数据类型；新建后状态为 ACTIVE。
type CreateDataTypeRequestBody struct {
	DataTypeCode         *string `json:"dataTypeCode" binding:"required,vault_code"`
	TokenMode            *string `json:"tokenMode" binding:"required,oneof=REFERENCE STABLE"`
	DataClass            *string `json:"dataClass" binding:"required,oneof=STANDARD SECRET"`
	Description          *string `json:"description" binding:"required,min=1,max=256"`
	ManualProtectEnabled *bool   `json:"manualProtectEnabled" binding:"required"`
	ManualRevealEnabled  *bool   `json:"manualRevealEnabled" binding:"required"`
	Reason               *string `json:"reason" binding:"required,min=1,max=500"`
}

// UpdateDataTypeRequestBody 修改数据类型；编码与 Token 模式不可修改。
type UpdateDataTypeRequestBody struct {
	ExpectedVersion      *int    `json:"expectedVersion" binding:"required,min=1"`
	DataClass            *string `json:"dataClass" binding:"required,oneof=STANDARD SECRET"`
	Description          *string `json:"description" binding:"required,min=1,max=256"`
	ManualProtectEnabled *bool   `json:"manualProtectEnabled" binding:"required"`
	ManualRevealEnabled  *bool   `json:"manualRevealEnabled" binding:"required"`
	Status               *string `json:"status" binding:"required,oneof=ACTIVE DISABLED"`
	Reason               *string `json:"reason" binding:"required,min=1,max=500"`
}

// DataTypeResponseBody 是维护后的数据类型与新策略版本。
type DataTypeResponseBody struct {
	DataType      DataTypeBody `json:"dataType"`
	PolicyVersion int          `json:"policyVersion"`
}

// AccessRuleBody 是服务访问规则；ID 为 64 位整数，以字符串传输。
type AccessRuleBody struct {
	AccessRuleID  string    `json:"accessRuleId"`
	CallerService string    `json:"callerService"`
	Action        string    `json:"action"`
	DataTypeCode  string    `json:"dataTypeCode"`
	PurposeCode   string    `json:"purposeCode"`
	Status        string    `json:"status"`
	Version       int       `json:"version"`
	UpdateTime    time.Time `json:"updateTime"`
}

// AccessRulePageResponseBody 是访问规则分页结果。
type AccessRulePageResponseBody struct {
	Items []AccessRuleBody `json:"items"`
	Page  int              `json:"page"`
	Size  int              `json:"size"`
	Total int64            `json:"total"`
}

// CreateAccessRuleRequestBody 新建服务访问规则。
type CreateAccessRuleRequestBody struct {
	CallerService *string `json:"callerService" binding:"required,vault_caller"`
	Action        *string `json:"action" binding:"required,oneof=PROTECT REVEAL"`
	DataTypeCode  *string `json:"dataTypeCode" binding:"required,vault_code"`
	PurposeCode   *string `json:"purposeCode" binding:"required,vault_code"`
	Reason        *string `json:"reason" binding:"required,min=1,max=500"`
}

// UpdateAccessRuleRequestBody 启用或停用访问规则。
type UpdateAccessRuleRequestBody struct {
	ExpectedVersion *int    `json:"expectedVersion" binding:"required,min=1"`
	Status          *string `json:"status" binding:"required,oneof=ACTIVE DISABLED"`
	Reason          *string `json:"reason" binding:"required,min=1,max=500"`
}

// AccessRuleResponseBody 是维护后的规则与新策略版本。
type AccessRuleResponseBody struct {
	AccessRule    AccessRuleBody `json:"accessRule"`
	PolicyVersion int            `json:"policyVersion"`
}

// PolicyChangeBody 是策略变更记录；详情时包含前后快照。
type PolicyChangeBody struct {
	PolicyVersionNo int             `json:"policyVersionNo"`
	ChangeType      string          `json:"changeType"`
	TargetType      string          `json:"targetType"`
	TargetKey       string          `json:"targetKey"`
	ActorID         string          `json:"actorId"`
	Reason          string          `json:"reason"`
	CreateTime      time.Time       `json:"createTime"`
	BeforeSnapshot  json.RawMessage `json:"beforeSnapshot,omitempty"`
	AfterSnapshot   json.RawMessage `json:"afterSnapshot,omitempty"`
}

// PolicyChangePageResponseBody 是变更记录分页结果。
type PolicyChangePageResponseBody struct {
	Items []PolicyChangeBody `json:"items"`
	Page  int                `json:"page"`
	Size  int                `json:"size"`
	Total int64              `json:"total"`
}

// ManualProtectRequestBody 是人工加密请求；允许的数据级别由 BFF 按路由填写。
type ManualProtectRequestBody struct {
	DataTypeCode       *string  `json:"dataTypeCode" binding:"required,vault_code"`
	Value              *string  `json:"value" binding:"required"`
	ReasonCode         *string  `json:"reasonCode" binding:"required,vault_code"`
	TicketRef          *string  `json:"ticketRef" binding:"required,vault_ticket"`
	AllowedDataClasses []string `json:"allowedDataClasses" binding:"required,min=1,max=2,dive,oneof=STANDARD SECRET"`
}

// ManualRevealRequestBody 是人工解密请求。
type ManualRevealRequestBody struct {
	Token              *string  `json:"token" binding:"required,vault_token"`
	ReasonCode         *string  `json:"reasonCode" binding:"required,vault_code"`
	TicketRef          *string  `json:"ticketRef" binding:"required,vault_ticket"`
	AllowedDataClasses []string `json:"allowedDataClasses" binding:"required,min=1,max=2,dive,oneof=STANDARD SECRET"`
}

// ManualRevealResponseBody 是人工解密结果。
type ManualRevealResponseBody struct {
	DataTypeCode string `json:"dataTypeCode"`
	Value        string `json:"value"`
}

// ManualEventBody 是人工操作审计；不含 Token 与原值。
type ManualEventBody struct {
	RequestID    string    `json:"requestId"`
	ActorID      string    `json:"actorId"`
	Action       string    `json:"action"`
	DataTypeCode string    `json:"dataTypeCode"`
	ReasonCode   string    `json:"reasonCode"`
	TicketRef    string    `json:"ticketRef"`
	Outcome      string    `json:"outcome"`
	ResultCode   string    `json:"resultCode"`
	CreateTime   time.Time `json:"createTime"`
}

// ManualEventPageResponseBody 是人工操作审计分页结果。
type ManualEventPageResponseBody struct {
	Items []ManualEventBody `json:"items"`
	Page  int               `json:"page"`
	Size  int               `json:"size"`
	Total int64             `json:"total"`
}
