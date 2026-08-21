"""Provider 注册表：Provider 可替换（已确认决策：不把任何厂商写死在业务代码中）。"""

from typing import Callable, TypeAlias

from app.core.llm.base import LLMProvider

Factory: TypeAlias = Callable[[], LLMProvider]

_PROVIDERS: dict[str, Factory] = {}


def register(name: str, factory: Factory) -> None:
    if name in _PROVIDERS:
        raise ValueError(f"provider already registered: {name}")
    _PROVIDERS[name] = factory


def get_provider(name: str) -> LLMProvider:
    if name not in _PROVIDERS:
        raise KeyError(f"unknown provider: {name}")
    return _PROVIDERS[name]()


def registered_names() -> list[str]:
    return sorted(_PROVIDERS)
