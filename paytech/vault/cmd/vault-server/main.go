package main

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/internal/bootstrap"
	"github.com/paytech/vault-service/internal/conf"
	"github.com/paytech/vault-service/internal/pkg/observability"
)

// assembler 组装进程资源；生产为 bootstrap.Assemble，测试可替换。
type assembler func(context.Context, conf.Config, *observability.Runtime) (*bootstrap.App, error)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()
	if run(ctx, os.Getenv, os.Stdout, bootstrap.Assemble) != nil {
		slog.New(slog.NewJSONHandler(os.Stdout, nil)).Error("vault_server_stopped", "resultCode", "INTERNAL_ERROR")
		os.Exit(1)
	}
}

// run 读取配置、初始化观测、组装并监听，直到上下文结束后优雅停机。
func run(ctx context.Context, getenv func(string) string, out io.Writer, build assembler) (result error) {
	cfg, err := conf.Load(getenv)
	if err != nil {
		return biz.NewError(biz.InternalError)
	}
	startup, cancel := context.WithTimeout(ctx, 15*time.Second)
	defer cancel()
	obs, err := observability.New(startup, observability.Config{Writer: out, DisableExport: getenv("OTEL_TRACES_EXPORTER") == "none" || getenv("OTEL_SDK_DISABLED") == "true"})
	if err != nil {
		return biz.NewError(biz.InternalError)
	}
	defer func() {
		shutdown, cancel := context.WithTimeout(context.Background(), bootstrap.ShutdownTimeout)
		defer cancel()
		if obs.Shutdown(shutdown) != nil {
			slog.New(slog.NewJSONHandler(out, nil)).Error("telemetry_shutdown_failed")
		}
	}()
	app, err := build(startup, cfg, obs)
	if err != nil {
		return biz.NewError(biz.InternalError)
	}
	defer func() {
		if err := app.Close(); err != nil && result == nil {
			result = err
		}
	}()
	listener, err := net.Listen("tcp", cfg.HTTPAddress)
	if err != nil {
		return biz.NewError(biz.InternalError)
	}
	return serve(ctx, app.Server, listener)
}

// serve 运行 HTTP Server；上下文结束时在预算内优雅关闭，超时则强制关闭。
func serve(ctx context.Context, server *http.Server, listener net.Listener) error {
	stopped := make(chan error, 1)
	go func() { stopped <- server.Serve(listener) }()
	select {
	case err := <-stopped:
		if !errors.Is(err, http.ErrServerClosed) {
			return biz.NewError(biz.InternalError)
		}
		return nil
	case <-ctx.Done():
		shutdown, cancel := context.WithTimeout(context.Background(), bootstrap.ShutdownTimeout)
		defer cancel()
		if server.Shutdown(shutdown) != nil {
			_ = server.Close()
			<-stopped
			return biz.NewError(biz.InternalError)
		}
		if err := <-stopped; !errors.Is(err, http.ErrServerClosed) {
			return biz.NewError(biz.InternalError)
		}
		return nil
	}
}
