# 当前实现基线

## 已落盘

- `java-platform/pom.xml`：22 个模块的 Maven 反应堆。
- `platform-domain`：Agent、Task、Tool、Approval、领域事件和任务状态机。
- `platform-application`：任务提交用例以及 Repository、Registry、Policy、Tool、Model 端口。
- `policy-engine`：高风险审批、写权限和幂等键的首个纯 Java 规则实现；审批请求持久化端口、内存仓储和恢复工作流。
- `apps/agent-worker`：模型 Tool Call 单调用治理骨架，经过 Tool Registry、Policy Engine、审批持久化或 `ToolExecutorPort`，并记录 Tool 审计事件；默认执行器 fail-closed。
- `task-service`：任务与 `TASK_CREATED` Outbox 事件的原子持久化端口，以及本地内存适配器。
- `agent-runtime`：Supervisor 顺序编排骨架、Agent 调用端口、子任务上下文继承和失败收敛。
- `adapters/persistence-postgres`：JDBC PostgreSQL 适配器，覆盖任务、Outbox 和审批请求持久化。
- `platform-api`：框架无关 REST/SSE Facade、DTO 和审批/任务事件 API 边界。
- `docs/architecture/version-matrix.md`：JDK 21、Spring Boot 4.1.1、Spring AI 2.0.1 的首期基线。
- `platform-domain/src/test`：任务状态转换和高风险工具审批规则测试。

## 设计约束

领域模块不依赖 Spring、Spring AI、数据库或 Web 框架。模型供应商和 Spring AI
只能通过 `model-gateway` 与 `adapters/spring-ai-2` 接入。Python 服务不拥有平台
任务状态，也不能绕过 Policy Engine 执行高风险写操作。

## 验证状态

截至 2026-09-23，JDK 21 与 Maven Wrapper 可用。全量 `./mvnw test` 和
`./mvnw package -DskipTests` 已通过；5 项本机 HTTP socket 测试因运行环境限制跳过。
生产 MCP Tool 调用仍未接入或验证。

## 后续实现

1. 为 Tool Registry 持久化输入/输出 schema、权限、超时、重试和租户范围，并将可用 Tool 声明传给模型 Gateway。
2. 接入真实 MCP Tool Executor，校验参数 schema、租户边界、超时与下游幂等。
3. 将审批决定事件接回任务执行器；审批通过后恢复并重新校验策略，再执行待审批 Tool Call。
4. 用 PostgreSQL 集成测试覆盖任务/审批原子性、Outbox 重投、审批恢复及审计顺序。
