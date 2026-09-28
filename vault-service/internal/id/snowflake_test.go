package id

import (
	"context"
	"sync"
	"testing"
	"time"
)

func TestNewGeneratorRejectsOutOfRangeNodeIDs(t *testing.T) {
	if _, err := NewGenerator(32, 0); err == nil {
		t.Fatal("datacenter ID 32 must be rejected")
	}
	if _, err := NewGenerator(0, 32); err == nil {
		t.Fatal("worker ID 32 must be rejected")
	}
}

func TestGeneratorUsesApprovedLayoutAndEpoch(t *testing.T) {
	millis := epochMillis + 7
	g := newGenerator(3, 5, func() int64 { return millis }, nil)
	value, err := g.Next(context.Background())
	if err != nil {
		t.Fatalf("next ID: %v", err)
	}
	want := int64((7 << timestampShift) | (3 << datacenterShift) | (5 << workerShift))
	if value != want {
		t.Fatalf("ID = %d, want %d", value, want)
	}
	if value <= 0 {
		t.Fatalf("ID = %d, want a positive BIGINT", value)
	}
}

func TestGeneratorSupportsAllSequencesInOneMillisecond(t *testing.T) {
	g := newGenerator(0, 0, func() int64 { return epochMillis + 1 }, nil)
	seen := make(map[int64]struct{}, maxSequence+1)
	for range maxSequence + 1 {
		value, err := g.Next(context.Background())
		if err != nil {
			t.Fatalf("sequence ID: %v", err)
		}
		if _, duplicate := seen[value]; duplicate {
			t.Fatalf("duplicate ID %d", value)
		}
		seen[value] = struct{}{}
	}
}

func TestGeneratorDoesNotConsumeSequenceWhenNextMillisecondWaitIsCanceled(t *testing.T) {
	millis := epochMillis + 1
	g := newGenerator(0, 0, func() int64 { return millis }, func(ctx context.Context, _ int64) (int64, error) {
		return 0, ctx.Err()
	})
	for range maxSequence + 1 {
		if _, err := g.Next(context.Background()); err != nil {
			t.Fatalf("fill sequence: %v", err)
		}
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if _, err := g.Next(ctx); err == nil {
		t.Fatal("canceled wait must fail")
	}
	millis++
	value, err := g.Next(context.Background())
	if err != nil {
		t.Fatalf("next millisecond after cancellation: %v", err)
	}
	if value&maxSequence != 0 {
		t.Fatalf("sequence = %d, want 0", value&maxSequence)
	}
}

func TestGeneratorClampsToleratedRollback(t *testing.T) {
	millis := epochMillis + 10
	g := newGenerator(0, 0, func() int64 { return millis }, nil)
	first, err := g.Next(context.Background())
	if err != nil {
		t.Fatalf("first ID: %v", err)
	}
	millis -= 5
	second, err := g.Next(context.Background())
	if err != nil {
		t.Fatalf("tolerated rollback: %v", err)
	}
	if second <= first || second>>timestampShift != first>>timestampShift {
		t.Fatalf("rollback must retain timestamp and advance sequence: first=%d second=%d", first, second)
	}
}

func TestGeneratorRejectsRollbackAboveTolerance(t *testing.T) {
	millis := epochMillis + 10
	g := newGenerator(0, 0, func() int64 { return millis }, nil)
	if _, err := g.Next(context.Background()); err != nil {
		t.Fatalf("first ID: %v", err)
	}
	millis -= 6
	if _, err := g.Next(context.Background()); err == nil {
		t.Fatal("rollback beyond five milliseconds must fail")
	}
}

func TestGeneratorIsConcurrentAndUnique(t *testing.T) {
	millis := epochMillis + 1
	g := newGenerator(1, 1, func() int64 { return millis }, nil)
	const count = 1024
	values := make(chan int64, count)
	var group sync.WaitGroup
	for range count {
		group.Add(1)
		go func() {
			defer group.Done()
			value, err := g.Next(context.Background())
			if err != nil {
				t.Errorf("next ID: %v", err)
				return
			}
			values <- value
		}()
	}
	group.Wait()
	close(values)
	seen := make(map[int64]struct{}, count)
	for value := range values {
		if _, duplicate := seen[value]; duplicate {
			t.Fatalf("duplicate concurrent ID %d", value)
		}
		seen[value] = struct{}{}
	}
	if len(seen) != count {
		t.Fatalf("unique IDs = %d, want %d", len(seen), count)
	}
}

func TestGeneratorRejectsTimeBeforeEpoch(t *testing.T) {
	g := newGenerator(0, 0, func() int64 { return epochMillis - 1 }, nil)
	if _, err := g.Next(context.Background()); err == nil {
		t.Fatal("time before epoch must fail")
	}
}

func TestDefaultEpochIsUTC2025(t *testing.T) {
	want := time.Date(2025, time.January, 1, 0, 0, 0, 0, time.UTC).UnixMilli()
	if epochMillis != want {
		t.Fatalf("epoch = %d, want %d", epochMillis, want)
	}
}
