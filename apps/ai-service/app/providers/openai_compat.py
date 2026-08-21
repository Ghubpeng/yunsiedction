"""OpenAI 兼容协议 Provider（DeepSeek / 通义 / OpenAI 等统一入口）。

Stage 1 仅骨架：正式 HTTP 调用在 AI 场景开发阶段完成（本阶段禁止实现业务）。
密钥经环境变量注入（AI_PROVIDERS__<NAME>__API_KEY），禁止写死。
"""

from typing import Any

from app.core.llm.base import ChatMessage, ChatResult, LLMProvider


class OpenAICompatProvider(LLMProvider):
    name = "openai-compat"

    def __init__(self, base_url: str = "", api_key: str = "") -> None:
        self.base_url = base_url
        self.api_key = api_key

    async def chat(self, model: str, messages: list[ChatMessage], **params: Any) -> ChatResult:
        raise NotImplementedError("Stage 1 骨架：Provider 实现在 AI 场景开发阶段完成")
