from pathlib import Path
from typing import Optional
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    # 项目基本信息
    PROJECT_NAME: str = "Excel Calc API"
    VERSION: str = "1.0.0"
    API_PREFIX: str = "/api/v1"
    APP_NAME: str = "Excel计算服务"

    DEBUG: bool = True
    LOG_LEVEL: str = "INFO"

# 创建全局配置实例
settings = Settings()