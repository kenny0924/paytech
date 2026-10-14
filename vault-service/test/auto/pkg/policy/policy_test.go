package policy_test

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/internal/pkg/policy"
)

func TestLoadRejectsCaseAliasedFields(t *testing.T) {
	const canonical = `{"policyVersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"REFERENCE"}},"rules":[{"callerService":"test-caller","action":"REVEAL","dataType":"EMAIL","purposes":["TEST"]}]}`
	for _, field := range []struct{ name, member string }{
		{"policyVersion", `"policyVersion":"v1"`},
		{"dataTypes", `"dataTypes":{"EMAIL":{"tokenMode":"REFERENCE"}}`},
		{"rules", `"rules":[{"callerService":"test-caller","action":"REVEAL","dataType":"EMAIL","purposes":["TEST"]}]`},
		{"tokenMode", `"tokenMode":"REFERENCE"`},
		{"callerService", `"callerService":"test-caller"`},
		{"action", `"action":"REVEAL"`},
		{"dataType", `"dataType":"EMAIL"`},
		{"purposes", `"purposes":["TEST"]`},
	} {
		alias := strings.Replace(field.member, field.name, strings.ToUpper(field.name[:1])+field.name[1:], 1)
		for name, replacement := range map[string]string{
			"alias-only": alias, "alias-last": field.member + "," + alias, "alias-first": alias + "," + field.member,
		} {
			t.Run(field.name+"/"+name, func(t *testing.T) {
				_, err := policy.Load(strings.NewReader(strings.Replace(canonical, field.member, replacement, 1)))
				if err == nil || biz.CodeOf(err) != biz.InternalError {
					t.Fatalf("aliased field accepted: %v", err)
				}
			})
		}
	}
}

func TestLoadRejectsAliasOverwritesAndEscapedDuplicates(t *testing.T) {
	for name, document := range map[string]string{
		"root-overwrite":         `{"policyVersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"REFERENCE"}},"rules":[],"Rules":[{"callerService":"test-caller","action":"REVEAL","dataType":"EMAIL","purposes":["TEST"]}]}`,
		"version-overwrite":      `{"policyVersion":"!","PolicyVersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"REFERENCE"}},"rules":[]}`,
		"mode-overwrite":         `{"policyVersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"INVALID","TokenMode":"REFERENCE"}},"rules":[]}`,
		"mode-alias-overwrite":   `{"policyVersion":"v1","dataTypes":{"EMAIL":{"TokenMode":"INVALID","tokenMode":"REFERENCE"}},"rules":[]}`,
		"action-overwrite":       `{"policyVersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"REFERENCE"}},"rules":[{"callerService":"test-caller","action":"DELETE","Action":"REVEAL","dataType":"EMAIL","purposes":["TEST"]}]}`,
		"action-alias-overwrite": `{"policyVersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"REFERENCE"}},"rules":[{"callerService":"test-caller","Action":"DELETE","action":"REVEAL","dataType":"EMAIL","purposes":["TEST"]}]}`,
		"escaped-root":           `{"policyVersion":"v1","policy\u0056ersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"REFERENCE"}},"rules":[]}`,
		"escaped-type":           `{"policyVersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"REFERENCE"},"\u0045MAIL":{"tokenMode":"STABLE"}},"rules":[]}`,
		"escaped-mode":           `{"policyVersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"STABLE","token\u004dode":"REFERENCE"}},"rules":[]}`,
		"escaped-rule":           `{"policyVersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"REFERENCE"}},"rules":[{"callerService":"test-caller","action":"PROTECT","\u0061ction":"REVEAL","dataType":"EMAIL","purposes":["TEST"]}]}`,
	} {
		t.Run(name, func(t *testing.T) {
			if _, err := policy.Load(strings.NewReader(document)); err == nil || biz.CodeOf(err) != biz.InternalError {
				t.Fatalf("ambiguous policy accepted: %v", err)
			}
		})
	}
}

func TestLoadAuthorizesOnlyExactDeclaredDecision(t *testing.T) {
	contents, err := os.ReadFile(filepath.Join("..", "..", "..", "..", "testdata", "policy", "valid.json"))
	if err != nil {
		t.Fatal(err)
	}
	loaded, err := policy.Load(bytes.NewReader(contents))
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}
	if got, ok := loaded.DataTypeMode("EMAIL_ADDRESS_V1"); !ok || got != biz.Stable {
		t.Fatalf("DataTypeMode() = %q, %t", got, ok)
	}
	if _, ok := loaded.DataTypeMode("email_address_v1"); ok {
		t.Fatal("DataTypeMode() normalized a dynamic Data Type key")
	}
	if !loaded.Authorize("test-caller", biz.ProtectAction, "EMAIL_ADDRESS_V1", "CHECKOUT_CONTACT") {
		t.Fatal("Authorize() denied declared decision")
	}
	if loaded.Authorize("test-caller", biz.RevealAction, "EMAIL_ADDRESS_V1", "CHECKOUT_CONTACT") ||
		loaded.Authorize("other-service", biz.ProtectAction, "EMAIL_ADDRESS_V1", "CHECKOUT_CONTACT") ||
		loaded.Authorize("test-caller", biz.ProtectAction, "EMAIL_ADDRESS_V1", "OTHER_PURPOSE") {
		t.Fatal("Authorize() did not default deny unmatched decisions")
	}
	if loaded.Version() != "test-v1" {
		t.Fatalf("Version() = %q", loaded.Version())
	}
}

func TestLoadRejectsMalformedOrAmbiguousPolicy(t *testing.T) {
	cases := []struct {
		name string
		json string
	}{
		{"missing rules", `{"policyVersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"REFERENCE"}}}`},
		{"null rules", `{"policyVersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"REFERENCE"}},"rules":null}`},
		{"duplicate top-level field", `{"policyVersion":"v1","policyVersion":"v2","dataTypes":{},"rules":[]}`},
		{"duplicate data type", `{"policyVersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"REFERENCE"},"EMAIL":{"tokenMode":"STABLE"}},"rules":[]}`},
		{"unknown field", `{"policyVersion":"v1","dataTypes":{},"rules":[],"extra":true}`},
		{"trailing JSON", `{"policyVersion":"v1","dataTypes":{},"rules":[]} {}`},
		{"empty data types", `{"policyVersion":"v1","dataTypes":{},"rules":[]}`},
		{"unknown token mode", `{"policyVersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"OTHER"}},"rules":[]}`},
		{"unknown action", `{"policyVersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"REFERENCE"}},"rules":[{"callerService":"commerce-service","action":"DELETE","dataType":"EMAIL","purposes":["CONTACT"]}]}`},
		{"empty purpose", `{"policyVersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"REFERENCE"}},"rules":[{"callerService":"commerce-service","action":"PROTECT","dataType":"EMAIL","purposes":[""]}]}`},
		{"unregistered data type", `{"policyVersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"REFERENCE"}},"rules":[{"callerService":"commerce-service","action":"PROTECT","dataType":"PHONE","purposes":["CONTACT"]}]}`},
		{"duplicate purpose", `{"policyVersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"REFERENCE"}},"rules":[{"callerService":"commerce-service","action":"PROTECT","dataType":"EMAIL","purposes":["CONTACT","CONTACT"]}]}`},
		{"duplicate decision", `{"policyVersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"REFERENCE"}},"rules":[{"callerService":"commerce-service","action":"PROTECT","dataType":"EMAIL","purposes":["CONTACT"]},{"callerService":"commerce-service","action":"PROTECT","dataType":"EMAIL","purposes":["CONTACT"]}]}`},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if _, err := policy.Load(bytes.NewBufferString(tc.json)); err == nil || biz.CodeOf(err) != biz.InternalError {
				t.Fatalf("Load() error code = %s, error = %v", biz.CodeOf(err), err)
			}
		})
	}
}

func TestLoadRejectsInvalidUTF8(t *testing.T) {
	invalid := append([]byte(`{"policyVersion":"v1","dataTypes":{},"rules":[]}`), 0xff)
	if _, err := policy.Load(bytes.NewReader(invalid)); err == nil || biz.CodeOf(err) != biz.InternalError {
		t.Fatalf("Load() error code = %s, error = %v", biz.CodeOf(err), err)
	}
}

func TestLoadAllowsExplicitEmptyRules(t *testing.T) {
	loaded, err := policy.Load(bytes.NewBufferString(`{"policyVersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"REFERENCE"}},"rules":[]}`))
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}
	if loaded.Authorize("test-caller", biz.ProtectAction, "EMAIL", "CONTACT") {
		t.Fatal("Authorize() allowed an explicit deny-all policy")
	}
}

func TestLoadCompilesEachDeclaredPurpose(t *testing.T) {
	const document = `{"policyVersion":"v1","dataTypes":{"EMAIL":{"tokenMode":"REFERENCE"}},"rules":[{"callerService":"test-caller","action":"PROTECT","dataType":"EMAIL","purposes":["CONTACT","SUPPORT"]}]}`
	loaded, err := policy.Load(strings.NewReader(document))
	if err != nil {
		t.Fatal("Load() rejected distinct purposes in one rule")
	}
	for _, purpose := range []string{"CONTACT", "SUPPORT"} {
		if !loaded.Authorize("test-caller", biz.ProtectAction, "EMAIL", purpose) {
			t.Fatal("Authorize() denied a declared purpose")
		}
	}
	if loaded.Authorize("test-caller", biz.ProtectAction, "EMAIL", "OTHER") {
		t.Fatal("Authorize() allowed an undeclared purpose")
	}
}
