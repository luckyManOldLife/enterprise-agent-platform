# 模块边界

| 模块 | 职责 | 禁止依赖 |
|---|---|---|
| `platform-domain` | 领域对象、状态机、领域事件 | Spring、数据库、Web、Spring AI |
| `platform-application` | 用例、端口、事务编排 | 具体模型 SDK |
| `agent-runtime` | Supervisor、子任务、超时和恢复 | 前端代码 |
| `policy-engine` | 身份、租户、角色、风险、审批策略 | 模型输出授权结果 |
| `model-gateway` | 统一模型接口、路由、预算、Usage | 具体业务 Agent |
| `adapters/spring-ai-2` | Spring AI 2.0 到平台接口的映射 | 领域对象反向依赖 |
| `python-services` | AI 专项能力 | 平台数据库写权限 |
| `frontend` | 交互、状态展示、人工决策入口 | 模型和 Python 内部 API |

核心依赖方向：`domain -> application -> adapters`，禁止反向引用。
