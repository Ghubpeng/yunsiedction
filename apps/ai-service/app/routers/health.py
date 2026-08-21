"""健康检查（骨架验证用）。"""

from fastapi import APIRouter

router = APIRouter(tags=["health"])


@router.get("/health")
async def health() -> dict:
    return {"status": "ok", "service": "yunsie-ai-service"}
