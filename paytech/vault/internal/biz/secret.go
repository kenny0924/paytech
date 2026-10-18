package biz

// Wipe 尽力清零调用方持有的敏感字节；Go 运行时与密码库仍可能保留副本。
//
//go:noinline
func Wipe(value []byte) { clear(value) }
