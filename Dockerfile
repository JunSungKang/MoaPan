# Official latest Temurin security build for musl; Ubuntu residual OS components are removed.
FROM alpine:3.24.3@sha256:be2656061b536c1883c4034790e21d0a09c5aaaf51968b89368a3c0371ea2c9a AS verified-alpine
COPY scripts/security/patch-os-trust.sh /security/patch-os-trust.sh
RUN apk upgrade --no-cache && apk add --no-cache ca-certificates openssl \
    && sh /security/patch-os-trust.sh
FROM verified-alpine AS patched-jdk
ARG TARGETARCH
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
RUN apk upgrade --no-cache && apk add --no-cache curl ca-certificates libstdc++ zlib \
    && sh /security/patch-os-trust.sh && apk del openssl && rm -rf /security
RUN case "$TARGETARCH" in amd64) jdk_arch=x64; checksum=576a3b8ba79aa5a8bc7f1cb8b54aeffab8b2d1fb84a8fde4f8f3035ac65dfca1;; arm64) jdk_arch=aarch64; checksum=d6e8d5e5c101d9319a995405de1e800b02f718ac226dccd8c3b11dae46479ae0;; *) exit 1;; esac \
    && curl --fail --location --proto '=https' --tlsv1.2 -o /jdk.tar.gz "https://github.com/adoptium/temurin27-binaries/releases/download/jdk-27.0.1.0.2%2B1/OpenJDK27U-jdk_${jdk_arch}_alpine-linux_hotspot_27.0.1.0.2_1.tar.gz" \
    && echo "$checksum  /jdk.tar.gz" | sha256sum -c - \
    && rm -rf /opt/java/openjdk && mkdir -p /opt/java/openjdk \
    && tar --no-same-owner -xzf /jdk.tar.gz -C /opt/java/openjdk --strip-components=1 && rm /jdk.tar.gz \
    && java -version 2>&1 | grep -F '27.0.1.0.2+1'
COPY scripts/security/TrustStorePatch.java /security/TrustStorePatch.java
RUN java /security/TrustStorePatch.java "$JAVA_HOME/lib/security/cacerts" && rm -rf /security
FROM patched-jdk AS build
WORKDIR /workspace
ENV GRADLE_USER_HOME=/workspace/.gradle-home
COPY gradlew gradlew.bat build.gradle settings.gradle gradle.properties gradle.lockfile ./
COPY gradle ./gradle
RUN echo '3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98  gradle/wrapper/gradle-wrapper.jar' | sha256sum -c -
COPY scripts ./scripts
COPY src ./src
COPY licenses ./licenses
RUN --mount=type=cache,id=moapan-gradle-9.8.2,target=/workspace/.gradle-home,sharing=locked mkdir -p /workspace/.local/tmp && ./gradlew --no-daemon -Djava.io.tmpdir=/workspace/.local/tmp bootJar && sh scripts/security/fetch-thirdparty-sources.sh
FROM patched-jdk AS runtime
WORKDIR /app
RUN addgroup -g 10001 moapan && adduser -D -H -u 10001 -G moapan moapan
COPY --from=build --chown=10001:10001 /workspace/build/libs/moapan-1.0.0.jar /app/moapan.jar
COPY THIRD_PARTY_NOTICES.md /opt/moapan/THIRD_PARTY_NOTICES.md
COPY licenses /opt/moapan/licenses
COPY --from=build /workspace/build/third-party-sources /opt/moapan/third-party-sources
USER 10001:10001
ENV MOAPAN_ROLE=web JAVA_TOOL_OPTIONS='-XX:MaxRAMPercentage=70 -Djava.io.tmpdir=/tmp -Duser.timezone=UTC'
ENTRYPOINT ["java","-jar","/app/moapan.jar"]

FROM verified-alpine AS proxy-build
ARG TARGETARCH
RUN apk upgrade --no-cache && apk add --no-cache curl ca-certificates patch \
    && sh /security/patch-os-trust.sh && apk del openssl && rm -rf /security
RUN case "$TARGETARCH" in amd64) checksum=65efbc80fdc1fa2cd2631cad5b481777a68e8725f34e89f50fc6ac714e379b40;; arm64) checksum=a06e99309dbcbdca1a8ccd2282c3ee146e55930886e26b4f1193269587165f49;; *) exit 1;; esac \
    && curl --fail --location --proto '=https' --tlsv1.2 -o /go.tar.gz "https://go.dev/dl/go1.27.3.linux-${TARGETARCH}.tar.gz" \
    && echo "$checksum  /go.tar.gz" | sha256sum -c - && tar -xzf /go.tar.gz -C /usr/local && rm /go.tar.gz
ENV PATH="/usr/local/go/bin:${PATH}" GOTOOLCHAIN=local CGO_ENABLED=0
WORKDIR /proxy
COPY scripts/deploy/proxy/ ./
COPY scripts/security/CollectModuleLicenses.go /license-tools/collect.go
RUN --mount=type=cache,id=moapan-go-mod,target=/root/go/pkg/mod \
    go mod download github.com/go-jose/go-jose/v3 \
    && cp -R /root/go/pkg/mod/github.com/go-jose/go-jose/v3@v3.0.5 /proxy/patched-jose \
    && chmod -R u+w /proxy/patched-jose \
    && cd /proxy/patched-jose && sha256sum -c /proxy/patches/original.sha256 \
    && patch --fuzz=0 -p1 < /proxy/patches/jose-v3-security.patch \
    && sha256sum -c /proxy/patches/modified.sha256 \
    && cp /proxy/patches/jose_security_test.go . \
    && cp /proxy/patches/public_api_test.go . \
    && cp /proxy/patches/json_security_test.go json/ \
    && cp /proxy/patches/cipher_security_test.go cipher/
RUN go mod edit -replace=github.com/go-jose/go-jose/v3=./patched-jose
RUN --mount=type=cache,id=moapan-go-mod,target=/root/go/pkg/mod --mount=type=cache,id=moapan-go-build,target=/root/.cache/go-build \
    go test -mod=readonly -timeout 120s github.com/go-jose/go-jose/v3/...
RUN --mount=type=cache,id=moapan-go-mod,target=/root/go/pkg/mod --mount=type=cache,id=moapan-go-build,target=/root/.cache/go-build go build -mod=readonly -trimpath -o /caddy . \
    && go build -mod=readonly -trimpath -o /gosu github.com/tianon/gosu \
    && go run -mod=readonly /license-tools/collect.go /third-party-notices
FROM postgres:18.6-alpine@sha256:10a5c69a83035ab5239f5620d708520e8b28f5aa1f2b20143e6024532edadfb5 AS database
COPY scripts/security/patch-os-trust.sh /security/patch-os-trust.sh
RUN apk upgrade --no-cache && apk add --no-cache ca-certificates openssl \
    && sh /security/patch-os-trust.sh && apk del openssl && rm -rf /security
COPY --from=proxy-build /gosu /usr/local/bin/gosu
COPY --chmod=0555 scripts/deploy/db-init.sh /docker-entrypoint-initdb.d/10-app-user.sh
COPY --from=proxy-build /third-party-notices /opt/moapan/licenses/go
COPY THIRD_PARTY_NOTICES.md /opt/moapan/THIRD_PARTY_NOTICES.md
FROM verified-alpine AS proxy
RUN apk upgrade --no-cache && apk add --no-cache ca-certificates curl \
    && sh /security/patch-os-trust.sh && apk del openssl && rm -rf /security \
    && mkdir -p /data /config && chown -R 10001:10001 /data /config
COPY --from=proxy-build /caddy /usr/bin/caddy
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=proxy-build /third-party-notices /opt/moapan/licenses/go
COPY licenses/native/caddy /opt/moapan/licenses/caddy
COPY --from=proxy-build /proxy/patched-jose /opt/moapan/security/go-jose-v3-source
COPY scripts/deploy/proxy/patches/README.md /opt/moapan/security/jose-backport.md
COPY scripts/deploy/proxy/patches/*.sha256 scripts/deploy/proxy/patches/*.patch /opt/moapan/security/
COPY THIRD_PARTY_NOTICES.md /opt/moapan/THIRD_PARTY_NOTICES.md
RUN chmod 0755 /etc/caddy && chmod 0444 /etc/caddy/Caddyfile
USER 10001:10001
ENV HOME=/tmp XDG_DATA_HOME=/data XDG_CONFIG_HOME=/config
ENTRYPOINT ["caddy","run","--config","/etc/caddy/Caddyfile","--adapter","caddyfile"]

FROM proxy AS public-proxy
USER root
COPY scripts/deploy/Caddyfile.public /etc/caddy/Caddyfile
RUN chmod 0755 /etc/caddy && chmod 0444 /etc/caddy/Caddyfile
USER 10001:10001
