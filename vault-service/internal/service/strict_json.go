package service

import (
	"encoding/json"
	jsonv2 "encoding/json/v2"
	"io"
	"net/http"
	"sync"

	"github.com/gin-gonic/gin"
	"github.com/gin-gonic/gin/binding"
	"github.com/go-playground/validator/v10"

	"github.com/paytech/vault-service/internal/biz"
)

// maxBodyBytes 是请求体原始字节上限，超限即停止读取。
const maxBodyBytes = 256 * 1024

var registerOnce sync.Once

// registerValidations 向 Gin 校验器登记 Vault 自定义规则：vault_code、vault_token。
func registerValidations() {
	registerOnce.Do(func() {
		engine := binding.Validator.Engine().(*validator.Validate)
		_ = engine.RegisterValidation("vault_code", func(fl validator.FieldLevel) bool { return biz.IsCode(fl.Field().String()) })
		_ = engine.RegisterValidation("vault_token", func(fl validator.FieldLevel) bool { return biz.IsToken(fl.Field().String()) })
	})
}

// validate 按 binding 标签校验请求体。
func validate(body any) error { return binding.Validator.ValidateStruct(body) }

// bindStrictJSON 严格解析请求体：限长后由 encoding/json/v2 解码。
//
// v2 默认区分字段大小写、拒绝重复键（含转义后重复）、非法 UTF-8、孤立 UTF-16 代理与尾随内容；
// 再开启 RejectUnknownMembers 拒绝未声明字段。字段为 *string，数字、数组、对象等类型错误同样失败；
// 缺失与 null 解码为 nil，由 binding:"required" 拒绝。不使用 Gin 的 ShouldBindJSON（v1 解码不满足上述要求）。
func bindStrictJSON(c *gin.Context, target any) error {
	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxBodyBytes)
	raw, err := io.ReadAll(c.Request.Body)
	defer clear(raw)
	if err != nil || jsonv2.Unmarshal(raw, target, jsonv2.RejectUnknownMembers(true)) != nil {
		return biz.NewError(biz.InvalidRequest)
	}
	return nil
}

// text 解引用可空字符串。
func text(value *string) string {
	if value == nil {
		return ""
	}
	return *value
}

// WriteJSON 写出成功 JSON 响应；响应体没有动态序列化器，写失败不改变已提交的业务结果。
func WriteJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}
