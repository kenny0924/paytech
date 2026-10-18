package fixture

import "github.com/paytech/vault-service/internal/biz"

// SeedDataTypes 是本次运行在启动前写入数据库的数据类型。
func (i *Identity) SeedDataTypes() []biz.DataType {
	active := func(code string, mode biz.TokenMode, class biz.DataClass, manual bool) biz.DataType {
		return biz.DataType{Code: code, TokenMode: mode, Class: class, Description: "auto test " + code, ManualProtect: manual, ManualReveal: manual, Status: biz.ActiveStatus}
	}
	return []biz.DataType{
		active(i.StableType, biz.Stable, biz.StandardClass, false),
		active(i.ReferenceType, biz.Reference, biz.StandardClass, false),
		active(i.ManualType, biz.Stable, biz.StandardClass, true),
		active(i.SecretType, biz.Reference, biz.SecretClass, true),
	}
}

// SeedRules 是本次运行的服务访问规则：Caller 拥有两种数据类型的 Protect/Reveal，SecondCaller 只能 Protect STABLE 类型。
func (i *Identity) SeedRules() []biz.AccessRule {
	allow := func(caller string, action biz.Action, dataType string) biz.AccessRule {
		return biz.AccessRule{Caller: caller, Action: action, DataTypeCode: dataType, Purpose: Purpose, Status: biz.ActiveStatus}
	}
	return []biz.AccessRule{
		allow(i.Caller, biz.ProtectAction, i.StableType),
		allow(i.Caller, biz.ProtectAction, i.ReferenceType),
		allow(i.Caller, biz.RevealAction, i.StableType),
		allow(i.Caller, biz.RevealAction, i.ReferenceType),
		allow(i.SecondCaller, biz.ProtectAction, i.StableType),
	}
}
