ARG UBUNTU_IMAGE=ubuntu:26.04@sha256:6a06111c3b0a8600feadb270aec771dc239bf407b25625f93f44ab00e11f4d3c
ARG NODE_IMAGE=node:24.18.0@sha256:5711a0d445a1af54af9589066c646df387d1831a608226f4cd694fc59e745059
FROM ${NODE_IMAGE} AS node-donor
FROM ${UBUNTU_IMAGE} AS os
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get -y upgrade && apt-get install -y --no-install-recommends ca-certificates curl openssl tzdata libstdc++6 zlib1g libatomic1 && rm -rf /var/lib/apt/lists/*
COPY scripts/security/patch-os-trust.sh /security/patch-os-trust.sh
RUN sh /security/patch-os-trust.sh && rm -rf /security
USER 65532:65532
FROM os AS java-download
USER 0:0
ARG TARGETARCH
COPY --from=node-donor /usr/local/bin/node /usr/local/bin/node
COPY scripts/platform/runtime-versions.json /runtime-versions.json
COPY scripts/platform/fetch-java.mjs /fetch-java.mjs
RUN --mount=type=cache,id=damgox2-temurin25-${TARGETARCH},target=/downloads,sharing=locked --mount=type=bind,from=java-dist,source=.,target=/official-java-input,ro node --version | grep '^v24\.' && node /fetch-java.mjs "$TARGETARCH" && rm /fetch-java.mjs
ENV JAVA_HOME=/opt/java/jdk PATH=/opt/java/jdk/bin:$PATH
COPY scripts/security/TrustStorePatch.java /security/TrustStorePatch.java
RUN java -version 2>&1 | grep -F '25.0.5+8' && java /security/TrustStorePatch.java /opt/java/jdk/lib/security/cacerts && java /security/TrustStorePatch.java /opt/java/jre/lib/security/cacerts && rm -rf /security
USER 65532:65532
FROM java-download AS java-build
USER 0:0
RUN apt-get update && apt-get install -y --no-install-recommends unzip git ripgrep && rm -rf /var/lib/apt/lists/*
USER 65532:65532
FROM os AS java-runtime
COPY --from=java-download /opt/java/jre /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk PATH=/opt/java/openjdk/bin:$PATH
RUN java -version 2>&1 | grep -F '25.0.5+8'
FROM os AS native-build
USER 0:0
ARG TARGETARCH
RUN apt-get update && apt-get install -y --no-install-recommends build-essential cmake pkg-config patch && rm -rf /var/lib/apt/lists/*
RUN case "$TARGETARCH" in amd64) checksum=65efbc80fdc1fa2cd2631cad5b481777a68e8725f34e89f50fc6ac714e379b40;; arm64) checksum=a06e99309dbcbdca1a8ccd2282c3ee146e55930886e26b4f1193269587165f49;; *) exit 1;; esac \
 && curl --fail --location --proto '=https' --tlsv1.2 -o /go.tar.gz "https://go.dev/dl/go1.27.3.linux-${TARGETARCH}.tar.gz" \
 && echo "$checksum  /go.tar.gz" | sha256sum -c - && tar -xzf /go.tar.gz -C /usr/local && rm /go.tar.gz
ENV PATH=/usr/local/go/bin:$PATH GOTOOLCHAIN=local GOMAXPROCS=2
USER 65532:65532
