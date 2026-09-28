package id

import (
	"context"
	"errors"
	"sync"
	"time"
)

const (
	epochMillis       int64 = 1735689600000
	rollbackTolerance int64 = 5
	sequenceBits            = 12
	workerBits              = 5
	datacenterBits          = 5
	workerShift             = sequenceBits
	datacenterShift         = sequenceBits + workerBits
	timestampShift          = sequenceBits + workerBits + datacenterBits
	maxSequence       int64 = (1 << sequenceBits) - 1
	maxWorkerID       uint8 = (1 << workerBits) - 1
	maxDatacenterID   uint8 = (1 << datacenterBits) - 1
	maxTimestamp      int64 = (1 << 41) - 1
)

var (
	errDatacenterID       = errors.New("Snowflake数据中心ID无效")
	errWorkerID           = errors.New("Snowflake工作节点ID无效")
	errClockBeforeEpoch   = errors.New("当前时间早于Snowflake纪元")
	errClockRollback      = errors.New("时钟回拨超过Snowflake容忍范围")
	errSequenceExhausted  = errors.New("同一毫秒Snowflake序列已耗尽")
	errTimestampExhausted = errors.New("Snowflake时间位已耗尽")
)

type Generator struct {
	datacenterID uint8
	workerID     uint8
	nowMillis    func() int64
	waitNext     func(context.Context, int64) (int64, error)

	mu            sync.Mutex
	lastTimestamp int64
	sequence      int64
}

func NewGenerator(datacenterID, workerID uint8) (*Generator, error) {
	if datacenterID > maxDatacenterID {
		return nil, errDatacenterID
	}
	if workerID > maxWorkerID {
		return nil, errWorkerID
	}
	return newGenerator(datacenterID, workerID, func() int64 { return time.Now().UTC().UnixMilli() }, nil), nil
}

func newGenerator(datacenterID, workerID uint8, nowMillis func() int64, waitNext func(context.Context, int64) (int64, error)) *Generator {
	return &Generator{
		datacenterID:  datacenterID,
		workerID:      workerID,
		nowMillis:     nowMillis,
		waitNext:      waitNext,
		lastTimestamp: -1,
	}
}

func (g *Generator) Next(ctx context.Context) (int64, error) {
	if err := ctx.Err(); err != nil {
		return 0, err
	}
	g.mu.Lock()
	defer g.mu.Unlock()

	current := g.nowMillis()
	if current < epochMillis {
		return 0, errClockBeforeEpoch
	}
	if g.lastTimestamp >= 0 && current < g.lastTimestamp {
		if g.lastTimestamp-current > rollbackTolerance {
			return 0, errClockRollback
		}
		current = g.lastTimestamp
	}

	sequence := int64(0)
	if current == g.lastTimestamp {
		if g.sequence == maxSequence {
			var err error
			current, err = g.nextMillisecond(ctx, g.lastTimestamp)
			if err != nil {
				return 0, err
			}
			if current <= g.lastTimestamp {
				return 0, errSequenceExhausted
			}
		} else {
			sequence = g.sequence + 1
		}
	}

	delta := current - epochMillis
	if delta > maxTimestamp {
		return 0, errTimestampExhausted
	}
	identifier := (delta << timestampShift) | (int64(g.datacenterID) << datacenterShift) | (int64(g.workerID) << workerShift) | sequence
	if identifier <= 0 {
		return 0, errTimestampExhausted
	}
	g.lastTimestamp = current
	g.sequence = sequence
	return identifier, nil
}

func (g *Generator) nextMillisecond(ctx context.Context, lastTimestamp int64) (int64, error) {
	if g.waitNext != nil {
		return g.waitNext(ctx, lastTimestamp)
	}
	for {
		if err := ctx.Err(); err != nil {
			return 0, err
		}
		current := g.nowMillis()
		if current > lastTimestamp {
			return current, nil
		}
		timer := time.NewTimer(time.Millisecond)
		select {
		case <-ctx.Done():
			timer.Stop()
			return 0, ctx.Err()
		case <-timer.C:
		}
	}
}
