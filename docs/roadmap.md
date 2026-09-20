# Roadmap

## M0 架构冻结

- [x] 仓库和模块目录
- [x] Java/Python/前端职责边界
- [x] OpenAPI、A2A、MCP、JSON Schema 契约入口
- [x] JDK/Spring AI 2.0 版本基线冻结（实际构建验证待安装 JDK/Maven）

## M1 Java 中台核心

- [x] 领域模型和任务状态机
- [x] Policy Engine 首个纯 Java 规则实现
- [x] 任务与 Outbox 原子持久化端口
- Agent/Tool Registry
- Supervisor Runtime
- Policy Engine 的审批持久化和恢复
- PostgreSQL 持久化
- REST/SSE API

## M2 业务 Agent

- Customer Agent
- Order Agent
- Support Agent
- 首个端到端业务闭环

## M3 AI 能力

- Spring AI 2.0 Model Gateway
- Structured Output 和 Tool Calling
- Python RAG/文档/评测服务

## M4 企业交付

- Redis、消息队列、Nacos
- Kubernetes、Prometheus、Trace
- Jenkins、灰度、回滚、数据库审批
