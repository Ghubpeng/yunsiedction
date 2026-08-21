# yunsie-ai-service
# 生产/容器运行镜像。开发期日常调试用本地 uvicorn --reload（见 docs/ARCHITECTURE.md）。
FROM python:3.14-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
