from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    port: int = 8070
    aws_s3_bucket: str = ""
    aws_region: str = "eu-north-1"
    aws_access_key_id: str = "placeholder-access-key"
    aws_secret_access_key: str = "placeholder-secret-key"
    aws_s3_endpoint: str = ""
    cloudfront_domain: str = ""
    cdn_enabled: bool = False
    photos_presign_exp_seconds: int = 300
    photos_max_size_bytes: int = 5 * 1024 * 1024
    photos_min_dimension_px: int = 300
    photos_max_dimension_px: int = 4096
    photos_allowed_content_types: str = "image/jpeg,image/png,image/webp"

    @property
    def allowed_types(self) -> list[str]:
        return [item.strip() for item in self.photos_allowed_content_types.split(",") if item.strip()]

    @property
    def cloudfront_serves_traffic(self) -> bool:
        return self.cdn_enabled and bool(self.cloudfront_domain.strip())

    @property
    def presign_exp_seconds(self) -> int:
        return self.photos_presign_exp_seconds

    @property
    def max_size_bytes(self) -> int:
        return self.photos_max_size_bytes

    @property
    def min_dimension_px(self) -> int:
        return self.photos_min_dimension_px

    @property
    def max_dimension_px(self) -> int:
        return self.photos_max_dimension_px


@lru_cache
def get_settings() -> Settings:
    return Settings()
