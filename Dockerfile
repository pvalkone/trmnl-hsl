ARG BB_VERSION=1.12.218
FROM babashka/babashka:${BB_VERSION}

RUN groupadd \
  --system \
  --gid 10001 \
  trmnl \
  && useradd \
    --system \
    --uid 10001 \
    --gid trmnl \
    --no-create-home \
    --shell /usr/sbin/nologin \
    trmnl

WORKDIR /app
COPY bb.edn ./
COPY src/ src/
COPY views/ views/
COPY resources/ resources/

USER 10001:10001

ENV PORT=4001
EXPOSE 4001

HEALTHCHECK --interval=1m --timeout=5s --start-period=30s \
  CMD ["sh", "-c", "curl -fsS \"http://localhost:${PORT}/health\" > /dev/null || exit 1"]

CMD ["bb", "serve"]
