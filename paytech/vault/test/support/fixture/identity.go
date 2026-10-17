// Package fixture 定义一次测试运行独占的业务标识，隔离共享的本机 mor_vault。
//
// 调用方、数据类型、请求 ID 与幂等键都带本次运行的随机前缀；清理只删除带这些前缀的数据，不触碰其他进程的行。
package fixture

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"strings"
	"sync/atomic"
)

const (
	// Purpose 是策略允许的访问用途。
	Purpose = "CHECKOUT_CONTACT"

	// OtherPurpose 是策略未授权的访问用途。
	OtherPurpose = "SUPPORT_REVIEW"
)

// Identity 是本次运行的隔离标识。
type Identity struct {
	// RunID 是本次运行的随机标识（小写十六进制）。
	RunID string

	// Caller 拥有全部 Protect/Reveal 授权。
	Caller string

	// SecondCaller 只拥有 STABLE 数据类型的 Protect 授权，用于跨调用方复用场景。
	SecondCaller string

	// OtherCaller 没有任何授权。
	OtherCaller string

	// StableType 登记为 STABLE 模式的数据类型。
	StableType string

	// ReferenceType 登记为 REFERENCE 模式的数据类型。
	ReferenceType string

	// UnregisteredType 未在策略中登记。
	UnregisteredType string

	// ManualType 是普通敏感级别、开启人工加/解密的数据类型。
	ManualType string

	// SecretType 是密钥级别、开启人工加/解密的数据类型。
	SecretType string

	// AdminCaller 是本次运行的管理调用方（相当于 operations-console-bff）。
	AdminCaller string

	// Operator 是人工操作与策略维护的运营账号 ID。
	Operator string

	sequence atomic.Int64
}

// NewIdentity 生成本次运行的隔离标识。
func NewIdentity() *Identity {
	var raw [4]byte
	if _, err := rand.Read(raw[:]); err != nil {
		panic(err)
	}
	run := hex.EncodeToString(raw[:])
	upper := strings.ToUpper(run)
	return &Identity{
		RunID:            run,
		Caller:           "auto-" + run + "-commerce",
		SecondCaller:     "auto-" + run + "-payment",
		OtherCaller:      "auto-" + run + "-stranger",
		StableType:       "AUTO_" + upper + "_EMAIL",
		ReferenceType:    "AUTO_" + upper + "_NOTE",
		UnregisteredType: "AUTO_" + upper + "_UNKNOWN",
		ManualType:       "AUTO_" + upper + "_PHONE",
		SecretType:       "AUTO_" + upper + "_SECRET",
		AdminCaller:      "auto-" + run + "-ops-bff",
		Operator:         "auto-" + run + "-operator",
	}
}

// Prefix 是本次运行所有调用方、请求 ID 与幂等键的公共前缀。
func (i *Identity) Prefix() string { return "auto-" + i.RunID + "-" }

// NextRequestID 生成本次运行的请求 ID，清理依据之一。
func (i *Identity) NextRequestID() string {
	return fmt.Sprintf("%sreq-%d", i.Prefix(), i.sequence.Add(1))
}

// NextIdempotencyKey 生成本次运行的幂等键。
func (i *Identity) NextIdempotencyKey() string {
	return fmt.Sprintf("%skey-%d", i.Prefix(), i.sequence.Add(1))
}

// TypePrefix 是本次运行全部数据类型编码的前缀（策略维护场景新建的类型也使用它）。
func (i *Identity) TypePrefix() string { return "AUTO_" + strings.ToUpper(i.RunID) + "_" }
