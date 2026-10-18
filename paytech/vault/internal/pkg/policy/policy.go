// Package policy 提供 biz.Authorizer 的实现：从数据库读取的策略快照，按固定间隔全量刷新。
//
// 快照只包含 ACTIVE 数据类型与引用 ACTIVE 数据类型的 ACTIVE 规则；刷新失败时保留上一份快照。
package policy

import (
	"context"
	"sync"
	"sync/atomic"
	"time"

	"github.com/paytech/vault-service/internal/biz"
)

// Loader 一致性读取当前策略，由 data.Store 实现。
type Loader interface {
	LoadPolicy(ctx context.Context) (biz.PolicyState, error)
}

// Snapshot 是某一时刻编译好的不可变策略。
type Snapshot struct {
	version   string
	dataTypes map[string]biz.DataType
	rules     map[string]biz.ValueRule
	decisions map[decisionKey]struct{}
}

type decisionKey struct {
	caller   string
	action   biz.Action
	dataType string
	purpose  string
}

// Build 由数据库状态编译快照；忽略引用了非 ACTIVE 数据类型的规则。
func Build(state biz.PolicyState) *Snapshot {
	s := &Snapshot{
		version:   biz.PolicyVersionLabel(state.Version),
		dataTypes: make(map[string]biz.DataType, len(state.DataTypes)),
		rules:     make(map[string]biz.ValueRule, len(state.DataTypes)),
		decisions: make(map[decisionKey]struct{}, len(state.Rules)),
	}
	for _, dataType := range state.DataTypes {
		if dataType.Status == biz.ActiveStatus {
			s.dataTypes[dataType.Code] = dataType
			s.rules[dataType.Code] = compileRule(dataType)
		}
	}
	for _, rule := range state.Rules {
		if _, ok := s.dataTypes[rule.DataTypeCode]; ok && rule.Status == biz.ActiveStatus {
			s.decisions[decisionKey{rule.Caller, rule.Action, rule.DataTypeCode, rule.Purpose}] = struct{}{}
		}
	}
	return s
}

// DataTypeMode 返回 ACTIVE 数据类型的 Token 模式。
func (s *Snapshot) DataTypeMode(dataType string) (biz.TokenMode, bool) {
	d, ok := s.dataTypes[dataType]
	return d.TokenMode, ok
}

// Authorize 判断服务访问规则是否允许。
func (s *Snapshot) Authorize(caller string, action biz.Action, dataType, purpose string) bool {
	_, ok := s.decisions[decisionKey{caller, action, dataType, purpose}]
	return ok
}

// Version 返回快照对应的策略版本标识。
func (s *Snapshot) Version() string { return s.version }

// DataType 返回 ACTIVE 数据类型。
func (s *Snapshot) DataType(code string) (biz.DataType, bool) {
	d, ok := s.dataTypes[code]
	return d, ok
}

// CheckValue 按快照中预编译的取值规则校验原值。
func (s *Snapshot) CheckValue(dataType string, value []byte) error {
	rule, ok := s.rules[dataType]
	if !ok {
		return biz.NewError(biz.UnsupportedDataType)
	}
	return rule.Check(value)
}

// compileRule 编译数据类型的取值规则；库中规则无法编译（保存时已校验，正常不会发生）时返回零值规则，
// 该类型的 Protect 一律失败关闭，其他类型不受影响。未设置值格式等同列默认值 TEXT。
func compileRule(dataType biz.DataType) biz.ValueRule {
	format := dataType.ValueFormat
	if format == "" {
		format = biz.TextFormat
	}
	rule, err := biz.CompileValueRule(format, dataType.ValuePattern)
	if err != nil {
		return biz.ValueRule{}
	}
	return rule
}

// Refresher 持有当前快照并定时全量刷新；对外以 biz.Authorizer 使用。
type Refresher struct {
	loader   Loader
	interval time.Duration
	onError  func()
	current  atomic.Pointer[Snapshot]
	stop     chan struct{}
	done     chan struct{}
	once     sync.Once
}

// NewRefresher 创建刷新器；onError 在刷新失败时调用（只记录固定诊断，不含原因）。
func NewRefresher(loader Loader, interval time.Duration, onError func()) *Refresher {
	return &Refresher{loader: loader, interval: interval, onError: onError, stop: make(chan struct{}), done: make(chan struct{})}
}

// Start 同步加载一次（失败即返回错误，调用方应拒绝启动），然后在后台定时刷新。
func (r *Refresher) Start(ctx context.Context) error {
	if err := r.Refresh(ctx); err != nil {
		return err
	}
	go r.loop()
	return nil
}

// Refresh 立即全量读取并替换快照；失败时保留上一份快照。
func (r *Refresher) Refresh(ctx context.Context) error {
	state, err := r.loader.LoadPolicy(ctx)
	if err != nil {
		return biz.NewError(biz.DependencyUnavailable)
	}
	r.current.Store(Build(state))
	return nil
}

// Close 停止后台刷新并等待其退出。
func (r *Refresher) Close() {
	r.once.Do(func() { close(r.stop) })
	<-r.done
}

func (r *Refresher) loop() {
	defer close(r.done)
	ticker := time.NewTicker(r.interval)
	defer ticker.Stop()
	for {
		select {
		case <-r.stop:
			return
		case <-ticker.C:
			ctx, cancel := context.WithTimeout(context.Background(), r.interval)
			if r.Refresh(ctx) != nil && r.onError != nil {
				r.onError()
			}
			cancel()
		}
	}
}

func (r *Refresher) snapshot() *Snapshot {
	if s := r.current.Load(); s != nil {
		return s
	}
	return Build(biz.PolicyState{})
}

// DataTypeMode 委托当前快照。
func (r *Refresher) DataTypeMode(dataType string) (biz.TokenMode, bool) {
	return r.snapshot().DataTypeMode(dataType)
}

// Authorize 委托当前快照。
func (r *Refresher) Authorize(caller string, action biz.Action, dataType, purpose string) bool {
	return r.snapshot().Authorize(caller, action, dataType, purpose)
}

// Version 委托当前快照。
func (r *Refresher) Version() string { return r.snapshot().Version() }

// DataType 委托当前快照。
func (r *Refresher) DataType(code string) (biz.DataType, bool) { return r.snapshot().DataType(code) }

// CheckValue 委托当前快照。
func (r *Refresher) CheckValue(dataType string, value []byte) error {
	return r.snapshot().CheckValue(dataType, value)
}
