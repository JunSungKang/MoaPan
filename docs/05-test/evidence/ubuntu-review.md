# Ubuntu 공급자 재확인

2026-10-28T05:25Z. HTML 페이지 timeout 후 공식 JSON endpoint로 22개 CVE를 모두 조회했다. 아래는 Ubuntu24.04(noble) 상태다. needed/deferred는 수정 완료가 아니며 scanner 경고를 유지한다.

|CVE|공급자 상태|실제 프로젝트 노출 검토|
|---|---|---|
|[CVE-2026-89092](https://ubuntu.com/security/cves/CVE-2026-89092.json)|glibc: needed|ccs mode를 외부 입력으로 지정하지 않으며 nscd 서비스를 실행하지 않음. 기본 C 런타임은 유지.|
|[CVE-2026-18374](https://ubuntu.com/security/cves/CVE-2026-18374.json)|glibc: needed|ccs mode를 외부 입력으로 지정하지 않으며 nscd 서비스를 실행하지 않음. 기본 C 런타임은 유지.|
|[CVE-2026-102474](https://ubuntu.com/security/cves/CVE-2026-102474.json)|dash: deferred|비신뢰 입력을 shell 패턴·printf로 전달하지 않음. 빌드/운영자가 임의 shell 명령을 실행할 경우 잔여 위험.|
|[CVE-2026-102473](https://ubuntu.com/security/cves/CVE-2026-102473.json)|dash: deferred|비신뢰 입력을 shell 패턴·printf로 전달하지 않음. 빌드/운영자가 임의 shell 명령을 실행할 경우 잔여 위험.|
|[CVE-2026-95184](https://ubuntu.com/security/cves/CVE-2026-95184.json)|gnutls28: deferred|앱 HTTPS는 JSSE, Caddy는 Go TLS, healthcheck curl은 OpenSSL 사용. GnuTLS 인증서 검증을 서비스에 노출하지 않음.|
|[CVE-2026-67693](https://ubuntu.com/security/cves/CVE-2026-67693.json)|gnutls28: deferred|앱 HTTPS는 JSSE, Caddy는 Go TLS, healthcheck curl은 OpenSSL 사용. GnuTLS 인증서 검증을 서비스에 노출하지 않음.|
|[CVE-2026-88648](https://ubuntu.com/security/cves/CVE-2026-88648.json)|gnutls28: deferred|앱 HTTPS는 JSSE, Caddy는 Go TLS, healthcheck curl은 OpenSSL 사용. GnuTLS 인증서 검증을 서비스에 노출하지 않음.|
|[CVE-2026-88647](https://ubuntu.com/security/cves/CVE-2026-88647.json)|gnutls28: deferred|앱 HTTPS는 JSSE, Caddy는 Go TLS, healthcheck curl은 OpenSSL 사용. GnuTLS 인증서 검증을 서비스에 노출하지 않음.|
|[CVE-2026-95210](https://ubuntu.com/security/cves/CVE-2026-95210.json)|gnutls28: deferred|앱 HTTPS는 JSSE, Caddy는 Go TLS, healthcheck curl은 OpenSSL 사용. GnuTLS 인증서 검증을 서비스에 노출하지 않음.|
|[CVE-2026-95209](https://ubuntu.com/security/cves/CVE-2026-95209.json)|gnutls28: deferred|앱 HTTPS는 JSSE, Caddy는 Go TLS, healthcheck curl은 OpenSSL 사용. GnuTLS 인증서 검증을 서비스에 노출하지 않음.|
|[CVE-2026-103111](https://ubuntu.com/security/cves/CVE-2026-103111.json)|pcre2: needed|Java/Go 정규식 사용; 외부 native PCRE2 패턴 실행 API 없음. 32비트 전용 항목은 amd64/arm64 대상과 불일치.|
|[CVE-2026-86145](https://ubuntu.com/security/cves/CVE-2026-86145.json)|pcre2: needed|Java/Go 정규식 사용; 외부 native PCRE2 패턴 실행 API 없음. 32비트 전용 항목은 amd64/arm64 대상과 불일치.|
|[CVE-2026-89158](https://ubuntu.com/security/cves/CVE-2026-89158.json)|pcre2: needed|Java/Go 정규식 사용; 외부 native PCRE2 패턴 실행 API 없음. 32비트 전용 항목은 amd64/arm64 대상과 불일치.|
|[CVE-2026-89157](https://ubuntu.com/security/cves/CVE-2026-89157.json)|pcre2: needed|Java/Go 정규식 사용; 외부 native PCRE2 패턴 실행 API 없음. 32비트 전용 항목은 amd64/arm64 대상과 불일치.|
|[CVE-2026-89156](https://ubuntu.com/security/cves/CVE-2026-89156.json)|pcre2: needed|Java/Go 정규식 사용; 외부 native PCRE2 패턴 실행 API 없음. 32비트 전용 항목은 amd64/arm64 대상과 불일치.|
|[CVE-2026-89160](https://ubuntu.com/security/cves/CVE-2026-89160.json)|pcre2: needed|Java/Go 정규식 사용; 외부 native PCRE2 패턴 실행 API 없음. 32비트 전용 항목은 amd64/arm64 대상과 불일치.|
|[CVE-2026-89161](https://ubuntu.com/security/cves/CVE-2026-89161.json)|pcre2: needed|Java/Go 정규식 사용; 외부 native PCRE2 패턴 실행 API 없음. 32비트 전용 항목은 amd64/arm64 대상과 불일치.|
|[CVE-2024-56433](https://ubuntu.com/security/cves/CVE-2024-56433.json)|shadow: deferred|원격 OS 로그인·NFS home·newuidmap 기능을 서비스에 제공하지 않음.|
|[CVE-2026-40228](https://ubuntu.com/security/cves/CVE-2026-40228.json)|systemd: deferred|컨테이너에서 journald/ForwardToWall 서비스를 실행하지 않음.|
|[CVE-2026-107161](https://ubuntu.com/security/cves/CVE-2026-107161.json)|cyrus-sasl2: deferred|서비스는 DIGEST-MD5/HTTP Digest SASL 인증을 사용하지 않음.|
|[CVE-2026-18477](https://ubuntu.com/security/cves/CVE-2026-18477.json)|tar: needed|기사/사용자 업로드 archive 추출 없음. 빌드의 공식 checksum 검증된 tar만 사용. 백업은 PostgreSQL dump/restore이며 GNU incremental tar가 아님.|
|[CVE-2026-18508](https://ubuntu.com/security/cves/CVE-2026-18508.json)|tar: needed|기사/사용자 업로드 archive 추출 없음. 빌드의 공식 checksum 검증된 tar만 사용. 백업은 PostgreSQL dump/restore이며 GNU incremental tar가 아님.|

후속 조치: 공급자 수정 패키지 제공 시 업데이트·재빌드·전체 산출물 재검사. 구성·사용 경로가 바뀌면 이 노출 판정을 재검토한다. 이는 base OS 전체가 취약점 없음이라는 판정이 아니다. 현재 알려진 공격 전제의 외부 노출은 찾지 못했지만 미공개 경로까지 보장하지 않는다.
