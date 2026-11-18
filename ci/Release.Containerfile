FROM ghcr.io/jdx/mise:debian

RUN apt-get update && apt-get install -y --no-install-recommends \
    default-jre-headless \
    apksigner \
    gpg \
    git \
    curl \
    unzip \
    zip \
    ca-certificates \
    jq \
    && rm -rf /var/lib/apt/lists/*

COPY ci/release-mise.toml .mise.toml
RUN mise trust .mise.toml && \
    mise install && \
    rm -rf /root/.local/share/mise/downloads/* && \
    rm -rf /root/.cache/mise/*
