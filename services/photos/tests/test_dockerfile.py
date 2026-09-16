from pathlib import Path

DOCKERFILE = Path(__file__).resolve().parents[1] / "Dockerfile"


def test_given_bookworm_pcre2_cves_when_the_image_is_defined_then_libpcre2_is_upgraded():
    # Given python:3.12-slim-bookworm ships libpcre2-8-0 10.42-1
    # (CVE-2026-86145, CVE-2026-89161; fixed in 10.42-1+deb12u1),
    # when the photos image is built, then apt must install/upgrade that package.
    # Trivy image scan of this Dockerfile without the package failed CI as HIGH.
    text = DOCKERFILE.read_text()

    assert "apt-get update" in text
    assert "libpcre2-8-0" in text
