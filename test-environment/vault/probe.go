package main

import (
	"net/http"
	"os"
	"time"
)

// 固定容器内就绪地址；不接受外部参数、不走代理、不跟随重定向或输出响应。
func main() {
	client := &http.Client{
		Timeout: 3 * time.Second,
		Transport: &http.Transport{
			Proxy:             nil,
			DisableKeepAlives: true,
		},
		CheckRedirect: func(*http.Request, []*http.Request) error {
			return http.ErrUseLastResponse
		},
	}
	response, err := client.Get("http://127.0.0.1:18107/health/readiness")
	if err != nil {
		os.Exit(1)
	}
	_ = response.Body.Close()
	if response.StatusCode != http.StatusOK {
		os.Exit(1)
	}
}
