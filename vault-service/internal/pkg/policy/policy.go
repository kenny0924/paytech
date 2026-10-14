package policy

import (
	"encoding/json/v2"
	"io"
	"regexp"

	"github.com/paytech/vault-service/internal/biz"
)

const maxPolicyBytes = 1 << 20

var (
	callerPattern  = regexp.MustCompile(`^[a-z][a-z0-9-]{0,127}$`)
	codePattern    = regexp.MustCompile(`^[A-Z][A-Z0-9_]{0,63}$`)
	versionPattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`)
)

type Policy struct {
	version   string
	dataTypes map[string]biz.TokenMode
	decisions map[decisionKey]struct{}
}

type decisionKey struct {
	Caller   string
	Action   biz.Action
	DataType string
	Purpose  string
}

type policyDocument struct {
	PolicyVersion string                    `json:"policyVersion"`
	DataTypes     map[string]dataTypePolicy `json:"dataTypes"`
	Rules         []rule                    `json:"rules"`
}

type dataTypePolicy struct {
	TokenMode biz.TokenMode `json:"tokenMode"`
}

type rule struct {
	CallerService string     `json:"callerService"`
	Action        biz.Action `json:"action"`
	DataType      string     `json:"dataType"`
	Purposes      []string   `json:"purposes"`
}

func Load(reader io.Reader) (*Policy, error) {
	if reader == nil {
		return nil, loadError()
	}
	raw, err := io.ReadAll(io.LimitReader(reader, maxPolicyBytes+1))
	if err != nil || len(raw) > maxPolicyBytes {
		return nil, loadError()
	}
	// encoding/json/v2 默认区分大小写、拒绝重复键（含 dataTypes 动态键）、非法 UTF-8 与尾随内容；
	// RejectUnknownMembers 拒绝固定结构中的未知字段。
	var document policyDocument
	if json.Unmarshal(raw, &document, json.RejectUnknownMembers(true)) != nil {
		return nil, loadError()
	}
	return compile(document)
}

func (p *Policy) DataTypeMode(dataType string) (biz.TokenMode, bool) {
	if p == nil {
		return "", false
	}
	mode, ok := p.dataTypes[dataType]
	return mode, ok
}

func (p *Policy) Authorize(caller string, action biz.Action, dataType, purpose string) bool {
	if p == nil {
		return false
	}
	_, ok := p.decisions[decisionKey{caller, action, dataType, purpose}]
	return ok
}

func (p *Policy) Version() string {
	if p == nil {
		return ""
	}
	return p.version
}

func compile(document policyDocument) (*Policy, error) {
	if !versionPattern.MatchString(document.PolicyVersion) || len(document.DataTypes) == 0 || document.Rules == nil {
		return nil, loadError()
	}
	compiled := &Policy{
		version:   document.PolicyVersion,
		dataTypes: make(map[string]biz.TokenMode, len(document.DataTypes)),
		decisions: make(map[decisionKey]struct{}),
	}
	if err := compiled.compileDataTypes(document.DataTypes); err != nil {
		return nil, err
	}
	for _, rule := range document.Rules {
		if err := compiled.addRuleDecisions(rule); err != nil {
			return nil, err
		}
	}
	return compiled, nil
}

func (p *Policy) compileDataTypes(definitions map[string]dataTypePolicy) error {
	for dataType, definition := range definitions {
		if !codePattern.MatchString(dataType) || !validTokenMode(definition.TokenMode) {
			return loadError()
		}
		p.dataTypes[dataType] = definition.TokenMode
	}
	return nil
}

func (p *Policy) addRuleDecisions(rule rule) error {
	if !callerPattern.MatchString(rule.CallerService) || !validAction(rule.Action) || !codePattern.MatchString(rule.DataType) || len(rule.Purposes) == 0 {
		return loadError()
	}
	if _, exists := p.dataTypes[rule.DataType]; !exists {
		return loadError()
	}
	for _, purpose := range rule.Purposes {
		if !codePattern.MatchString(purpose) {
			return loadError()
		}
		key := decisionKey{rule.CallerService, rule.Action, rule.DataType, purpose}
		if _, duplicate := p.decisions[key]; duplicate {
			return loadError()
		}
		p.decisions[key] = struct{}{}
	}
	return nil
}

func validAction(action biz.Action) bool {
	return action == biz.ProtectAction || action == biz.RevealAction
}

func validTokenMode(mode biz.TokenMode) bool {
	return mode == biz.Reference || mode == biz.Stable
}

func loadError() error { return biz.NewError(biz.InternalError) }
