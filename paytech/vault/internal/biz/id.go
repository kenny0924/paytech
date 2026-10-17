package biz

import (
	"context"
)

// IDGenerator 生成 Snowflake 主键。
type IDGenerator interface {
	Next(ctx context.Context) (int64, error)
}

// NextID 生成正数主键；生成失败按安全错误返回。
func NextID(ctx context.Context, ids IDGenerator) (int64, error) {
	n, err := ids.Next(ctx)
	if err != nil {
		return 0, Sanitize(err)
	}
	if n <= 0 {
		return 0, NewError(InternalError)
	}
	return n, nil
}
