# Git 和发布策略

## 分支

- `main`：始终保持可审查、可构建的主线。
- `feat/*`：新能力。
- `fix/*`：缺陷修复。
- `docs/*`：文档和仓库治理。
- `refactor/*`：不改变外部行为的重构。
- `release/*`：发布准备和变更说明。

不直接向 `main` 推送业务代码；通过 Pull Request 合并，并要求 CI 通过。

## 提交

使用 Conventional Commits：

```text
feat(runtime): resume task after approval
fix(policy): reject write tool without idempotency key
docs(api): document task event stream
```

提交应该是可回滚的最小变更。涉及数据库或协议的提交必须同时包含迁移、
兼容说明、测试和回滚方案。

## 版本

首期使用语义化版本 `0.x.y`：

- `0.x.0`：新增不保证稳定的新能力。
- `0.x.y`：兼容的修复、文档和内部改进。
- 进入生产稳定阶段后，再发布 `1.0.0` 并冻结公开契约。

发布前检查：

1. Java、Python、契约 CI 全部通过。
2. 版本矩阵、CHANGELOG 和迁移说明已更新。
3. 无密钥、数据库文件、构建产物和客户数据进入 Git。
4. Docker 镜像、数据库迁移和回滚路径经过审查。

## GitHub 设置建议

仓库创建后建议开启：

- `main` 分支保护和必须通过的 CI 检查。
- Pull Request 必须至少一名维护者审查。
- Dependabot 安全更新。
- Secret scanning 和 push protection。
- GitHub Security Advisories。
