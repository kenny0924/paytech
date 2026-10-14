package fixture

import "encoding/json"

// PolicyJSON 生成本次运行的静态 Allowlist：Caller 拥有两种数据类型的 Protect/Reveal，SecondCaller 只能 Protect STABLE 类型。
func (i *Identity) PolicyJSON() []byte {
	type rule struct {
		CallerService string   `json:"callerService"`
		Action        string   `json:"action"`
		DataType      string   `json:"dataType"`
		Purposes      []string `json:"purposes"`
	}
	allow := func(caller, action, dataType string) rule {
		return rule{CallerService: caller, Action: action, DataType: dataType, Purposes: []string{Purpose}}
	}
	document := map[string]any{
		"policyVersion": "auto-" + i.RunID,
		"dataTypes": map[string]map[string]string{
			i.StableType:    {"tokenMode": "STABLE"},
			i.ReferenceType: {"tokenMode": "REFERENCE"},
		},
		"rules": []rule{
			allow(i.Caller, "PROTECT", i.StableType),
			allow(i.Caller, "PROTECT", i.ReferenceType),
			allow(i.Caller, "REVEAL", i.StableType),
			allow(i.Caller, "REVEAL", i.ReferenceType),
			allow(i.SecondCaller, "PROTECT", i.StableType),
		},
	}
	raw, err := json.Marshal(document)
	if err != nil {
		panic(err)
	}
	return raw
}

// PolicyVersion 是生成策略的版本，访问审计应记录该值。
func (i *Identity) PolicyVersion() string { return "auto-" + i.RunID }
