ARG JAVA_BUILD_IMAGE=damgox2-java-build:ubuntu26.04-java25-20261109
ARG JAVA_RUNTIME_IMAGE=damgox2-java-runtime:ubuntu26.04-java25-20261109
ARG NATIVE_BUILD_IMAGE=damgox2-native-build:ubuntu26.04-java25-20261109
ARG OS_RUNTIME_IMAGE=damgox2-ubuntu:ubuntu26.04-java25-20261109
FROM ${JAVA_BUILD_IMAGE} AS build
USER 0:0
WORKDIR /workspace
ENV GRADLE_USER_HOME=/workspace/.gradle-home
COPY gradlew gradlew.bat build.gradle settings.gradle gradle.properties gradle.lockfile ./
COPY gradle ./gradle
RUN echo '3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98  gradle/wrapper/gradle-wrapper.jar' | sha256sum -c -
COPY scripts ./scripts
COPY src ./src
COPY licenses ./licenses
RUN mkdir -p /workspace/.local/security-tools && javac -d /workspace/.local/security-tools scripts/security/TrustStorePatch.java
RUN --mount=type=cache,id=damgox2-gradle-9.8.2-java25,target=/workspace/.gradle-home,sharing=locked mkdir -p /workspace/.local/tmp && ./gradlew --no-daemon -Djava.io.tmpdir=/workspace/.local/tmp bootJar && sh scripts/security/fetch-thirdparty-sources.sh
FROM ${JAVA_RUNTIME_IMAGE} AS runtime
USER 0:0
WORKDIR /app
RUN groupadd -g 10001 damgox2 && useradd -r -u 10001 -g 10001 -M damgox2
COPY --from=build /workspace/.local/security-tools/TrustStorePatch.class /security/TrustStorePatch.class
COPY scripts/security/patch-os-trust.sh /security/patch-os-trust.sh
RUN java -cp /security TrustStorePatch "$JAVA_HOME/lib/security/cacerts" && sh /security/patch-os-trust.sh && rm -rf /security
COPY --from=build --chown=10001:10001 /workspace/build/libs/damgox2-1.0.0.jar /app/damgox2.jar
COPY THIRD_PARTY_NOTICES.md /opt/damgox2/THIRD_PARTY_NOTICES.md
COPY licenses /opt/damgox2/licenses
COPY --from=build /workspace/build/third-party-sources /opt/damgox2/third-party-sources
COPY --chmod=0555 scripts/performance/java-entrypoint.sh /opt/damgox2/java-entrypoint.sh
USER 10001:10001
ENV DAMGOX2_ROLE=web MALLOC_ARENA_MAX=2
ENTRYPOINT ["/opt/damgox2/java-entrypoint.sh"]
FROM ${NATIVE_BUILD_IMAGE} AS proxy-build
USER 0:0
ENV CGO_ENABLED=0
WORKDIR /proxy
COPY scripts/deploy/proxy/ ./
COPY scripts/security/CollectModuleLicenses.go /license-tools/collect.go
RUN --mount=type=cache,id=damgox2-go-mod,target=/root/go/pkg/mod \
 go mod download github.com/go-jose/go-jose/v3 && cp -R /root/go/pkg/mod/github.com/go-jose/go-jose/v3@v3.0.5 /proxy/patched-jose && chmod -R u+w /proxy/patched-jose \
 && cd /proxy/patched-jose && sha256sum -c /proxy/patches/original.sha256 && patch --fuzz=0 -p1 < /proxy/patches/jose-v3-security.patch \
 && sha256sum -c /proxy/patches/modified.sha256 && cp /proxy/patches/jose_security_test.go . && cp /proxy/patches/public_api_test.go . && cp /proxy/patches/json_security_test.go json/ && cp /proxy/patches/cipher_security_test.go cipher/
RUN go mod edit -replace=github.com/go-jose/go-jose/v3=./patched-jose
RUN --mount=type=cache,id=damgox2-go-mod,target=/root/go/pkg/mod --mount=type=cache,id=damgox2-go-build,target=/root/.cache/go-build go test -mod=readonly -timeout 120s github.com/go-jose/go-jose/v3/... && go build -mod=readonly -trimpath -o /caddy . && go run -mod=readonly /license-tools/collect.go /third-party-notices
FROM ${OS_RUNTIME_IMAGE} AS proxy
USER 0:0
RUN groupadd -g 10001 damgox2 && useradd -r -u 10001 -g 10001 -M damgox2 && mkdir -p /data /config && chown 10001:10001 /data /config
COPY --from=proxy-build /caddy /usr/bin/caddy
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=proxy-build /third-party-notices /opt/damgox2/licenses/go
COPY licenses/native/caddy /opt/damgox2/licenses/caddy
USER 10001:10001
ENV XDG_DATA_HOME=/data XDG_CONFIG_HOME=/config
EXPOSE 80 443
ENTRYPOINT ["caddy","run","--config","/etc/caddy/Caddyfile","--adapter","caddyfile"]
FROM proxy AS public-proxy
COPY scripts/platform/Caddyfile /etc/caddy/Caddyfile
FROM ${NATIVE_BUILD_IMAGE} AS ai-build
USER 0:0
ARG TARGETARCH
WORKDIR /source
RUN curl --fail --location --proto '=https' --tlsv1.2 -o source.tar.gz https://github.com/ggml-org/llama.cpp/archive/8642541b255bfb5dc39cd351a178be09013a55d6.tar.gz && echo '1688a167a8edd9499cdd0c19b8bd044f0fa184b466981977bbf2222d884953a4  source.tar.gz' | sha256sum -c - && tar -xzf source.tar.gz --strip-components=1 && rm source.tar.gz
RUN --mount=type=cache,id=damgox2-llama-glibc-b11960-${TARGETARCH},target=/source/build,sharing=locked cmake -S . -B build -DCMAKE_BUILD_TYPE=Release -DGGML_NATIVE=OFF -DBUILD_SHARED_LIBS=OFF -DGGML_RPC=OFF -DLLAMA_CURL=OFF -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_SERVER=ON -DLLAMA_BUILD_UI=OFF -DLLAMA_USE_PREBUILT_UI=OFF && cmake --build build --target llama-server -j 2 && cp build/bin/llama-server /llama-server
FROM ${OS_RUNTIME_IMAGE} AS ai
USER 0:0
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 util-linux && rm -rf /var/lib/apt/lists/* && groupadd -g 10001 damgox2 && useradd -r -u 10001 -g 10001 -M damgox2 && mkdir /models && chown 10001:10001 /models
COPY --from=ai-build /llama-server /usr/local/bin/llama-server
COPY --chmod=0555 scripts/translation/model-init.sh scripts/translation/serve.sh /opt/translation/
COPY --chmod=0555 scripts/classification/model-init.sh scripts/classification/serve.sh /opt/classification/
COPY --chmod=0555 scripts/platform/ai-entrypoint.sh /opt/damgox2/ai-entrypoint.sh
COPY licenses/models/index/ /opt/translation/licenses/model/
COPY licenses/models/qwen/ /opt/classification/licenses/model/
COPY licenses/native/llama.cpp/ /opt/damgox2/licenses/runtime/
COPY THIRD_PARTY_NOTICES.md /opt/damgox2/THIRD_PARTY_NOTICES.md
USER 10001:10001
ENV LLAMA_CACHE=/tmp/cache XDG_CONFIG_HOME=/tmp/config
EXPOSE 9931
ENTRYPOINT ["/opt/damgox2/ai-entrypoint.sh"]
