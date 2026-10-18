// Package local 准备本地数据库前置数据（对应 Java mocker/local）。
package local

import (
	"context"
	"crypto/rand"
	"testing"
	"time"

	"github.com/stretchr/testify/require"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/internal/data"
	"github.com/paytech/vault-service/internal/pkg/localaes"
	"github.com/paytech/vault-service/internal/pkg/snowflake"
	"github.com/paytech/vault-service/test/support/base"
)

// mockerWorkerID 是本地 Mocker 的 Snowflake 坐标 31/26，与被测服务的 31/27 区分。
const mockerWorkerID = 26

// RecordMocker 直接插入可被被测 Vault 解密的记录，不经 Protect 接口准备数据。
type RecordMocker struct {
	env    *base.Environment
	store  *data.Store
	cipher *biz.RecordCipherService
}

// NewRecordMocker 使用与被测服务相同的根密钥与密钥引用；连接在用例结束时关闭。
func NewRecordMocker(t *testing.T, env *base.Environment) *RecordMocker {
	t.Helper()
	root := env.RootKey()
	defer clear(root)
	keys, err := localaes.New(root, "auto-test")
	require.NoError(t, err)
	ids, err := snowflake.NewGenerator(31, mockerWorkerID)
	require.NoError(t, err)
	store, err := data.Open(context.Background(), env.DSN(), data.DefaultOptions)
	require.NoError(t, err, "打开测试数据库失败")
	t.Cleanup(func() { _ = store.Close() })
	return &RecordMocker{env: env, store: store, cipher: biz.NewRecordCipherService(keys, ids, rand.Reader, time.Now)}
}

// MockRecord 插入一条已保护的值，返回 Token 与记录主键。
func (m *RecordMocker) MockRecord(t *testing.T, dataType string, mode biz.TokenMode, value string) (string, int64) {
	t.Helper()
	ctx := context.Background()
	in := biz.ProtectInput{DataType: dataType, TokenMode: mode, Value: []byte(value), Purpose: "FIXTURE", IdempotencyKey: m.env.ID.NextIdempotencyKey()}
	material, err := m.cipher.ProtectMaterial(ctx, m.env.ID.Caller, in)
	require.NoError(t, err)
	record, err := m.cipher.SealRecord(ctx, in, material.Fingerprint)
	require.NoError(t, err)
	err = m.store.InTransaction(ctx, func(tx biz.TxStore) error { return tx.InsertRecord(ctx, record) })
	require.NoError(t, err, "插入前置记录失败")
	return record.Token, record.ID
}
