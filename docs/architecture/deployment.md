# 部署策略

本地开发使用 Docker Compose；测试环境使用 Kubernetes；生产环境通过 Jenkins 构建、评测、灰度和回滚。Java 平台首期为模块化单体，业务 Agent 和 Python 服务独立部署。
