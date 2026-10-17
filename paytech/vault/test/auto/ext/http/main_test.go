package http_test

import (
	"os"
	"testing"

	"github.com/paytech/vault-service/test/support/base"
)

// TestMain 按正式装配启动一次 Vault，供本包全部用例共享；用例数据逐例准备与清理。
func TestMain(m *testing.M) { os.Exit(base.Run(m)) }
