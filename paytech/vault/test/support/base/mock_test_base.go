package base

import (
	"context"
	"testing"
	"time"
)

// Setup 是每个用例的全局准备：确认环境可复用且本次运行数据为空，并登记全局清理。
//
// 全局清理最先登记、因此最后执行（t.Cleanup 后进先出），在类级清理之后；断言失败时同样执行。
func Setup(t *testing.T) *Environment {
	t.Helper()
	if current == nil {
		t.Fatal("测试环境未启动：测试包必须在 TestMain 中调用 base.Run")
	}
	return current.Setup(t)
}

// Setup 是指定环境的全局准备，语义同包级 Setup；用于 Start 启动的多个环境。
func (e *Environment) Setup(t *testing.T) *Environment {
	t.Helper()
	e.assertReusable(t)
	t.Cleanup(func() { e.clearAfterEach(t) })
	e.assertEmpty(t)
	return e
}

// assertEmpty 检查本次运行的数据基线为空；不在准备阶段偷偷删除。
func (e *Environment) assertEmpty(t *testing.T) {
	t.Helper()
	records, requests, events, err := e.Mapper.CountOwned(context.Background())
	if err != nil {
		t.Fatalf("读取测试基线失败：%v", err)
	}
	if records+requests+events != 0 {
		t.Fatalf("测试基线不为空：records=%d requests=%d events=%d", records, requests, events)
	}
}

// clearAfterEach 删除本次运行的数据并恢复初始策略、清空日志；用例改过策略时等待一个刷新周期，
// 保证下一个用例看到的是初始策略。失败时标记环境不可复用。
func (e *Environment) clearAfterEach(t *testing.T) {
	e.Logs.Reset()
	changed, err := e.Mapper.ResetOwned(context.Background(), e.ID.SeedDataTypes(), e.ID.SeedRules())
	if err == nil && changed {
		time.Sleep(3 * PolicyRefreshInterval)
	}
	if err != nil {
		e.mu.Lock()
		e.unusable = true
		e.mu.Unlock()
		t.Errorf("全局清理失败，环境不可再复用：%v", err)
	}
}

func (e *Environment) assertReusable(t *testing.T) {
	t.Helper()
	e.mu.Lock()
	defer e.mu.Unlock()
	if e.unusable {
		t.Fatal("此前清理失败，测试环境不可复用；恢复后重新运行")
	}
}
