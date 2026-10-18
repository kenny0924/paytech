package snowflake

import (
	"context"
	"sync"
	"testing"
	"time"
)

func TestNewGeneratorRejectsOutOfRangeNodeIDs(t *testing.T) {
	for _, ids := range [][2]uint8{{32, 0}, {0, 32}, {255, 255}} {
		if _, err := NewGenerator(ids[0], ids[1]); err == nil {
			t.Fatalf("节点 %v 应被拒绝", ids)
		}
	}
	if _, err := NewGenerator(31, 31); err != nil {
		t.Fatal(err)
	}
}

func TestGeneratorEncodesMachineAndCurrentTime(t *testing.T) {
	g, err := NewGenerator(31, 27)
	if err != nil {
		t.Fatal(err)
	}
	before := time.Since(Epoch).Milliseconds()
	id, err := g.Next(context.Background())
	if err != nil || id <= 0 {
		t.Fatalf("id=%d err=%v", id, err)
	}
	parts := g.flake.Decompose(id)
	if parts["machine"] != 31<<5|27 {
		t.Fatalf("机器号未按 数据中心<<5|工作节点 编码：%v", parts)
	}
	if elapsed := id >> (sequenceBits + 2*nodeBits); elapsed < before || elapsed > before+1000 {
		t.Fatalf("时间位 %d 与当前时间 %d 不符", elapsed, before)
	}
}

func TestGeneratorIsConcurrentUniqueAndIncreasing(t *testing.T) {
	g, err := NewGenerator(31, 26)
	if err != nil {
		t.Fatal(err)
	}
	const workers, perWorker = 8, 2000
	var mu sync.Mutex
	seen := make(map[int64]bool, workers*perWorker)
	var wg sync.WaitGroup
	for range workers {
		wg.Go(func() {
			last := int64(0)
			for range perWorker {
				id, err := g.Next(context.Background())
				if err != nil || id <= last {
					t.Errorf("id=%d last=%d err=%v", id, last, err)
					return
				}
				last = id
				mu.Lock()
				seen[id] = true
				mu.Unlock()
			}
		})
	}
	wg.Wait()
	if len(seen) != workers*perWorker {
		t.Fatalf("出现重复 ID：%d/%d", len(seen), workers*perWorker)
	}
}

func TestGeneratorHonorsCanceledContext(t *testing.T) {
	g, err := NewGenerator(0, 0)
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if _, err := g.Next(ctx); err == nil {
		t.Fatal("已取消的上下文不应生成 ID")
	}
}
