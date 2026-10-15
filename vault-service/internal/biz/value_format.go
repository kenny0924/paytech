package biz

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"regexp"
	"unicode/utf8"
)

// ValueFormat 是数据类型接收的值格式：TEXT 不限、JSON 须为 JSON 对象、REGEX 按正则整值匹配。
type ValueFormat string

const (
	TextFormat ValueFormat = "TEXT"

	JSONFormat ValueFormat = "JSON"

	RegexFormat ValueFormat = "REGEX"
)

const (
	maxPatternRunes = 256
	maxSampleValues = 5
)

// ValueRule 是编译好的取值规则，供 Protect 在计算 HMAC 前校验原值。
//
// 规则只校验、不规范化：不符合即拒绝，原值保持调用方提交的字节。
type ValueRule struct {
	format  ValueFormat
	pattern *regexp.Regexp
}

// CompileValueRule 按格式与正则编译规则；组合不合法或正则无法编译返回 VALUE_PATTERN_INVALID。
//
// 正则按 RE2 语法编译并包成 ^(?:…)$ 整值匹配；RE2 匹配时间与输入长度线性相关，不会回溯爆炸。
func CompileValueRule(format ValueFormat, pattern string) (ValueRule, error) {
	switch format {
	case TextFormat, JSONFormat:
		if pattern != "" {
			return ValueRule{}, NewError(ValuePatternInvalid)
		}
		return ValueRule{format: format}, nil
	case RegexFormat:
		if pattern == "" || !utf8.ValidString(pattern) || utf8.RuneCountInString(pattern) > maxPatternRunes {
			return ValueRule{}, NewError(ValuePatternInvalid)
		}
		compiled, err := regexp.Compile(`^(?:` + pattern + `)$`)
		if err != nil {
			return ValueRule{}, NewError(ValuePatternInvalid)
		}
		return ValueRule{format: format, pattern: compiled}, nil
	default:
		return ValueRule{}, NewError(InvalidRequest)
	}
}

// Check 校验原值；失败只返回固定错误码，不携带原值或解析器的报错原文。
func (r ValueRule) Check(value []byte) error {
	switch r.format {
	case TextFormat:
		return nil
	case JSONFormat:
		if !isJSONObject(value) {
			return NewError(ValueNotJSONObject)
		}
		return nil
	case RegexFormat:
		if !r.pattern.Match(value) {
			return NewError(ValuePatternMismatch)
		}
		return nil
	default:
		// 零值规则表示快照中该类型的正则无法编译：失败关闭。
		return NewError(InternalError)
	}
}

// CheckSamples 用测试样例验证规则；required 时至少一个样例。样例只在内存中使用，不落库、不进快照。
func (r ValueRule) CheckSamples(samples []string, required bool) error {
	if len(samples) > maxSampleValues || (required && len(samples) == 0) {
		return NewError(SampleValueInvalid)
	}
	for _, sample := range samples {
		if r.Check([]byte(sample)) != nil {
			return NewError(SampleValueInvalid)
		}
	}
	return nil
}

// isJSONObject 判断值是否为单个 JSON 对象：根为对象、任意层级无重复键、末尾无多余内容。
func isJSONObject(value []byte) bool {
	decoder := json.NewDecoder(bytes.NewReader(value))
	decoder.UseNumber()
	first, err := decoder.Token()
	if err != nil || first != json.Delim('{') {
		return false
	}
	if readObject(decoder) != nil {
		return false
	}
	_, err = decoder.Token()
	return errors.Is(err, io.EOF)
}

// errDuplicateKey 表示同一对象内出现重复键。
var errDuplicateKey = errors.New("JSON对象存在重复键")

// readObject 读取已消费左括号之后的对象成员，检查重复键并递归校验嵌套值。
func readObject(decoder *json.Decoder) error {
	keys := map[string]struct{}{}
	for decoder.More() {
		token, err := decoder.Token()
		if err != nil {
			return err
		}
		key, _ := token.(string)
		if _, seen := keys[key]; seen {
			return errDuplicateKey
		}
		keys[key] = struct{}{}
		if err := readValue(decoder); err != nil {
			return err
		}
	}
	_, err := decoder.Token()
	return err
}

// readValue 读取一个 JSON 值；对象与数组递归检查。
func readValue(decoder *json.Decoder) error {
	token, err := decoder.Token()
	if err != nil {
		return err
	}
	switch token {
	case json.Delim('{'):
		return readObject(decoder)
	case json.Delim('['):
		for decoder.More() {
			if err := readValue(decoder); err != nil {
				return err
			}
		}
		_, err := decoder.Token()
		return err
	default:
		return nil
	}
}
