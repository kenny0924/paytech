package biz_test

import (
	"strings"
	"testing"

	"github.com/paytech/vault-service/internal/biz"
)

// 取值规则的公开合同：编译组合、JSON 对象判定、正则整值匹配与样例校验。
//
// 被测入口：biz.CompileValueRule、ValueRule.Check、ValueRule.CheckSamples（纯 Java 风格的公开静态合同）。

const emailPattern = `[^\s@\p{Lu}]{1,64}@[^\s@.\p{Lu}]+(?:\.[^\s@.\p{Lu}]+)+`

func TestCompileValueRule_rejectsInvalidCombinations(t *testing.T) {
	cases := []struct {
		name    string
		format  biz.ValueFormat
		pattern string
		code    biz.ErrorCode
	}{
		{"TEXT 带正则", biz.TextFormat, "a", biz.ValuePatternInvalid},
		{"JSON 带正则", biz.JSONFormat, "a", biz.ValuePatternInvalid},
		{"REGEX 缺正则", biz.RegexFormat, "", biz.ValuePatternInvalid},
		{"正则无法编译", biz.RegexFormat, "(", biz.ValuePatternInvalid},
		{"RE2 不支持回溯引用", biz.RegexFormat, `(a)\1`, biz.ValuePatternInvalid},
		{"正则超过 256 字符", biz.RegexFormat, strings.Repeat("a", 257), biz.ValuePatternInvalid},
		{"未知格式", biz.ValueFormat("XML"), "", biz.InvalidRequest},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			_, err := biz.CompileValueRule(tc.format, tc.pattern)
			assertCode(t, err, tc.code)
		})
	}
	if _, err := biz.CompileValueRule(biz.RegexFormat, strings.Repeat("中", 256)); err != nil {
		t.Fatalf("256 个字符（按字符计）的正则应可保存: %v", err)
	}
}

func TestValueRule_textAcceptsAnything(t *testing.T) {
	rule := mustRule(t, biz.TextFormat, "")
	for _, value := range []string{"", " 任意 ", "{not json"} {
		if err := rule.Check([]byte(value)); err != nil {
			t.Fatalf("TEXT 不应限制 %q: %v", value, err)
		}
	}
}

func TestValueRule_jsonRequiresSingleObjectWithoutDuplicateKeys(t *testing.T) {
	rule := mustRule(t, biz.JSONFormat, "")
	accepted := []string{`{}`, ` {"a":1} `, `{"a":{"b":[1,{"c":null}]},"d":"x"}`, `{"a":{"k":1},"b":{"k":2}}`}
	for _, value := range accepted {
		if err := rule.Check([]byte(value)); err != nil {
			t.Fatalf("合法 JSON 对象被拒绝 %q: %v", value, err)
		}
	}
	rejected := []string{``, `[]`, `null`, `"s"`, `1`, `{`, `{"a":1}x`, `{"a":1} {}`, `{"a":1,"a":2}`, `{"o":{"k":1,"k":2}}`, `{"l":[{"k":1,"k":2}]}`, `{'a':1}`}
	for _, value := range rejected {
		err := rule.Check([]byte(value))
		assertCode(t, err, biz.ValueNotJSONObject)
		if strings.Contains(err.Error(), value) && value != "" {
			t.Fatalf("错误信息不得回显原值: %v", err)
		}
	}
}

func TestValueRule_regexMatchesWholeValue(t *testing.T) {
	email := mustRule(t, biz.RegexFormat, emailPattern)
	for _, value := range []string{"operator@example.test", "a.b+c@sub.example.com", "user@例子.中国", "user@xn--fsqu00a.xn--fiqs8s"} {
		if err := email.Check([]byte(value)); err != nil {
			t.Fatalf("规范邮箱被拒绝 %q: %v", value, err)
		}
	}
	for _, value := range []string{"Foo@x.com", " a@x.com", "a@x.com ", "a@@x.com", "a@x", "a@.x.com", "a@x..com"} {
		assertCode(t, email.Check([]byte(value)), biz.ValuePatternMismatch)
	}
	phone := mustRule(t, biz.RegexFormat, `\+[1-9][0-9]{7,14}`)
	for _, value := range []string{"+1234567", "+1234567890123456", "+0123456789", "13800138000", "x+8613800138000"} {
		assertCode(t, phone.Check([]byte(value)), biz.ValuePatternMismatch)
	}
	// 自动锚定：正则中的分支也必须整值匹配，部分匹配不通过。
	alternation := mustRule(t, biz.RegexFormat, `a|b`)
	assertCode(t, alternation.Check([]byte("ab")), biz.ValuePatternMismatch)
}

func TestValueRule_samples(t *testing.T) {
	rule := mustRule(t, biz.RegexFormat, `[0-9]{4}`)
	assertCode(t, rule.CheckSamples(nil, true), biz.SampleValueInvalid)
	assertCode(t, rule.CheckSamples([]string{"1234", "12a4"}, true), biz.SampleValueInvalid)
	assertCode(t, rule.CheckSamples([]string{"1", "2", "3", "4", "5", "6"}, false), biz.SampleValueInvalid)
	if err := rule.CheckSamples([]string{"1234", "0000"}, true); err != nil {
		t.Fatalf("合格样例被拒绝: %v", err)
	}
	if err := mustRule(t, biz.TextFormat, "").CheckSamples(nil, false); err != nil {
		t.Fatalf("非必填时可以不带样例: %v", err)
	}
}

func TestValueRule_zeroValueFailsClosed(t *testing.T) {
	assertCode(t, biz.ValueRule{}.Check([]byte("any")), biz.InternalError)
}

func mustRule(t *testing.T, format biz.ValueFormat, pattern string) biz.ValueRule {
	t.Helper()
	rule, err := biz.CompileValueRule(format, pattern)
	if err != nil {
		t.Fatalf("compile %s %q: %v", format, pattern, err)
	}
	return rule
}
