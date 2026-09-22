# 当前实现基线

## 已落盘

- `java-platform/pom.xml`：22 个模块的 Maven 反应堆。
- `platform-domain`：Agent、Task、Tool、Approval、领域事件和任务状态机。
- `platform-application`：任务提交用例以及 Repository、Registry、Policy、Tool、Model 端口。
- `policy-engine`：高风险审批、写权限和幂等键的首个纯 Java 规则实现；审批请求持久化端口、内存仓储和恢复工作流。
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

## 环境状态

当前服务器尚未安装 `java`、`javac` 和 `mvn`。所有 POM 已完成 XML 解析和模块路径
核验，但 Java 编译、JUnit 执行和 Spring Boot 启动验证必须在安装 JDK/Maven 后完成。

## 下一批实现

1. 在 `platform-api` 接入租户鉴权和 Spring Controller 适配层。
2. 安装 JDK/Maven 后执行全量构建，再引入 Spring Boot 和 Spring AI 依赖。
