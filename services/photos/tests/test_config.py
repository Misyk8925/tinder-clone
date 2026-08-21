from pathlib import Path

from app.config import Settings, env_files, find_repo_root, get_settings


def _repo(tmp_path: Path) -> Path:
    (tmp_path / "docker-compose.yml").write_text("services: {}\n")
    return tmp_path


def test_given_a_nested_service_path_when_the_root_is_resolved_then_it_is_the_compose_directory(
    tmp_path: Path,
):
    root = _repo(tmp_path)
    nested = root / "services" / "photos" / "app"
    nested.mkdir(parents=True)
    (nested / "config.py").write_text("")

    assert find_repo_root(nested / "config.py") == root


def test_given_root_and_local_env_files_when_listed_then_local_overrides_root(tmp_path: Path):
    root = _repo(tmp_path)
    (root / ".env").write_text("AWS_S3_BUCKET=from-env\n")
    (root / ".env.local").write_text("AWS_S3_BUCKET=from-local\n")

    settings = Settings(_env_file=env_files(root))

    assert settings.aws_s3_bucket == "from-local"


def test_given_unrelated_root_keys_when_settings_load_then_unknown_keys_are_ignored(tmp_path: Path):
    root = _repo(tmp_path)
    (root / ".env").write_text(
        "AWS_S3_BUCKET=photos-bucket\nPOSTGRES_PASSWORD=ignore-me\nCDN_ENABLED=true\n"
    )

    settings = Settings(_env_file=env_files(root))

    assert settings.aws_s3_bucket == "photos-bucket"
    assert settings.cdn_enabled is True


def test_given_only_the_repo_root_env_when_cwd_is_the_service_dir_then_settings_use_the_root_file(
    tmp_path: Path, monkeypatch
):
    root = _repo(tmp_path)
    (root / ".env").write_text("AWS_S3_BUCKET=from-root\n")
    service_dir = root / "services" / "photos"
    service_dir.mkdir(parents=True)
    monkeypatch.chdir(service_dir)
    monkeypatch.setattr("app.config.find_repo_root", lambda start=None: root)
    get_settings.cache_clear()

    try:
        assert get_settings().aws_s3_bucket == "from-root"
    finally:
        get_settings.cache_clear()
