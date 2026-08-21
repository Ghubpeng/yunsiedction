"""LLM Provider 抽象层。

铁律：任何模型厂商必须通过 Provider 接入；业务代码不得写死厂商/模型。
Provider 的 chat 必须返回真实 usage（input/output tokens），
供 Java 侧按实际消耗结算（token-billing：计量以服务端 usage 为准）。
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any


@dataclass
class ChatMessage:
    role: str  # system | user | assistant
    content: str


@dataclass
class ChatResult:
    content: str
    input_tokens: int = 0
    output_tokens: int = 0
    raw: dict[str, Any] = field(default_factory=dict)


class LLMProvider(ABC):
    """Provider 基类。name 用于注册表路由。"""

    name: str = "base"

    @abstractmethod
    async def chat(self, model: str, messages: list[ChatMessage], **params: Any) -> ChatResult:
        """非流式对话。

        流式接口（SSE）在正式实现 Provider 时补充；
        计费结算规则（断连/超时按已产生 usage 结算）见 skills/token-billing。
        """
        raise NotImplementedError
