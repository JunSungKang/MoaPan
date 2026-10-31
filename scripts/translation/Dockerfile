FROM alpine:3.24.3@sha256:be2656061b536c1883c4034790e21d0a09c5aaaf51968b89368a3c0371ea2c9a AS verified-alpine
COPY scripts/security/patch-os-trust.sh /security/patch-os-trust.sh
RUN apk upgrade --no-cache && apk add --no-cache ca-certificates openssl \
    && sh /security/patch-os-trust.sh
FROM verified-alpine AS build
ARG TARGETARCH
RUN apk upgrade --no-cache && apk add --no-cache ca-certificates curl cmake build-base linux-headers \
    && sh /security/patch-os-trust.sh && apk del openssl && rm -rf /security
WORKDIR /source
RUN curl -fL --retry 2 -o source.tar.gz https://github.com/ggml-org/llama.cpp/archive/8642541b255bfb5dc39cd351a178be09013a55d6.tar.gz \
    && echo '1688a167a8edd9499cdd0c19b8bd044f0fa184b466981977bbf2222d884953a4  source.tar.gz' | sha256sum -c - \
    && tar -xzf source.tar.gz --strip-components=1 && rm source.tar.gz
RUN --mount=type=cache,id=moapan-llama-musl-b11960-${TARGETARCH},target=/source/build,sharing=locked cmake -S . -B build -DCMAKE_BUILD_TYPE=Release -DGGML_NATIVE=OFF -DBUILD_SHARED_LIBS=OFF -DLLAMA_CURL=OFF -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_SERVER=ON -DLLAMA_BUILD_UI=OFF -DLLAMA_USE_PREBUILT_UI=OFF \
    && cmake --build build --target llama-server -j 2 && cp build/bin/llama-server /llama-server
FROM verified-alpine AS translator
RUN apk upgrade --no-cache && apk add --no-cache ca-certificates curl util-linux-misc libgomp libstdc++ \
    && sh /security/patch-os-trust.sh && apk del openssl && rm -rf /security \
    && addgroup -g 10001 translator && adduser -D -H -u 10001 -G translator translator && mkdir /models && chown 10001:10001 /models
COPY --from=build /llama-server /usr/local/bin/llama-server
COPY --chmod=0555 scripts/translation/model-init.sh scripts/translation/serve.sh /opt/translation/
COPY licenses/models/index/ /opt/translation/licenses/model/
COPY licenses/native/llama.cpp/ /opt/translation/licenses/runtime/
COPY THIRD_PARTY_NOTICES.md /opt/translation/THIRD_PARTY_NOTICES.md
USER 10001:10001
ENV LLAMA_CACHE=/tmp/cache XDG_CONFIG_HOME=/tmp/config
ENTRYPOINT ["/opt/translation/serve.sh"]
