// Package snowflake 基于 sony/sonyflake v2 生成 63 位正整数主键。
//
// 位布局：41 位毫秒时间（纪元 2025-01-01T00:00:00Z）| 12 位序列 | 10 位机器号（5 位数据中心 + 5 位工作节点）。
// 时钟回拨时 sonyflake 沿用上次时间继续递增序列，序列耗尽时等待下一毫秒，不产生重复 ID。
package snowflake

import (
	"context"
	"errors"
	"time"

	"github.com/sony/sonyflake/v2"
)

const (
	sequenceBits = 12
	nodeBits     = 5
)

// Epoch 是 ID 时间位的起点。
var Epoch = time.Date(2025, time.January, 1, 0, 0, 0, 0, time.UTC)

var errNodeID = errors.New("Snowflake数据中心或工作节点ID超出0～31")

// Generator 是并发安全的 ID 生成器；同一数据中心内每个实例的工作节点必须唯一。
type Generator struct{ flake *sonyflake.Sonyflake }

// NewGenerator 以数据中心与工作节点（各 0～31）组成 10 位机器号。
func NewGenerator(datacenterID, workerID uint8) (*Generator, error) {
	if datacenterID >= 1<<nodeBits || workerID >= 1<<nodeBits {
		return nil, errNodeID
	}
	machineID := int(datacenterID)<<nodeBits | int(workerID)
	flake, err := sonyflake.New(sonyflake.Settings{
		BitsSequence:  sequenceBits,
		BitsMachineID: 2 * nodeBits,
		TimeUnit:      time.Millisecond,
		StartTime:     Epoch,
		MachineID:     func() (int, error) { return machineID, nil },
	})
	if err != nil {
		return nil, err
	}
	return &Generator{flake: flake}, nil
}

// Next 生成下一个 ID；上下文已取消时不生成。
func (g *Generator) Next(ctx context.Context) (int64, error) {
	if err := ctx.Err(); err != nil {
		return 0, err
	}
	return g.flake.NextID()
}
