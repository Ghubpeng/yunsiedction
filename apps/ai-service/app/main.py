"""应用入口。

职责边界（已确认产品决策 + project-architecture）：
- Java 主服务负责：用户身份、权限、业务上下文、Token 账务、订单、业务数据、AI 调用计费、AI 请求上下文组装。
- 本服务只负责：LLM 调用、RAG 检索、AI 推理、AI 知识处理、AI Provider 适配。

场景→模型→Provider 的路由配置由 Java 侧（ai 域）持久化并随请求下发，
本服务按请求参数执行，不在此处写死任何厂商或模型。
"""

from fastapi import FastAPI

from app.routers import health


def create_app() -> FastAPI:
    app = FastAPI(title="yunsie-ai-service", version="0.1.0")
    app.include_router(health.router)
    return app


app = create_app()
