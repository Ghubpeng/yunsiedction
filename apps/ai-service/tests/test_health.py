"""健康检查测试。

运行前提：已安装 requirements.txt 依赖（pip install -r requirements.txt）。
本阶段不安装依赖、不运行测试；依赖安装留待 AI 场景开发阶段。
"""

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health() -> None:
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"
