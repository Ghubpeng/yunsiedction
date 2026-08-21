"""RAG 检索抽象。

MVP 轻量方案（已确认决策）：不引入独立向量库基础设施；
先以关键词 + 本地索引实现，本接口预留 embedding 检索升级（向量库选型后置）。

引用溯源（rag-knowledge-base 铁律）：命中结果必须携带
source_type / source_id / knowledge_point_id / version，
供 AI 回答引用渲染（“本回答依据：…章节/知识点”）。
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any


@dataclass
class RetrievalHit:
    source_type: str = ""
    source_id: str = ""
    knowledge_point_id: str | None = None
    version: str | None = None
    score: float = 0.0
    text: str = ""
    extra: dict[str, Any] = field(default_factory=dict)


class Retriever(ABC):
    """检索器。filters 至少支持证书/考试科目过滤（检索前必须按可见范围过滤）。"""

    @abstractmethod
    async def retrieve(self, query: str, filters: dict[str, Any], top_k: int = 5) -> list[RetrievalHit]:
        raise NotImplementedError
