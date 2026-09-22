# 版本兼容矩阵

正式开发前必须在此表冻结实际版本：

| 组件 | 目标版本 | 验证状态 | 备注 |
|---|---|---|---|
| JDK | 21 | 已冻结 | 运行基线；Spring Boot 4.x 要求至少 JDK 17 |
| Spring Boot | 4.1.1 | 已冻结 | 与 Spring AI 2.0.1 的官方支持线匹配 |
| Spring AI | 2.0.1 | 已冻结 | 只进入 `adapters/spring-ai-2` |
| PostgreSQL JDBC | 42.7.13 | 已冻结 | Worker 使用 JDBC PostgreSQL 持久化；2026-09-22 纳入项目依赖基线 |
| Spring Cloud | 与 Boot 匹配 | 待验证 | 服务治理 |
| Spring Cloud Alibaba | 与 Cloud/Nacos 匹配 | 待验证 | 配置和发现 |
| Nacos Client | 与 Alibaba 匹配 | 待验证 | 不单独升级 |
| Python | 3.12 | 已具备 | uv 管理 |
| Node.js | 20+ | 待验证 | Next.js 控制台 |

## 冻结说明

2026-09-18 起，Java 平台以 JDK 21、Spring Boot 4.1.1 和 Spring AI 2.0.1
作为首期开发基线。真正引入 Spring 依赖前，必须在 CI 中执行一次依赖解析和
最小启动验证；如果 Spring AI 发布新的兼容矩阵，变更必须同步更新本文件。
