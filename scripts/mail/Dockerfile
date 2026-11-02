# Audited MailBox SMTP source, with maintained minimal OS and patched Postfix.
FROM alpine:3.24.3@sha256:be2656061b536c1883c4034790e21d0a09c5aaaf51968b89368a3c0371ea2c9a
RUN apk upgrade --no-cache && apk add --no-cache ca-certificates openssl bash coreutils postfix \
 && mkdir -p /config && (getent group vmail >/dev/null || addgroup -g 5000 vmail) && id -u vmail >/dev/null
COPY patch-os-trust.sh /security/patch-os-trust.sh
RUN sh /security/patch-os-trust.sh && rm -rf /security
COPY main.cf /etc/postfix/main.cf
COPY upstream-entrypoint.sh /usr/local/bin/mailbox-smtp-receiver-entrypoint
COPY secure-entrypoint.sh /usr/local/bin/secure-entrypoint
COPY virtual-mailbox-accounts /config/virtual-mailbox-accounts
RUN chmod 0755 /usr/local/bin/mailbox-smtp-receiver-entrypoint /usr/local/bin/secure-entrypoint \
 && mkdir -p /config/postfix /config/spool && cp -a /etc/postfix/. /config/postfix/ && cp -a /var/spool/postfix/. /config/spool/
EXPOSE 25
ENTRYPOINT ["secure-entrypoint"]
