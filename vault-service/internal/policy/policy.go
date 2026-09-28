package policy

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"regexp"
	"unicode/utf8"

	"github.com/paytech/vault-service/internal/domain"
)

const maxPolicyBytes = 1 << 20

var (
	callerPattern  = regexp.MustCompile(`^[a-z][a-z0-9-]{0,127}$`)
	codePattern    = regexp.MustCompile(`^[A-Z][A-Z0-9_]{0,63}$`)
	versionPattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`)
)

type Policy struct {
	version   string
	dataTypes map[string]domain.TokenMode
	decisions map[decisionKey]struct{}
}

type decisionKey struct {
	Caller   string
	Action   domain.Action
	DataType string
	Purpose  string
}

type policyDocument struct {
	PolicyVersion string                    `json:"policyVersion"`
	DataTypes     map[string]dataTypePolicy `json:"dataTypes"`
	Rules         []rule                    `json:"rules"`
}

type dataTypePolicy struct {
	TokenMode domain.TokenMode `json:"tokenMode"`
}

type rule struct {
	CallerService string        `json:"callerService"`
	Action        domain.Action `json:"action"`
	DataType      string        `json:"dataType"`
	Purposes      []string      `json:"purposes"`
}

func Load(reader io.Reader) (*Policy, error) {
	if reader == nil {
		return nil, loadError()
	}
	raw, err := io.ReadAll(io.LimitReader(reader, maxPolicyBytes+1))
	if err != nil || len(raw) > maxPolicyBytes || !utf8.Valid(raw) || validatePolicyKeys(raw) != nil {
		return nil, loadError()
	}
	var document policyDocument
	decoder := json.NewDecoder(bytes.NewReader(raw))
	decoder.DisallowUnknownFields()
	if decoder.Decode(&document) != nil || decoder.Decode(&struct{}{}) != io.EOF {
		return nil, loadError()
	}
	return compile(document)
}

func (p *Policy) DataTypeMode(dataType string) (domain.TokenMode, bool) {
	if p == nil {
		return "", false
	}
	mode, ok := p.dataTypes[dataType]
	return mode, ok
}

func (p *Policy) Authorize(caller string, action domain.Action, dataType, purpose string) bool {
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
		dataTypes: make(map[string]domain.TokenMode, len(document.DataTypes)),
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

func validatePolicyKeys(raw []byte) error {
	decoder := json.NewDecoder(bytes.NewReader(raw))
	if err := consumeObject(decoder); err != nil {
		return err
	}
	if _, err := decoder.Token(); err != io.EOF {
		return fmt.Errorf("trailing JSON")
	}
	return nil
}

func consumeObject(decoder *json.Decoder) error {
	token, err := decoder.Token()
	if err != nil || token != json.Delim('{') {
		return fmt.Errorf("expected object")
	}
	return consumeObjectAfterOpen(decoder, "policy")
}

func consumeValue(decoder *json.Decoder, shape string) error {
	token, err := decoder.Token()
	if err != nil {
		return err
	}
	switch token {
	case json.Delim('{'):
		return consumeObjectAfterOpen(decoder, shape)
	case json.Delim('['):
		for decoder.More() {
			if err := consumeValue(decoder, shape); err != nil {
				return err
			}
		}
		_, err := decoder.Token()
		return err
	default:
		return nil
	}
}

func consumeObjectAfterOpen(decoder *json.Decoder, shape string) error {
	keys := make(map[string]struct{})
	for decoder.More() {
		key, err := decoder.Token()
		if err != nil {
			return err
		}
		name, ok := key.(string)
		if !ok {
			return fmt.Errorf("invalid key")
		}
		if _, duplicate := keys[name]; duplicate {
			return fmt.Errorf("duplicate key")
		}
		keys[name] = struct{}{}
		childShape, allowed := policyFieldShape(shape, name)
		if !allowed {
			return fmt.Errorf("unknown policy field")
		}
		if err := consumeValue(decoder, childShape); err != nil {
			return err
		}
	}
	_, err := decoder.Token()
	return err
}

// 固定结构字段区分大小写，只有 Data Type 名称允许作为动态键。
func policyFieldShape(shape, name string) (string, bool) {
	switch shape {
	case "policy":
		switch name {
		case "policyVersion":
			return "", true
		case "dataTypes":
			return "dataTypes", true
		case "rules":
			return "rule", true
		}
	case "dataTypes":
		return "dataTypePolicy", true
	case "dataTypePolicy":
		return "", name == "tokenMode"
	case "rule":
		return "", name == "callerService" || name == "action" || name == "dataType" || name == "purposes"
	}
	return "", false
}

func validAction(action domain.Action) bool {
	return action == domain.ProtectAction || action == domain.RevealAction
}

func validTokenMode(mode domain.TokenMode) bool {
	return mode == domain.Reference || mode == domain.Stable
}

func loadError() error { return domain.NewError(domain.InternalError) }
