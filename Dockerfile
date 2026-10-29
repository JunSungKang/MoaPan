# The current Temurin container tag lags the chosen PSU; replace the complete JDK from verified vendor release.
FROM ubuntu:24.04@sha256:24737cef17f4e22b1977df7d9a05e77c4e28b7da5b8911f8d8ccda136bf3573c AS patched-jdk
ARG TARGETARCH
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates libstdc++6 zlib1g && rm -rf /var/lib/apt/lists/*
RUN case "$TARGETARCH" in amd64) jdk_arch=x64; checksum=30cb129f6cf299c852cec0feca9ea2a596d2ee9f26d8605d3a253951f6c8b20f;; arm64) jdk_arch=aarch64; checksum=5ddacf925244a00b60cac828b61ff2f67a68cd32b8dfe5cee1405e333b4589e5;; *) exit 1;; esac \
    && curl --fail --location --proto '=https' --tlsv1.2 -o /jdk.tar.gz "https://github.com/adoptium/temurin27-binaries/releases/download/jdk-27.0.1.0.2%2B1/OpenJDK27U-jdk_${jdk_arch}_linux_hotspot_27.0.1.0.2_1.tar.gz" \
    && echo "$checksum  /jdk.tar.gz" | sha256sum -c - \
    && rm -rf /opt/java/openjdk && mkdir -p /opt/java/openjdk \
    && tar --no-same-owner -xzf /jdk.tar.gz -C /opt/java/openjdk --strip-components=1 && rm /jdk.tar.gz \
    && java -version 2>&1 | grep -F '27.0.1.0.2+1'
FROM patched-jdk AS build
WORKDIR /workspace
ENV GRADLE_USER_HOME=/workspace/.gradle-home
COPY gradlew gradlew.bat build.gradle settings.gradle gradle.properties gradle.lockfile ./
COPY gradle ./gradle
RUN echo '3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98  gradle/wrapper/gradle-wrapper.jar' | sha256sum -c -
COPY scripts ./scripts
COPY src ./src
RUN --mount=type=cache,id=moapan-gradle-9.8.2,target=/workspace/.gradle-home,sharing=locked mkdir -p /workspace/.local/tmp && ./gradlew --no-daemon -Djava.io.tmpdir=/workspace/.local/tmp bootJar
FROM patched-jdk AS runtime
WORKDIR /app
RUN groupadd --gid 10001 moapan && useradd --uid 10001 --gid 10001 --no-create-home moapan
COPY --from=build --chown=10001:10001 /workspace/build/libs/moapan-1.0.0.jar /app/moapan.jar
USER 10001:10001
ENV MOAPAN_ROLE=web JAVA_TOOL_OPTIONS='-XX:MaxRAMPercentage=70 -Djava.io.tmpdir=/tmp -Duser.timezone=UTC'
ENTRYPOINT ["java","-jar","/app/moapan.jar"]

FROM ubuntu:24.04@sha256:24737cef17f4e22b1977df7d9a05e77c4e28b7da5b8911f8d8ccda136bf3573c AS proxy-build
ARG TARGETARCH
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && rm -rf /var/lib/apt/lists/*
RUN case "$TARGETARCH" in amd64) checksum=65efbc80fdc1fa2cd2631cad5b481777a68e8725f34e89f50fc6ac714e379b40;; arm64) checksum=a06e99309dbcbdca1a8ccd2282c3ee146e55930886e26b4f1193269587165f49;; *) exit 1;; esac \
    && curl --fail --location --proto '=https' --tlsv1.2 -o /go.tar.gz "https://go.dev/dl/go1.27.3.linux-${TARGETARCH}.tar.gz" \
    && echo "$checksum  /go.tar.gz" | sha256sum -c - && tar -xzf /go.tar.gz -C /usr/local && rm /go.tar.gz
ENV PATH="/usr/local/go/bin:${PATH}" GOTOOLCHAIN=local CGO_ENABLED=0
WORKDIR /proxy
COPY scripts/deploy/proxy/ ./
RUN --mount=type=cache,id=moapan-go-mod,target=/root/go/pkg/mod --mount=type=cache,id=moapan-go-build,target=/root/.cache/go-build go build -mod=readonly -trimpath -o /caddy . \
    && go build -mod=readonly -trimpath -o /gosu github.com/tianon/gosu
FROM postgres:18.6-alpine@sha256:10a5c69a83035ab5239f5620d708520e8b28f5aa1f2b20143e6024532edadfb5 AS database
RUN apk upgrade --no-cache
COPY --from=proxy-build /gosu /usr/local/bin/gosu
COPY --chmod=0555 scripts/deploy/db-init.sh /docker-entrypoint-initdb.d/10-app-user.sh
FROM ubuntu:24.04@sha256:24737cef17f4e22b1977df7d9a05e77c4e28b7da5b8911f8d8ccda136bf3573c AS proxy
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /data /config && chown -R 10001:10001 /data /config
COPY --from=proxy-build /caddy /usr/bin/caddy
COPY Caddyfile /etc/caddy/Caddyfile
RUN chmod 0755 /etc/caddy && chmod 0444 /etc/caddy/Caddyfile
USER 10001:10001
ENV HOME=/tmp XDG_DATA_HOME=/data XDG_CONFIG_HOME=/config
ENTRYPOINT ["caddy","run","--config","/etc/caddy/Caddyfile","--adapter","caddyfile"]

FROM proxy AS public-proxy
USER root
COPY scripts/deploy/Caddyfile.public /etc/caddy/Caddyfile
RUN chmod 0755 /etc/caddy && chmod 0444 /etc/caddy/Caddyfile
USER 10001:10001
