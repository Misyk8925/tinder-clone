from __future__ import annotations

from datetime import datetime, timezone
from typing import Protocol
from urllib.parse import quote

import boto3
from botocore.config import Config
from botocore.exceptions import BotoCoreError, ClientError

from app.config import Settings
from app.exceptions import PhotoStorageError


class ObjectStorage(Protocol):
    def put(self, key: str, data: bytes, content_type: str) -> None: ...
    def delete(self, key: str) -> None: ...
    def list_keys(self, prefix: str) -> list[str]: ...
    def public_url(self, key: str) -> str: ...
    def presigned_download_url(self, key: str) -> str: ...


class MemoryStorage:
    def __init__(self, public_base: str = "https://cdn.test") -> None:
        self._objects: dict[str, bytes] = {}
        self._public_base = public_base.rstrip("/")

    def put(self, key: str, data: bytes, content_type: str) -> None:
        self._objects[key] = data

    def delete(self, key: str) -> None:
        self._objects.pop(key, None)

    def list_keys(self, prefix: str) -> list[str]:
        return [key for key in self._objects if key.startswith(prefix)]

    def public_url(self, key: str) -> str:
        return f"{self._public_base}/{key}"

    def presigned_download_url(self, key: str) -> str:
        return f"{self._public_base}/{key}?X-Amz-Expires=300"


class S3Storage:
    def __init__(self, settings: Settings) -> None:
        if not settings.aws_s3_bucket.strip():
            raise PhotoStorageError("Missing config: AWS_S3_BUCKET must be set for photo uploads")
        self._settings = settings
        self._bucket = settings.aws_s3_bucket.strip()
        client_kwargs: dict = {
            "service_name": "s3",
            "region_name": settings.aws_region,
            "aws_access_key_id": settings.aws_access_key_id,
            "aws_secret_access_key": settings.aws_secret_access_key,
            "config": Config(s3={"addressing_style": "path" if settings.aws_s3_endpoint else "auto"}),
        }
        if settings.aws_s3_endpoint:
            client_kwargs["endpoint_url"] = settings.aws_s3_endpoint
        self._client = boto3.client(**client_kwargs)
        self._ensure_bucket_if_local()

    def put(self, key: str, data: bytes, content_type: str) -> None:
        try:
            self._client.put_object(
                Bucket=self._bucket,
                Key=key,
                Body=data,
                ContentType=content_type,
                Metadata={
                    "x-origin": "fastapi",
                    "uploaded-at": datetime.now(timezone.utc).isoformat(),
                },
            )
        except (BotoCoreError, ClientError) as exc:
            raise PhotoStorageError(f"Failed to store object {key}") from exc

    def delete(self, key: str) -> None:
        try:
            self._client.delete_object(Bucket=self._bucket, Key=key)
        except (BotoCoreError, ClientError):
            return

    def list_keys(self, prefix: str) -> list[str]:
        try:
            paginator = self._client.get_paginator("list_objects_v2")
            keys: list[str] = []
            for page in paginator.paginate(Bucket=self._bucket, Prefix=prefix):
                for item in page.get("Contents", []):
                    keys.append(item["Key"])
            return keys
        except (BotoCoreError, ClientError) as exc:
            raise PhotoStorageError(f"Failed to list objects for prefix {prefix}") from exc

    def public_url(self, key: str) -> str:
        if self._settings.cloudfront_serves_traffic:
            return f"{self._settings.cloudfront_domain.rstrip('/')}/{key}"
        return f"https://{self._bucket}.s3.{self._settings.aws_region}.amazonaws.com/{quote(key, safe='/')}"

    def presigned_download_url(self, key: str) -> str:
        try:
            return self._client.generate_presigned_url(
                "get_object",
                Params={"Bucket": self._bucket, "Key": key},
                ExpiresIn=self._settings.presign_exp_seconds,
            )
        except (BotoCoreError, ClientError) as exc:
            raise PhotoStorageError(f"Failed to presign object {key}") from exc

    def _ensure_bucket_if_local(self) -> None:
        if not self._settings.aws_s3_endpoint:
            return
        try:
            self._client.head_bucket(Bucket=self._bucket)
        except ClientError as exc:
            status = exc.response.get("ResponseMetadata", {}).get("HTTPStatusCode")
            error_code = exc.response.get("Error", {}).get("Code", "")
            if status == 404 or error_code in {"404", "NoSuchBucket", "NotFound"}:
                self._client.create_bucket(Bucket=self._bucket)
            else:
                raise
