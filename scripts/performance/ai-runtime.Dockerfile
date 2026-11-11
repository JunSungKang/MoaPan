# Reuse the already verified glibc/architecture-specific executable, without rebuilding it.
ARG AI_EXECUTION_IMAGE=damgox2-ai:ubuntu26.04-java25-20261109
FROM ${AI_EXECUTION_IMAGE}
USER 0:0
COPY scripts/security/patch-os-trust.sh /security/patch-os-trust.sh
RUN sh /security/patch-os-trust.sh && rm -rf /security
COPY --chmod=0555 scripts/translation/serve.sh /opt/translation/serve.sh
COPY --chmod=0555 scripts/classification/serve.sh /opt/classification/serve.sh
USER 10001:10001
