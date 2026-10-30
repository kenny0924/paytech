/** 已部署页面与动作的唯一 Web 注册表；服务端目录决定实际展示和授权。 */
export const pageCapabilities = [
  {
    "componentKey": "OPERATIONS_ACCOUNTS",
    "displayName": "人员管理",
    "frontendRoute": "/accounts",
    "actions": [
      {
        "resourceCode": "OPERATIONS_ACCOUNT_CREATE",
        "displayName": "新增"
      },
      {
        "resourceCode": "OPERATIONS_ACCOUNT_DISABLE",
        "displayName": "停用"
      },
      {
        "resourceCode": "OPERATIONS_ACCOUNT_ENABLE",
        "displayName": "启用"
      },
      {
        "resourceCode": "OPERATIONS_ACCOUNT_DELETE",
        "displayName": "删除"
      },
      {
        "resourceCode": "OPERATIONS_ACCOUNT_ASSIGN_ROLES",
        "displayName": "分配角色"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_ROLES",
    "displayName": "角色管理",
    "frontendRoute": "/roles",
    "actions": [
      {
        "resourceCode": "OPERATIONS_ROLE_CREATE",
        "displayName": "新增"
      },
      {
        "resourceCode": "OPERATIONS_ROLE_EDIT",
        "displayName": "编辑"
      },
      {
        "resourceCode": "OPERATIONS_ROLE_DELETE",
        "displayName": "删除"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_AUDIT",
    "displayName": "审计日志",
    "frontendRoute": "/audit",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_GATEWAY_CONFIGURATION",
    "displayName": "API 路由管理",
    "frontendRoute": "/gateway-configuration",
    "actions": [
      {
        "resourceCode": "OPERATIONS_GATEWAY_CONFIGURATION_MANAGE",
        "displayName": "管理"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_RISK_RULE_CONFIGURATION",
    "displayName": "规则配置",
    "frontendRoute": "/risk/rule-configuration",
    "actions": [
      {
        "resourceCode": "OPERATIONS_RISK_RULE_SET_SAVE",
        "displayName": "保存"
      },
      {
        "resourceCode": "OPERATIONS_RISK_RULE_SET_FREEZE",
        "displayName": "冻结"
      },
      {
        "resourceCode": "OPERATIONS_RISK_RULE_SET_DELETE",
        "displayName": "删除"
      },
      {
        "resourceCode": "OPERATIONS_RISK_RULE_SET_HISTORY",
        "displayName": "历史版本"
      },
      {
        "resourceCode": "OPERATIONS_RISK_RULE_SET_REFERENCES",
        "displayName": "使用关系"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_RISK_DECISION_FLOWS",
    "displayName": "决策流",
    "frontendRoute": "/risk/decision-flows",
    "actions": [
      {
        "resourceCode": "OPERATIONS_RISK_DECISION_FLOW_SAVE",
        "displayName": "保存"
      },
      {
        "resourceCode": "OPERATIONS_RISK_DECISION_FLOW_DELETE",
        "displayName": "删除"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_RISK_REJECTION_REASONS",
    "displayName": "拒绝原因",
    "frontendRoute": "/risk/rejection-reasons",
    "actions": [
      {
        "resourceCode": "OPERATIONS_RISK_REJECTION_REASON_CREATE",
        "displayName": "新增"
      },
      {
        "resourceCode": "OPERATIONS_RISK_REJECTION_REASON_UPDATE",
        "displayName": "编辑"
      },
      {
        "resourceCode": "OPERATIONS_RISK_REJECTION_REASON_STATUS",
        "displayName": "启停"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_RISK_SUBJECT_TAGS",
    "displayName": "主体标签",
    "frontendRoute": "/risk/subject-tags",
    "actions": [
      {
        "resourceCode": "OPERATIONS_RISK_SUBJECT_TAG_CREATE",
        "displayName": "新增"
      },
      {
        "resourceCode": "OPERATIONS_RISK_SUBJECT_TAG_UPDATE",
        "displayName": "编辑"
      },
      {
        "resourceCode": "OPERATIONS_RISK_SUBJECT_TAG_STATUS",
        "displayName": "启停"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_RISK_VARIABLES",
    "displayName": "变量管理",
    "frontendRoute": "/risk/variables",
    "actions": [
      {
        "resourceCode": "OPERATIONS_RISK_VARIABLE_MANAGE",
        "displayName": "管理"
      },
      {
        "resourceCode": "OPERATIONS_RISK_VARIABLE_DELETE",
        "displayName": "删除"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_RISK_LISTS",
    "displayName": "风控名单",
    "frontendRoute": "/risk/lists",
    "actions": [
      {
        "resourceCode": "OPERATIONS_RISK_LIST_MANAGE",
        "displayName": "维护名单"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_RISK_EXECUTION_RELEASES",
    "displayName": "执行发布",
    "frontendRoute": "/risk/execution-releases",
    "actions": [
      {
        "resourceCode": "OPERATIONS_RISK_EXECUTION_RELEASE_PREPARE",
        "displayName": "创建与校验发布"
      },
      {
        "resourceCode": "OPERATIONS_RISK_EXECUTION_RELEASE_ACTIVATE",
        "displayName": "激活发布"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_RISK_EVALUATIONS",
    "displayName": "评估查询",
    "frontendRoute": "/risk/evaluations",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_RISK_UNDERWRITING",
    "displayName": "承保",
    "frontendRoute": "/risk/underwriting",
    "actions": [
      {
        "resourceCode": "OPERATIONS_RISK_UNDERWRITING_MANAGE",
        "displayName": "维护"
      },
      {
        "resourceCode": "OPERATIONS_RISK_UNDERWRITING_APPROVE",
        "displayName": "复核"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_QUALIFICATIONS",
    "displayName": "主体资格",
    "frontendRoute": "/compliance/qualifications",
    "actions": [
      {
        "resourceCode": "OPERATIONS_QUALIFICATIONS_MANAGE",
        "displayName": "登记与撤销"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_CONSOLE_RESOURCES",
    "displayName": "菜单资源",
    "frontendRoute": "/console-resources",
    "actions": [
      {
        "resourceCode": "OPERATIONS_CONSOLE_RESOURCE_MANAGE",
        "displayName": "管理"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_PERMISSIONS",
    "displayName": "权限目录",
    "frontendRoute": "/permissions",
    "actions": [
      {
        "resourceCode": "OPERATIONS_PERMISSION_MANAGE",
        "displayName": "管理"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_CURRENCY_SPECS",
    "displayName": "币种",
    "frontendRoute": "/currency-specs",
    "actions": [
      {
        "resourceCode": "OPERATIONS_CURRENCY_SPEC_MANAGE",
        "displayName": "维护"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_FILE_STORAGE",
    "displayName": "存储管理",
    "frontendRoute": "/file-storage",
    "actions": [
      {
        "resourceCode": "OPERATIONS_FILE_STORAGE_MANAGE",
        "displayName": "维护"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_FILE_RECORDS",
    "displayName": "文件记录",
    "frontendRoute": "/file-records",
    "actions": [
      {
        "resourceCode": "OPERATIONS_FILE_RECORD_DOWNLOAD",
        "displayName": "下载"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_FILE_UPLOAD",
    "displayName": "文件上传",
    "frontendRoute": "/file-upload",
    "actions": [
      {
        "resourceCode": "OPERATIONS_FILE_UPLOAD_CREATE",
        "displayName": "上传"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_CARD_BIN_LIBRARY",
    "displayName": "卡 BIN 库",
    "frontendRoute": "/card-bin-library",
    "actions": [
      {
        "resourceCode": "OPERATIONS_CARD_BIN_LIBRARY_MANAGE",
        "displayName": "维护"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_REFERENCE_DATA",
    "displayName": "参考数据",
    "frontendRoute": "/reference-data",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_API_CREDENTIALS",
    "displayName": "API 凭证",
    "frontendRoute": "/api-credentials",
    "actions": [
      {
        "resourceCode": "OPERATIONS_API_CREDENTIAL_REVOKE",
        "displayName": "紧急撤销"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_API_SCOPES",
    "displayName": "API Scope",
    "frontendRoute": "/api-scopes",
    "actions": [
      {
        "resourceCode": "OPERATIONS_API_SCOPE_MANAGE",
        "displayName": "管理"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_TENANT_ACCESS",
    "displayName": "租户访问",
    "frontendRoute": "/tenant-access",
    "actions": [
      {
        "resourceCode": "OPERATIONS_TENANT_ACCESS_MANAGE",
        "displayName": "安全暂停与恢复"
      },
      {
        "resourceCode": "OPERATIONS_TENANT_MEMBER_READ",
        "displayName": "查看成员权限"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_EXTERNAL_STANDARD_APIS",
    "displayName": "标准 API",
    "frontendRoute": "/external-api/standard-apis",
    "actions": [
      {
        "resourceCode": "OPERATIONS_EXTERNAL_STANDARD_API_MANAGE",
        "displayName": "维护配置"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_CLIENT_ONBOARDING_REVIEW",
    "displayName": "客户入驻审核",
    "frontendRoute": "/client-onboarding/reviews",
    "actions": [
      {
        "resourceCode": "CLIENT_ONBOARDING_CLASSIFY",
        "displayName": "确认行业与 MCC"
      },
      {
        "resourceCode": "CLIENT_ONBOARDING_REVIEW_EVIDENCE",
        "displayName": "核验材料与检查"
      },
      {
        "resourceCode": "CLIENT_ONBOARDING_RETURN",
        "displayName": "退回修改"
      },
      {
        "resourceCode": "CLIENT_ONBOARDING_DECIDE",
        "displayName": "批准或最终拒绝"
      },
      {
        "resourceCode": "CLIENT_ONBOARDING_SCREENING",
        "displayName": "筛查与命中处置"
      },
      {
        "resourceCode": "CLIENT_ONBOARDING_RECOMMEND",
        "displayName": "推荐结论"
      },
      {
        "resourceCode": "CLIENT_ONBOARDING_WAIVE",
        "displayName": "豁免材料要求"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_ONBOARDING_CONFIG",
    "displayName": "入驻配置",
    "frontendRoute": "/client-onboarding/config",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_ONBOARDING_CODES",
    "displayName": "入驻码",
    "frontendRoute": "/client-onboarding/codes",
    "actions": [
      {
        "resourceCode": "OPERATIONS_ONBOARDING_CODES_MANAGE",
        "displayName": "签发与作废"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_CLIENTS",
    "displayName": "Client",
    "frontendRoute": "/master-data/clients",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_VAULT_DATA_TYPES",
    "displayName": "数据类型",
    "frontendRoute": "/vault/data-types",
    "actions": [
      {
        "resourceCode": "OPERATIONS_VAULT_DATA_TYPE_MANAGE",
        "displayName": "维护数据类型"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_VAULT_ACCESS_RULES",
    "displayName": "访问规则",
    "frontendRoute": "/vault/access-rules",
    "actions": [
      {
        "resourceCode": "OPERATIONS_VAULT_ACCESS_RULE_MANAGE",
        "displayName": "维护访问规则"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_VAULT_POLICY_CHANGES",
    "displayName": "策略变更记录",
    "frontendRoute": "/vault/policy-changes",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_VAULT_MANUAL_PROTECT",
    "displayName": "手动加密",
    "frontendRoute": "/vault/manual-protect",
    "actions": [
      {
        "resourceCode": "OPERATIONS_VAULT_SECRET_PROTECT",
        "displayName": "加密密钥类数据"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_VAULT_MANUAL_REVEAL",
    "displayName": "手动解密",
    "frontendRoute": "/vault/manual-reveal",
    "actions": [
      {
        "resourceCode": "OPERATIONS_VAULT_SECRET_REVEAL",
        "displayName": "解密密钥类数据"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_VAULT_MANUAL_EVENTS",
    "displayName": "人工操作记录",
    "frontendRoute": "/vault/manual-events",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_NOTIFICATION_TEMPLATES",
    "displayName": "通知模板",
    "frontendRoute": "/notification-templates",
    "actions": [
      {
        "resourceCode": "OPERATIONS_NOTIFICATION_TEMPLATE_MANAGE",
        "displayName": "维护模板"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_NOTIFICATION_PROVIDERS",
    "displayName": "发送账号",
    "frontendRoute": "/notification-providers",
    "actions": [
      {
        "resourceCode": "OPERATIONS_NOTIFICATION_PROVIDER_MANAGE",
        "displayName": "启停账号"
      },
      {
        "resourceCode": "OPERATIONS_NOTIFICATION_PROVIDER_EDIT",
        "displayName": "维护账号"
      },
      {
        "resourceCode": "OPERATIONS_NOTIFICATION_PROVIDER_CREDENTIAL",
        "displayName": "密钥与测试发送"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_NOTIFICATION_RECORDS",
    "displayName": "发送记录",
    "frontendRoute": "/notification-records",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_WEBHOOKS",
    "displayName": "Webhook 管理",
    "frontendRoute": "/webhooks",
    "actions": [
      {
        "resourceCode": "OPERATIONS_WEBHOOK_MANAGE",
        "displayName": "维护"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_FINANCE_SETUP",
    "displayName": "账务基础资料",
    "frontendRoute": "/finance/setup",
    "actions": [
      {
        "resourceCode": "OPERATIONS_FINANCE_SETUP_MANAGE",
        "displayName": "维护"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_FINANCE_LEDGER",
    "displayName": "账本查询",
    "frontendRoute": "/finance/ledger",
    "actions": [
      {
        "resourceCode": "OPERATIONS_FINANCE_LEDGER_MANAGE",
        "displayName": "发起试算"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_FINANCE_BALANCES",
    "displayName": "余额查询",
    "frontendRoute": "/finance/balances",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_FINANCE_SUBLEDGER",
    "displayName": "明细账查询",
    "frontendRoute": "/finance/subledger",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_FINANCE_ADJUSTMENTS",
    "displayName": "调整",
    "frontendRoute": "/finance/adjustments",
    "actions": [
      {
        "resourceCode": "OPERATIONS_FINANCE_ADJUSTMENTS_MANAGE",
        "displayName": "制单"
      },
      {
        "resourceCode": "OPERATIONS_FINANCE_ADJUSTMENTS_APPROVE",
        "displayName": "复核"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_FINANCE_PERIOD_CLOSE",
    "displayName": "期间关账",
    "frontendRoute": "/finance/period-close",
    "actions": [
      {
        "resourceCode": "OPERATIONS_FINANCE_PERIOD_CLOSE_MANAGE",
        "displayName": "发起"
      },
      {
        "resourceCode": "OPERATIONS_FINANCE_PERIOD_CLOSE_APPROVE",
        "displayName": "复核"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_FINANCE_ACCOUNTING_RULES",
    "displayName": "记账规则",
    "frontendRoute": "/finance/accounting-rules",
    "actions": [
      {
        "resourceCode": "OPERATIONS_FINANCE_ACCOUNTING_RULES_MANAGE",
        "displayName": "制单"
      },
      {
        "resourceCode": "OPERATIONS_FINANCE_ACCOUNTING_RULES_APPROVE",
        "displayName": "复核"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_FINANCE_TAX",
    "displayName": "税务查询",
    "frontendRoute": "/finance/tax",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_FINANCE_RECON_CLEARING",
    "displayName": "清算账户",
    "frontendRoute": "/finance/reconciliation/clearing-accounts",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_FINANCE_RECON_CASH",
    "displayName": "银行账户",
    "frontendRoute": "/finance/reconciliation/cash-accounts",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_FINANCE_RECON_STATEMENTS",
    "displayName": "渠道账单",
    "frontendRoute": "/finance/reconciliation/statements",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_FINANCE_RECON_PLATFORM_TXNS",
    "displayName": "平台流水",
    "frontendRoute": "/finance/reconciliation/platform-txns",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_FINANCE_RECON_CHANNEL_LINES",
    "displayName": "渠道流水",
    "frontendRoute": "/finance/reconciliation/channel-lines",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_FINANCE_RECON_BANK",
    "displayName": "银行对账",
    "frontendRoute": "/finance/reconciliation/bank",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_FINANCE_RECON_EXCEPTIONS",
    "displayName": "对账异常",
    "frontendRoute": "/finance/reconciliation/exceptions",
    "actions": [
      {
        "resourceCode": "OPERATIONS_FINANCE_RECON_OPERATE",
        "displayName": "对账操作"
      },
      {
        "resourceCode": "OPERATIONS_FINANCE_RECON_APPROVE",
        "displayName": "对账审批"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_FINANCE_RECON_SUMMARY",
    "displayName": "在途汇总",
    "frontendRoute": "/finance/reconciliation/summary",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_COMMERCIAL_AGREEMENTS",
    "displayName": "合同",
    "frontendRoute": "/commercial/agreements",
    "actions": [
      {
        "resourceCode": "OPERATIONS_COMMERCIAL_AGREEMENT_MANAGE",
        "displayName": "维护"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_COMMERCIAL_PRICING_PROFILES",
    "displayName": "定价画像",
    "frontendRoute": "/commercial/pricing-profiles",
    "actions": [
      {
        "resourceCode": "OPERATIONS_COMMERCIAL_PROFILE_MANAGE",
        "displayName": "维护"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_COMMERCIAL_TERMS",
    "displayName": "价目",
    "frontendRoute": "/commercial/terms",
    "actions": [
      {
        "resourceCode": "OPERATIONS_COMMERCIAL_TERM_MANAGE",
        "displayName": "维护"
      },
      {
        "resourceCode": "OPERATIONS_COMMERCIAL_TERM_APPROVE",
        "displayName": "审批"
      },
      {
        "resourceCode": "OPERATIONS_COMMERCIAL_TERM_SIGN",
        "displayName": "登记签署"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_COMMERCIAL_TEMPLATES",
    "displayName": "标准价目",
    "frontendRoute": "/commercial/templates",
    "actions": [
      {
        "resourceCode": "OPERATIONS_COMMERCIAL_TEMPLATE_MANAGE",
        "displayName": "维护"
      },
      {
        "resourceCode": "OPERATIONS_COMMERCIAL_TEMPLATE_APPROVE",
        "displayName": "复核"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_COMMERCIAL_WORKBENCH",
    "displayName": "工作台",
    "frontendRoute": "/commercial/workbench",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_COMMERCIAL_RATE_CARDS",
    "displayName": "费率预览",
    "frontendRoute": "/commercial/rate-cards",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_COMMERCIAL_PRICE_SIMULATOR",
    "displayName": "价格试算",
    "frontendRoute": "/commercial/price-simulator",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_COMMERCIAL_CATALOG",
    "displayName": "产品目录",
    "frontendRoute": "/commercial/catalog",
    "actions": [
      {
        "resourceCode": "OPERATIONS_COMMERCIAL_CATALOG_MANAGE",
        "displayName": "维护"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_COMMERCIAL_HISTORY",
    "displayName": "变更历史",
    "frontendRoute": "/commercial/history",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_OPERATING_ENTITIES",
    "displayName": "经营主体",
    "frontendRoute": "/master-data/operating-entities",
    "actions": [
      {
        "resourceCode": "OPERATIONS_OPERATING_ENTITIES_MANAGE",
        "displayName": "维护收单资质"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_LOCAL_PARTNERS",
    "displayName": "Local Partner",
    "frontendRoute": "/master-data/local-partners",
    "actions": [
      {
        "resourceCode": "OPERATIONS_LOCAL_PARTNERS_MANAGE",
        "displayName": "维护角色"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_CHANNEL_CHANNELS",
    "displayName": "渠道",
    "frontendRoute": "/channel/channels",
    "actions": [
      {
        "resourceCode": "OPERATIONS_CHANNEL_CHANNEL_MANAGE",
        "displayName": "维护"
      },
      {
        "resourceCode": "OPERATIONS_CHANNEL_ACCOUNT_CREDENTIAL",
        "displayName": "账号凭据"
      },
      {
        "resourceCode": "OPERATIONS_CHANNEL_CALL_GRANT_MANAGE",
        "displayName": "调用授权"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_CHANNEL_MIDS",
    "displayName": "MID 与能力",
    "frontendRoute": "/channel/mids",
    "actions": [
      {
        "resourceCode": "OPERATIONS_CHANNEL_MID_MANAGE",
        "displayName": "维护"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_CHANNEL_COST_PLANS",
    "displayName": "渠道成本",
    "frontendRoute": "/channel/cost-plans",
    "actions": [
      {
        "resourceCode": "OPERATIONS_CHANNEL_COST_MANAGE",
        "displayName": "维护"
      },
      {
        "resourceCode": "OPERATIONS_CHANNEL_COST_APPROVE",
        "displayName": "审批"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_WORKFLOW_MY_TASKS",
    "displayName": "我的待办",
    "frontendRoute": "/workflow/my-tasks",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_WORKFLOW_STARTED",
    "displayName": "我发起的",
    "frontendRoute": "/workflow/started",
    "actions": []
  },
  {
    "componentKey": "OPERATIONS_WORKFLOW_INSTANCES",
    "displayName": "流程实例",
    "frontendRoute": "/workflow/instances",
    "actions": [
      {
        "resourceCode": "OPERATIONS_WORKFLOW_INSTANCE_START",
        "displayName": "发起"
      },
      {
        "resourceCode": "OPERATIONS_WORKFLOW_INSTANCE_READ",
        "displayName": "查看全部"
      },
      {
        "resourceCode": "OPERATIONS_WORKFLOW_INSTANCE_MANAGE",
        "displayName": "管理"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_WORKFLOW_DEFINITIONS",
    "displayName": "流程定义",
    "frontendRoute": "/workflow/definitions",
    "actions": [
      {
        "resourceCode": "OPERATIONS_WORKFLOW_DEFINITION_MANAGE",
        "displayName": "维护"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_WORKFLOW_ACTION_TEMPLATES",
    "displayName": "动作目录",
    "frontendRoute": "/workflow/action-templates",
    "actions": [
      {
        "resourceCode": "OPERATIONS_WORKFLOW_ACTION_TEMPLATE_MANAGE",
        "displayName": "维护"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_DATA_SOURCES",
    "displayName": "数据源",
    "frontendRoute": "/data-query/data-sources",
    "actions": [
      {
        "resourceCode": "OPERATIONS_DATA_SOURCE_MANAGE",
        "displayName": "维护"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_QUERY_TEMPLATES",
    "displayName": "查询模板",
    "frontendRoute": "/data-query/templates",
    "actions": [
      {
        "resourceCode": "OPERATIONS_DATA_QUERY_TEMPLATE_MANAGE",
        "displayName": "维护"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_I18N_TRANSLATIONS",
    "displayName": "翻译",
    "frontendRoute": "/i18n/translations",
    "actions": [
      {
        "resourceCode": "OPERATIONS_I18N_TRANSLATION_EDIT",
        "displayName": "编辑翻译"
      },
      {
        "resourceCode": "OPERATIONS_I18N_KEY_IMPORT",
        "displayName": "导入 key"
      },
      {
        "resourceCode": "OPERATIONS_I18N_RELEASE_PUBLISH",
        "displayName": "发布"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_I18N_RELEASES",
    "displayName": "发布记录",
    "frontendRoute": "/i18n/releases",
    "actions": [
      {
        "resourceCode": "OPERATIONS_I18N_RELEASE_ACTIVATE",
        "displayName": "切回此版本"
      }
    ]
  },
  {
    "componentKey": "OPERATIONS_I18N_SETTINGS",
    "displayName": "语言与应用",
    "frontendRoute": "/i18n/settings",
    "actions": [
      {
        "resourceCode": "OPERATIONS_I18N_APPLICATION_CREATE",
        "displayName": "新建应用"
      },
      {
        "resourceCode": "OPERATIONS_I18N_NAMESPACE_MANAGE",
        "displayName": "维护命名空间"
      },
      {
        "resourceCode": "OPERATIONS_I18N_SETTINGS_EDIT",
        "displayName": "维护设置"
      }
    ]
  }
] as const;
export type PageComponentKey = typeof pageCapabilities[number]["componentKey"];
