# Enterprise Agent Platform

企业 Agent 中台综合实战项目。项目采用 **Java/Spring AI 2.0 主平台 + Python AI 专项服务 + Next.js 运营控制台** 的架构。

![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)
![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring%20AI](https://img.shields.io/badge/Spring%20AI-2.0.1-6db33f.svg)

本仓库按开源项目方式维护，欢迎通过 Issue 和 Pull Request 参与。项目仍处于
核心能力建设阶段，不应直接连接生产客户数据或生产业务系统。

正式架构代码位于 `java-platform/`、`python-services/`、`frontend/` 和 `contracts/`。
早期 Python 本地闭环已归档到 `prototype/python-local/`，仅作对照和回归使用，
不作为正式平台运行入口。

## 技术分工

```text
Java / Spring Boot       平台控制面、任务状态机、权限、审批、Agent/Tool 治理
Spring AI 2.0            Model Gateway 的模型适配层
Python                   RAG、文档解析、Embedding/Rerank、评测、Computer Use
Next.js                  Agent 工作台、任务中心、审批中心、运维控制台
PostgreSQL               任务、Agent、Tool、审计和评测数据
Redis / MQ               缓存、锁、事件和异步任务
Nacos / Kubernetes       配置、发现、部署和弹性
MCP / A2A                Tool 与 Agent 的统一互操作边界
```

## 首个业务闭环

`查询客户最近订单并创建售后任务`：Supervisor 调用 Customer Agent 和 Order Agent，Support Tool 的高风险写操作必须进入人工审批，最后返回结构化结果并留下完整审计链。

## 开发顺序

1. 冻结版本兼容矩阵和跨语言契约。
2. 完成 Java 领域模型、任务状态机和平台 API。
3. 接入三个独立业务 Agent。
4. 接入 Spring AI 2.0 Model Gateway。
5. 接入 Python RAG/评测专项服务。
6. 接入 PostgreSQL、Redis、Nacos、Kubernetes、Jenkins。

详细规则见 `docs/architecture/`、`contracts/` 和 `docs/roadmap.md`。

## 贡献和 AI 协作

贡献流程见 [CONTRIBUTING.md](CONTRIBUTING.md)，AI 编程约束见
[AGENTS.md](AGENTS.md) 和 [docs/AI-CODING-STANDARDS.md](docs/AI-CODING-STANDARDS.md)。
项目使用 Apache-2.0 许可证，详见 [LICENSE](LICENSE)。
