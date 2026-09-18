# 总体架构

```text
Next.js Console -> Java Agent Platform -> Business Agents / MCP Tools
                                      -> Python AI Services

PostgreSQL + Redis + MQ + Nacos + Prometheus/Trace + Kubernetes/Jenkins
```

Java 是控制面和业务执行面的唯一权威入口。Python 不保存平台任务状态，不绕过 Java Policy Engine 写入业务系统。
