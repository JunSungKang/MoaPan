# 수동 실행·배포·백업

현재 Ubuntu26.04·Temurin25.0.5+8 전환의 설정·실행 결과는 [통합 운영 인계](platform-ubuntu25.md)와 [검증서](../05-test/platform-ubuntu25.md)를 우선한다. 아래 Java27/Alpine·이전 Compose 명령은 과거 실행 기록이며 새 서비스에 재사용하지 않는다.



> 문서번호: MP-DOC-016 · 기준: 저장소 현재 리비전 · 상태: 유지관리 · 작성: 개발 담당 · 검토: 독립 리뷰 담당

사용자 최신 완료 기준: 로컬 Mac에서 실행하고 휴대폰 데이터망에서 접속 성공. https://175.195.62.165/ 에서 사용자 확인을 받았다. 별도 호스팅 배포는 범위에서 제외. CI/CD·공개 터널·유료 자원 생성은 없다.

기본 공개 모드 예시(메일·모델을 함께 유지하는 현재 재시작은 아래 최신 운영 전환 절을 사용): .env에 PUBLIC_BASE_URL=https://175.195.62.165, DAMGOX2_PUBLIC_HOST=175.195.62.165, COOKIE_SECURE=true를 설정한다. 비밀값은 기존 .env에 유지한다. `docker compose -f compose.yaml -f scripts/deploy/compose-public.yaml up -d --no-build --wait --wait-timeout 90`으로 실행한다. 기본 Compose만 실행하면 로컬 모드 설정이 적용되므로 공개 모드에서는 반드시 두 파일을 지정한다. Docker의 외부 privileged port forwarding은 사용자 승인 후 활성화했다.

공개 proxy는 80→8081(HTTPS redirect/ACME),443→8443(TLS),127.0.0.1:8080(health only)만 노출한다. DB와 web은 호스트 포트를 열지 않는다. Let’s Encrypt shortlived IP 인증서를 Caddy가 named volume에 보관·자동 갱신한다. Mac과 Docker가 실행 중이고 해당 IP와 포트가 유지돼야 접속·갱신 가능하다. 이전 측정 인증서 만료는2026-11-03T21:11:17Z였으며, 최신 실서비스 검증에서는 자동 갱신된 인증서 만료2026-11-10T14:34:47Z를 확인했다; IP가 바뀌면 .env·주소·인증서 재검증이 필요하다.

공개 접속 중지: `docker compose -f compose.yaml -f scripts/deploy/compose-public.yaml stop proxy`. 볼륨 삭제는 필요 없다. 다시 로컬 HTTP로 전환하려면 PUBLIC_BASE_URL=http://localhost:8080, COOKIE_SECURE=false 후 기본 Compose로 재생성한다.

현재 읽기 전용 검증: `MOAPAN_AUDIT_BASE_URL=https://175.195.62.165 node scripts/test/site-http-audit.mjs`. 과거 NASA/NOAA 등록형 실행기는 MP-DOC-034에서 폐기했다. 출처 구성을 바꾸는 실제 수집 시험은 승인된 격리 환경에서만 실행한다. TLS 검증 우회는 하지 않는다.

```sh
cp .env.example .env
# 비공개 편집기로 독립 random secret을 설정하고 .env 권한을 0600으로 제한
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

접속은 http://localhost:8080. 합성 계정만 사용한다. migrate exit0 후 web·worker가 시작하며 worker/migrate/admin-cli는 HTTP 포트를 열지 않는다. proxy만127.0.0.1:8080을 공개한다. DB·web은 내부 네트워크에서만 연결한다. 초기 앱 실행은 linux/amd64 emulation이었으며 현재는 공식 Temurin Linux arm64 빌드로 전환했다. 최신 런타임은 아래 운영 전환 절과 버전 기록을 따른다.

관리자 부여: `docker compose run --rm -e DAMGOX2_ROLE=admin-cli web`에서 기존 아이디를 stdin으로 입력한다. 초기 관리자 비밀번호는 없다. DB runtime 역할 damgox2는 superuser/createdb/createrole 권한이 없으며 migration 자격은 분리한다.

최종 검증 이미지 식별값은 `evidence/verification.json`에 기록한다. 이미지 tag를 다른 빌드로 덮으면 재검사해야 한다. 외부 배포 시 보안 DB를24시간 내 다시 동기화하고 같은 digest만 수동 승격한다. 현재 로컬 Caddyfile은 HTTP 전용이다. 승인 도메인 Caddy HTTPS·HSTS·PUBLIC_BASE_URL·Secure 쿠키·신뢰한 프록시 전달 정책을 함께 설정·검증하기 전 외부에 공개하지 않는다.

## 영속성과 복원

`database_v1` named volume을 유지한다. 일반 재시작에 `docker compose down -v`를 사용하지 않는다. Debian→Alpine 전환은 libc/locale 차이 때문에 구 볼륨을 직접 연결하지 않고 age 암호화 SQL을 새 볼륨에 복원했다. 구 볼륨은 자동 삭제하지 않았다. 실제 기사20건과 FK·Flyway 이력이 유지됨을 확인했다. 새 DB 초기화는 C.UTF-8이다.

```sh
sh scripts/backup/backup.sh <age-public-recipient>
sh scripts/backup/restore-check.sh <workspace-encrypted-backup> <workspace-private-identity-file>
```

`sh scripts/backup/build-backup-tools.sh`로 age 1.3.2 보안 backport 도구를 준비한다. 검증한 Go 1.27.3과 고정 모듈로 보안 수정이 반영된 도구를 빌드하며 버전·검사 근거는 VERSIONS.md에 있다. pg_dump를 FIFO에서 즉시 암호화하며 평문 dump를 파일에 남기지 않는다. dump와 암호화 각각 실패를 확인한다. workspace .local/backups만 허용하고 umask077을 적용한다. identity의 값은 CLI 인자로 전달하지 않는다. 백업·키를 Git이나 공개 보고서에 포함하지 않는다. 키는 백업과 분리한 승인된 비밀 보관소에 보관해야 한다.

격리 복원은 별도 DB를 생성해 복호화·SQL을 검증하고 모든 복원 계정을 비활성화·세션을 제거한 뒤 그 격리 DB만 삭제한다. 운영 복원 전 공개를 중지하고 이 정책을 적용하여 탈퇴 계정이 자동 부활하지 않게 한다. 재활성화는 별도 운영 확인을 거친다. 실제 검증에서 articles=20, active_accounts=0, sessions=0이었다. 검증용 키·암호화 백업은 검증 후 정리한다. 수동 백업 보관기간은7일이며 자동 백업은 구성하지 않았다.

앱 rollback은 이전 앱과 현재 schema의 호환성을 확인한 경우에만 수행한다. 파괴적 DB rollback이나 취약한 이미지로의 무조건 복귀는 하지 않는다. 보안 수정 실패 시 외부 공개 중지·격리를 우선한다. 외부 배포·백업 저장소 접근통제·사업자 로그 정책은 실제 운영 환경이 정해진 뒤 확인해야 한다.

엔진/서비스 준비를 제한 시간 안에 확인하려면 이미지 빌드 후 `node scripts/deploy/local-start.mjs`를 실행한다. Engine 사전 검사10초, Compose health90초, 전체 시작120초 상한이며 실패를 성공으로 처리하지 않는다. Docker 시작 장애 기록은 docs/05-test/bugs.md를 참고한다.

Caddyfile과 scripts/deploy/db-init.sh는 이미지에 포함된다. 수정 후 proxy/db 이미지를 재빌드하고 다시 보안 검사한다. 기본 Compose는 호스트 파일 bind mount를 사용하지 않으며 기존 named volume을 유지한다.

## 선택적 무료 로컬 번역 (macOS arm64)
현재 운영 번역은 Linux CPU 컨테이너 방식으로 전환했다. 준비·실행·서버 이관·자동 복구 절차는 [LINUX_TRANSLATION.md](linux-translation.md)를 따른다. host.docker.internal이나 Mac native 모델 시작은 필요하지 않다.

선택 모델 Index Translate 2B Q8_0와 glossary-v1 유지. 과거 Mac native 번역 실행기는 제거했으며 현재 운영은 Compose/Helm 경로만 사용한다. 과거 비교 측정 증빙은 보존한다. Docker daemon이 시작돼야 restart 정책이 동작한다.

## 광고 등록 방법
1. 관리자 계정으로 로그인하고 관리 → 광고 관리에서 직접 링크 배너를 선택한다.
2. 광고 문구와 광고주가 제공한 HTTPS 목적지 링크를 입력한다. 이미 서버에 준비한 이미지가 있을 때만 이미지 경로를 입력한다. 이미지 업로드 기능은 없으며 문구만으로도 배너가 표시된다.
3. 미리보기로 확인하고 지면에 광고 표시를 선택해 저장한다. 지면을 다시 열면 우측 상단에 반영된다. 표시 해제 후 저장하면 광고 문의 자리로 돌아간다.
4. CPC 계약 링크도 같은 목적지 필드에 입력한다. 담고담고는 클릭을 기록하지 않으므로 집계·수익·정산은 계약한 공급자에서 확인한다.
5. 애드센스는 연결 정보로 전환해 ca-pub-로 시작하는 공개 게시자 ID와 광고 단위 ID를 저장한다. 현재는 준비 상태이며 실제 광고 요청과 수익은 발생하지 않는다. 승인 계정·사이트·광고 단위와 동의 관리/CSP 설계를 확보한 뒤 별도 송출 연결이 필요하다.
공식 근거(2026-10-30 확인): [광고 단위 생성](https://support.google.com/adsense/answer/9274025), [strict CSP 통합](https://support.google.com/adsense/answer/16283098), [동의 관리](https://support.google.com/adsense/answer/7670013). 계정 승인/게시자 ID가 없어 실광고 송출·수익 검증은 하지 않았다. V12 마이그레이션 추가 및 Helm migrationVersion=12.

뉴스레터: /newsletter.html은 회원용 목록·본문, /newsletter-editor.html은 관리자 원고 편집이다. 관리자 계정 메뉴의 뉴스레터 관리에서 제목·소개·주제를 입력하고 문단/소제목/강조문/그림/참고 자료를 조합한다. 뉴스·논문 찾기로 기존 뉴스의 제목과 원문 링크만 참고 자료에 연결할 수 있다. PNG/JPEG/WebP 5MB 이하 파일을 브라우저에서 최대1200px JPEG로 압축하며 그림당 약220KiB, 원고 API 전체1MiB를 제한한다. 초안 저장은 발간본에 영향을 주지 않으며 발간 버튼과 확인 후에만 회원에게 보인다. 발간 취소 시 초안은 유지한다. 그림 대체 설명과 출처는 관리자가 작성하고 이용 권리를 확인한다. 자동 이메일 발송·이메일 수집·AI 원고 생성은 없다. UI 번역과 원고 번역은 구분하며 원고는 작성 언어 그대로다. V13 및 Helm migrationVersion13을 적용한다.

V14 전환: newsletters에 nullable dossier와최초발간/개정필드를추가하며기존본문/그림/회원/기사삭제없음. migrate역할을먼저한번실행한후동일검증이미지web/worker를재생성한다. 이전V13앱은추가컬럼을사용하지않으므로스키마를파괴적으로되돌리지않는다. 기존발간본은구조화근거·권리재검토전에는재게재도구가없고본문열람은유지한다. Helm migrationVersion=14이며실클러스터배포는검증범위밖이다. 백업은기존DB전체dump/암호화/접근통제절차로새컬럼도포함한다. 실제고객평가/가격/결제를운영중이라고표시하지않는다.

## 서버 캐시 V22 실제 반영
승인된 기존 환경에서 compose.yaml + scripts/deploy/compose-public.yaml + scripts/deploy/compose-translation.yaml + scripts/deploy/compose-classification.yaml 네 파일로 실행했다. app/worker 중지→암호화 백업/격리 복원→검사한 동일 앱 image를 migrate로 V17~22 적용→web/worker 재생성→검사한 proxy로 교체했다. DB/모델 이미지·볼륨 유지,300기사·2계정·1발간물 ID집합 일치. web/worker JAR113f46dcf6054f4fc9d12b368ecbdfe23894750f5e8d1d7446b7d555062397a3 일치. 승인된 기존 HTTPS와 API·자산304·live캐시를 확인했고 합성계정은 삭제했다. 이번 실행에서 별도 새 셀룰러 측정이나 새 배포 환경 생성은 하지 않았다. Helm migrationVersion은 최신22로 맞춰 wait-schema가 캐시 migration 완료를 확인한다. chart lint/template/config scan만 수행했고 실제 Kubernetes 설치는 하지 않았다. 실행 명령/원본 보고서 해시는 [검증 증빙](../05-test/evidence/cache-service-delivery-20261101.json)을 따른다.


## PageSpeed 후속 서비스 반영 검증 — 2026-11-02
기존4개 Compose 설정을 모두 사용해 web/worker를 검증 runtime sha256:fba510fc15d13e3a078a6a27185d763a9046badb2aa7294e0790450e117db7f2로, proxy를 sha256:453e34b953b74d07e425de9383bf2d7e3190deba1fe828eb34b036c69874074c로 교체했다. 나머지DB/모델/볼륨 보존,6서비스정상/heartbeat·300기사/2계정/1발간물/V22·ID해시일치. 정상TLS검증을 유지한 실제HTTPS·HEAD·API접근경계·합성가입/로그인/탈퇴를 확인했다. 외부 PSI는 양쪽4분야100점. 새 유료자원/도메인/터널/CI/CD를 추가하지 않았다. builder/scan/truststore/실행JAR증빙은 [최종 검증서](../05-test/pagespeed-desktop.md)에 있다.

## 이전 운영 전환 — 2026-11-04 웹 품질·외부 MailBox

이 절은 역사적 기록이다. 명칭 이관 후에는 아래 2026-11-06 절을 사용하며, 이전 이미지/환경 변수로 재시작하지 않는다. 최신 사용자가 서비스 교체와 develop 병합, 별도 운영 중인 MailBox 연동을 승인했다. 내장 SMTP 서버는 되살리지 않으며 MailBox 프로젝트·컨테이너는 수정/재시작하지 않았다.

- 앱 runtime: `sha256:d1e49e6d6c1010acc7230be1d7a9247bccb5c2422c78675ebd246c76ac848e5e`, JAR SHA256 `e03b4ef23d680c45528f2804af32c2fcf1fec73a1a530780f7c1721c1138f998`. Linux arm64, Temurin27.0.1.0.2+1, UID10001:10001. 빌드/스캔한 바이트 그대로 web/worker에 적용했다.
- V24 암호화 백업을 격리 DB에 복원하고 복원 계정 비활성화/세션 제거→V25→구/신 앱 JPA 기동을 검증한 뒤 그 격리 DB를 삭제했다. 웹/worker 중지 후 추가 암호화 백업을 만들고 운영 V25를 적용했다. 모든 기존 테이블의 건수와 행 SHA256 집계가 전환 전후 일치했다. 기존 계정의 이메일을 추정해 채우지 않는다.
- DB/proxy/번역·분류 모델의 container ID·시작시각·이미지가 그대로이며 원래 볼륨·키를 유지했다. 웹/worker만 재생성했다. HTTP 진입 포트를 추가하지 않았다.
- 외부 MailBox의 현재 SMTP 이미지 `sha256:a2392bb2ab623efe485b6a3e9abc956aebc3930130b1350d73f8fe8a1d1df1ad`의 취약점/secret 검사 검출0. 이는 과거 보류된 이미지와 다르다. 별도 SMTP master의 root 관찰은 외부 운영 범위이며 본 프로젝트가 비루트로 바꾸거나 예외 승인한 것으로 표시하지 않는다. 앱과 worker는 계속 비루트 SMTP 클라이언트다.
- 기존 `mailbox_default` 네트워크의 `mailbox-smtp-receiver:2587`에 필수 STARTTLS·SMTP AUTH로 연결한다. 서버 SAN의 실제 호스트와 운영자 CA를 검증하며 TLS/호스트 검증을 해제하지 않는다. CA 공개 인증서와 SMTP 비밀번호는 파일 secret, 이메일 데이터 키는 기존 파일 그대로 사용한다. web은 backend에만, worker만 SMTP/모델 네트워크에 참가한다.

현재 인스턴스의 비공개 `mail.env`에는 연결 정보와 파일 경로만 있으며 SMTP 비밀번호는 별도 `.local/mail/external/smtp-password`에 있다. 기존 `.env`의 값은 바꾸지 않았다. `verified-app.yaml`은 위 세 역할의 동일 이미지ID만 지정한다. 이 파일들은 이 호스트의 운영 설정으로 Git에 포함하지 않는다. 동일한 검증 이미지가 로컬 Docker에 존재할 때 재시작 명령은 다음과 같다.

```sh
docker compose --env-file .env \
  --env-file .local/deploy-web-quality-20261104/mail.env \
  -f compose.yaml -f scripts/deploy/compose-public.yaml \
  -f scripts/deploy/compose-translation.yaml -f scripts/deploy/compose-classification.yaml \
  -f scripts/deploy/compose-mail-external.yaml -f scripts/deploy/compose-mail-auth.yaml \
  -f .local/deploy-web-quality-20261104/verified-app.yaml \
  up -d --no-deps --no-build --pull never --wait --wait-timeout 100 web worker
```

새 호스트는 `.env.example`의 비밀값 없는 계약과 두 SMTP overlay로 각 운영자의 연결/인증서/비밀파일을 준비한다. 이 호스트의 private 파일이나 키를 저장소로 복사하지 않는다. 스키마 변경 시 동일 이미지로 migrate 역할을 선행하며 새 이미지·설정 변경은 검사 후 반영한다. MailBox의 가용성·인증서 유효기간/갱신·발신 평판·인터넷 외부 수신자 전달은 별도 운영 책임이다. 운영자 MailBox 내 실제 수신 검증을 Gmail 등 모든 외부 메일함 전달 보장으로 확대하지 않는다.

자동 백업은 추가하지 않았다. 이번 운영 전환용 age 암호화 백업과 복호화 키는 Git 제외 workspace 안의 제한된 경로에 분리 파일로 보존하며 수동 보관기간7일을 따른다. 동일 호스트 보관은 독립된 오프호스트 재해복구 보관소를 대신하지 않는다. 운영 이메일 데이터 키도 반드시 별도 보관해야 한다.

## 현재 운영 전환 — 2026-11-06 damgox2 명칭 이관

앞선 운영 기록의 명령과 이미지 식별값을 대체한다. 기존 데이터·모델·인증서 8개 볼륨을 파일 지문 검증 후 새 damgox2 볼륨으로 이관했다. DB/역할 이름은 damgox2/damgox2_migrate이며 데이터·권한·비밀번호·암호화 키는 유지한다. .env의 MOAPAN_SECRET/MOAPAN_PUBLIC_HOST는 같은 값을 가진 DAMGOX2_SECRET/DAMGOX2_PUBLIC_HOST 키로 변경했다. 이 호스트는 다음 모든 파일을 함께 사용한다.

```sh
docker compose --env-file .env \
  --env-file .local/mail/external/connection.env \
  -f compose.yaml -f scripts/deploy/compose-public.yaml \
  -f scripts/deploy/compose-translation.yaml -f scripts/deploy/compose-classification.yaml \
  -f scripts/deploy/compose-mail-external.yaml -f scripts/deploy/compose-mail-auth.yaml \
  -f .local/deploy-damgox2/verified-images.yaml \
  up -d --no-build --pull never --wait --wait-timeout 180
```

verified-images.yaml은 검사한 로컬 이미지 ID 5개를 9개 서비스/일회 작업에 고정한다. 실제 로컬 ID는 [인프라 검증서](../05-test/infrastructure-naming.md)에 기록한다. 로컬 이미지 ID는 registry digest가 아니며 다른 서버에서 재사용하려면 별도 빌드/검증 또는 승인 registry digest가 필요하다. 공개 주소와 SMTP 연결/비밀 파일은 기존과 같다. 다른 프로젝트/MailBox를 시작하거나 재시작하지 않는다.

구 이름 볼륨을 가진 다른 호스트는 새 이름으로 up을 먼저 실행하면 안 된다. [데이터 보존 절차](../03-design/infrastructure-naming.md)로 중지·암호화 백업/복원·복사 지문·DB/역할 RENAME을 먼저 수행한다. Helm은 scripts/deploy/helm/damgox2로 lint/template 검증했으며 실제 클러스터 이관은 별도다. 기존 Kubernetes release/PVC를 단순 rename으로 연결할 수 있다고 가정하지 않는다.
