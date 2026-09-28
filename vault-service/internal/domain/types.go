package domain

import "time"

type Action string

const (
	ProtectAction Action = "PROTECT"
	RevealAction  Action = "REVEAL"
)

type TokenMode string

const (
	Reference TokenMode = "REFERENCE"
	Stable    TokenMode = "STABLE"
)

type Outcome string

const (
	Success Outcome = "SUCCESS"
	Denied  Outcome = "DENIED"
	Failed  Outcome = "FAILED"
)

type ProtectInput struct {
	DataType       string
	TokenMode      TokenMode
	Value          []byte
	Purpose        string
	IdempotencyKey string
}

type ProtectResult struct {
	Token string
}

type RevealInput struct {
	Token   string
	Purpose string
}

type RevealResult struct {
	Value []byte
}

type Record struct {
	ID               int64
	Token            string
	DataType         string
	TokenMode        TokenMode
	ValueFingerprint *[32]byte
	Ciphertext       []byte
	EncryptionNonce  [12]byte
	WrappedDEK       []byte
	KMSKeyRef        string
	CreateTime       time.Time
	UpdateTime       time.Time
}

type ProtectRequest struct {
	ID             int64
	Caller         string
	IdempotencyKey string
	RequestHash    [32]byte
	RecordID       int64
	CreateTime     time.Time
	UpdateTime     time.Time
}

type AccessEvent struct {
	ID            int64
	RequestID     string
	TraceID       *string
	Caller        string
	Action        Action
	DataType      *string
	Purpose       *string
	RecordID      *int64
	Outcome       Outcome
	ResultCode    string
	PolicyVersion *string
	CreateTime    time.Time
}

type AuditProjection struct {
	RequestID     string
	TraceID       *string
	Caller        string
	Action        Action
	DataType      *string
	Purpose       *string
	RecordID      *int64
	Outcome       Outcome
	ResultCode    string
	PolicyVersion *string
}
