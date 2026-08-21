"""配置入口。

所有配置来自环境变量 / .env（project-architecture 铁律 4：禁止硬编码密钥）。
Provider 密钥约定环境变量前缀：AI_PROVIDERS__<NAME>__API_KEY / __BASE_URL，
由部署环境注入，仓库中绝不出现真实凭据。
"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "yunsie-ai-service"
    env: str = "dev"

    # Java → AI 服务间鉴权 secret（内网专用；Java 侧同名配置注入）
    internal_api_secret: str = ""

    # 开发兜底默认值；正式运行时由 Java 侧按 场景→模型 路由配置下发 model + provider
    default_provider: str = ""
    default_model: str = ""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="AI_",
        extra="ignore",
    )


settings = Settings()
