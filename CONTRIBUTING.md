# Contributing

感谢参与 Enterprise Agent Platform。项目仍处于架构和核心能力建设阶段，
请先阅读 [AGENTS.md](AGENTS.md)、[AI 编程规范](docs/AI-CODING-STANDARDS.md)
和 [架构文档](docs/architecture/)。

## 开发环境

- JDK 21
- Maven 3.9+
- Python 3.12 和 `uv`
- Node.js 20+（前端模块开始实现后）
- Docker（可选，用于本地基础设施）

Java 平台构建入口是 `java-platform/pom.xml`。当前服务器未安装 Java/Maven，
因此本地无法替代 CI 完成 Java 编译验证。

## 分支和提交

从 `main` 创建功能分支：

```text
feat/<topic>
fix/<topic>
docs/<topic>
refactor/<topic>
test/<topic>
```

提交使用 Conventional Commits：

```text
feat(policy): require idempotency keys for write tools
fix(task): preserve trace id when creating child tasks
docs(architecture): explain Python service boundary
```

## Pull Request

PR 必须说明：

- 变更目标和影响模块。
- 是否改变 API、JSON Schema、A2A/MCP、数据库或配置。
- 执行过的测试命令及结果。
- 已知风险、迁移步骤和回滚方式。

涉及权限、审批、租户隔离、审计或敏感数据的 PR 需要额外说明负向测试。

## 行为准则和安全问题

请遵守 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。安全漏洞不要公开创建 Issue，
请按 [SECURITY.md](SECURITY.md) 的方式报告。
