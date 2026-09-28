package httpapi

import (
	"context"
	"net/http"

	"github.com/paytech/vault-service/internal/application"
	"github.com/paytech/vault-service/internal/domain"
)

func (s *server) business(w http.ResponseWriter, r *http.Request, a application.Attempt, p *domain.AuditProjection) error {
	if a.Action == domain.ProtectAction {
		return s.protect(w, r, a, p)
	}
	return s.reveal(w, r, a, p)
}
func projectFields(p *domain.AuditProjection, dataType, purpose string) {
	if codePattern.MatchString(dataType) {
		p.DataType = &dataType
	}
	if codePattern.MatchString(purpose) {
		p.Purpose = &purpose
	}
}
func (s *server) early(ctx context.Context, p *domain.AuditProjection, err error) error {
	p.ResultCode = string(domain.CodeOf(err))
	p.Outcome = domain.OutcomeForCode(domain.CodeOf(err))
	if s.Service.AuditEarly(ctx, *p) != nil {
		return domain.NewError(domain.AuditUnavailable)
	}
	return err
}
func (s *server) protect(w http.ResponseWriter, r *http.Request, a application.Attempt, p *domain.AuditProjection) error {
	var doc protectDocument
	if err := decode(w, r, &doc, "dataType", "tokenMode", "value", "purpose"); err != nil {
		return s.early(r.Context(), p, err)
	}
	projectFields(p, text(doc.DataType), text(doc.Purpose))
	if p.DataType == nil || p.Purpose == nil || doc.Value == nil || doc.TokenMode == nil || (*doc.TokenMode != "REFERENCE" && *doc.TokenMode != "STABLE") {
		return s.early(r.Context(), p, domain.NewError(domain.InvalidRequest))
	}
	if len(*doc.Value) > 32*1024 {
		return s.early(r.Context(), p, domain.NewError(domain.ValueTooLarge))
	}
	value := []byte(*doc.Value)
	defer clear(value)
	result, err := s.Service.Protect(r.Context(), a, domain.ProtectInput{DataType: *doc.DataType, TokenMode: domain.TokenMode(*doc.TokenMode), Value: value, Purpose: *doc.Purpose, IdempotencyKey: r.Header.Get("Idempotency-Key")})
	if err != nil {
		return err
	}
	writeJSON(w, 200, struct {
		Token string `json:"token"`
	}{result.Token})
	return nil
}
func (s *server) reveal(w http.ResponseWriter, r *http.Request, a application.Attempt, p *domain.AuditProjection) error {
	var doc revealDocument
	if err := decode(w, r, &doc, "token", "purpose"); err != nil {
		return s.early(r.Context(), p, err)
	}
	projectFields(p, "", text(doc.Purpose))
	if p.Purpose == nil || !tokenPattern.MatchString(text(doc.Token)) {
		return s.early(r.Context(), p, domain.NewError(domain.InvalidRequest))
	}
	result, err := s.Service.Reveal(r.Context(), a, domain.RevealInput{Token: *doc.Token, Purpose: *doc.Purpose})
	defer clear(result.Value)
	if err != nil {
		return err
	}
	// []byte would be base64 encoded by encoding/json; the wire contract is a
	// JSON string. Clear the owned byte buffer even when the peer disconnects.
	writeJSON(w, 200, struct {
		Value string `json:"value"`
	}{string(result.Value)})
	return nil
}
