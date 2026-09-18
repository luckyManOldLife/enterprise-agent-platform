# 安全边界

- 前端权限只负责隐藏不可用操作，最终授权必须由 Java Policy Engine 决定。
- 子 Agent 必须重新校验租户、用户和关键业务权限。
- 高风险 Tool 默认需要人工审批；写操作必须提供幂等键。
- Python 服务不得拥有平台数据库写权限。
- 模型不得直接执行 SQL 或绕过 Tool Registry。
- 日志记录 trace/task/agent/tool 关联信息，敏感输入、密钥和完整私有 Prompt 必须脱敏。
