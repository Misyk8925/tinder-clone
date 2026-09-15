FROM python:3.12-slim-bookworm

WORKDIR /app

# wget: Compose healthcheck. libpcre2-8-0: slim-bookworm ships 10.42-1;
# Debian 10.42-1+deb12u1 fixes CVE-2026-86145 and CVE-2026-89161 (Trivy HIGH).
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget libpcre2-8-0 \
    && rm -rf /var/lib/apt/lists/* \
    && addgroup --system app \
    && adduser --system --ingroup app app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

USER app
EXPOSE 8070
ENV PORT=8070

CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT}"]
