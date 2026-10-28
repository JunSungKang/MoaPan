# 결정 기록

- 2026-10-28: 사용자가 지정한 Sprint Delivery 채택. 제품 결과별 2뎁스 계획은 PLAN.md.
- 최신 사용자 지시 우선: CI/CD·GitHub Actions·스케줄·업데이트 봇을 생성하거나 실행하지 않는다. 로컬 명령으로 동일 검증을 수행한다. requiredChecks 빈 배열은 검사 성공의 증거가 아니다.
- 서버는 계정·세션·인증과 quota 카운터만 회원 관련 정보로 저장한다. 관심사·검색·클릭은 서버 API를 만들지 않는다.
- 사용자 지정 branch codex/moapan-v1, 현재 실제 base main. 계약 검증기의 promotion 모드와 merge method merge를 사용하되 main merge는 실행하지 않는다.
- 수집 요청은 HTTPS·443만, 동일 호스트 redirect 최대 3회, DNS를 한 번 검증한 주소로 연결한다. 압축 응답은 거부하고 identity만 수락한다. 범용 HTML 수집과 JSON 임의 표현식은 지원하지 않는다.
- 외부 AI·번역·이미지·본문 저장 기능은 V1 기본 경로에서 비활성. 지원하지 않는 동작을 요청하면 오류로 알린다.
- 로그인 시 해시가 포함된 UserDetails를 세션에 저장하지 않고 UUID·권한만 저장한다. 비밀번호 변경은 모든 세션을 종료해 재로그인한다.
- 별도 임시 인프라·터널은 만들지 않는다. 제공된 외부 환경이 없어 외부 HTTPS는 미검증이다.

- 2026-10-28: Caddy 2.11.7 공식 이미지의 Go 1.26.8/x/net 0.59 및 Alpine 패키지에서 HIGH 발견. Caddy 표준 모듈을 Go 1.27.3, x/net 0.60.0으로 재빌드한다. go.mod/go.sum을 저장하고 readonly module build와 공식 Go tar SHA256을 검증한다. 제품 Java 스택 변경이 아닌 인프라 보안 수정이다.
- PostgreSQL 공식 Debian 이미지의 OS·gosu 취약점 때문에 PostgreSQL 18.6 Alpine 공식 digest + apk 보안 업데이트 + 같은 Go 1.27.3으로 재빌드한 gosu 1.19를 선택했다. libc/locale 차이를 고려해 기존 데이터 볼륨을 새 베이스에 직접 연결하지 않고 암호화 dump를 새 database_v1 볼륨에 복원했다. 원래 볼륨은 자동 삭제하지 않는다.
- 요약 허용 철회는 수동 잠금 여부와 관계없이 공개 DTO에서 즉시 요약을 제외한다. 저장된 편집 결과를 삭제하지 않고 출처 권리로 출력 여부를 결정한다.
- Trivy filesystem 모드가 JAR 분석을 생략하는 사실을 확인하여 실제 bootJar는 rootfs JAR analyzer로 검사한다. 인식 패키지 0개·빈 SBOM·24시간 초과 DB 동기화는 실패로 처리한다.
- 보안 인증 식별자 테이블은 신규 bucket 생성 시 PostgreSQL advisory lock으로10000행 용량을 원자적으로 제한한다. 기존 bucket 갱신은 허용하고 만료 행을 정리하며, 용량·DB 장애 때503으로 닫는다.
- worker lease 회복 시 중단된 fetch_runs도 FAILED/finished_at으로 종료한다. 초기 로컬 개발 실패에서 남은 고아 실행은 별도 로컬 검증 명령에서 실패 이력을 남겨 정리한다.

## ENV-001 실행 설정의 이미지 포함
DB 초기화 스크립트와 Caddyfile은 비밀값 없는 고정 배포 설정이므로 Dockerfile COPY로 포함한다. Compose 실행 시 host bind mount 두 개를 제거하고 DB/Caddy named volume은 그대로 유지한다. 설정 변경은 해당 이미지 재빌드·재스캔 후 반영한다. 파일 공유 정체의 노출 경로를 제거하는 변경이며 Docker 자체 결함 해결을 대신 증명하지 않는다.

- 사용자 완료 기준 변경: 별도 배포 검증 제외, 로컬 실행+외부 접속. 공인 IPv4 직접 연결에 Caddy/Let’s Encrypt shortlived IP 인증서를 사용하고 SNI 없는 IP 접속은 default_sni로 같은 인증서를 선택한다. 참고: https://letsencrypt.org/2026/01/15/6day-and-ip-general-availability 및 https://caddyserver.com/docs/caddyfile/options#default-sni . 인증서 검증 비활성화·공개 터널·도메인 구매 없음.

## 2026-10-28 인증 화면·약관
사용자 승인에 따라 로그인·가입의 긴 설명을 정책 페이지로 옮기고 아이디·비밀번호 중심으로 간소화했다. 가입 필수 약관 체크박스는 기본 미선택이며 전문을 새 탭에서 읽어 입력을 보존한다. 서버가 명시 동의와 현재 약관 버전을 확인하고 version/timestamp만 추가 저장한다. 기존 계정의 동의는 소급 생성하지 않는다. 운영자·연락처 미정은 사실 그대로 표시하고 법률 검토 완료를 주장하지 않는다. IndexedDB 방식은 유지한다. CI/CD는 계속 제외한다.

## 정책 문구 평이화 — 2026-10-28
기술 명칭 대신 이용자가 이해할 수 있는 수집 목적·보관·삭제·기기 저장 설명으로 다시 썼다. 수집 범위나 이용 조건은 확대하지 않았다. 공개 약관 본문 변경을 구분하도록 동의 버전을2026-10-28.2로 갱신하고 이전 기록은 유지한다. 개발 검증 상태·법률 검토 상태는 운영 문서에서 관리하며 법률 검토 완료를 뜻하지 않는다.

## 로그인 카운트다운
사용자 요청에 따라5번째 실패부터 남은 횟수를 안내하고10번째 실패에서 즉시 제한 상태를 알린다. 서버 카운터의 expires_at 기준 남은 초를 전달하고 브라우저는 종료 시각에서 차감해 표시한다. 자동 재시도나 별도 polling, 아이디·비밀번호·제한 시각의 브라우저 영속 저장은 추가하지 않는다. 새로고침 후에는 다음 서버 응답으로 현재 제한을 다시 확인한다. 화면 타이머를 우회해도 서버 제한은 유지한다.

## 입력 길이 변경 — 사용자 명시 승인
비밀번호 최소6자, 아이디1~128자로 변경한다. 비밀번호 단독 인증 최소15자를 제시한 NIST 지침과 차이가 있음을 설명한 뒤 사용자가6자를 명시 선택했다. 이전 단문 비밀번호 위험 설명에 따른 제품 정책 결정이며 NIST 해당 길이 기준 충족으로 표시하지 않는다. Argon2id·실패 횟수 제한 등은 유지한다. 기존 유니코드 경계 테스트의 최소 미만 값은 새 기준에 맞춰5자로 바꾸며 상한 검증은 그대로 유지한다.

관심사/계정 페이지 분리: settings.html·settings.js는 관심사 전용, account.html·account.js는 계정 전용. IndexedDB 스키마·이름·키를 유지하여 기존 관심사 마이그레이션이 필요 없다. 문장 분석은 기존 태그 별칭 사전과 단어 경계 규칙을 사용하며 LLM이나 외부 전송을 추가하지 않는다.

## 기기 간 관심사 이전 (2026-10-28)
서버 비전송 경계를 유지하며 암호화 파일의 수동 이전을 채택했다. IndexedDB는 브라우저/출처별 저장이며 계정 로그인만으로 다른 PC와 동기화되지 않는다. 자동 동기화는 서버 등 공유 저장소 또는 동시에 연결된 기기를 요구한다. WebRTC도 신호 교환 및 NAT 환경에 따른 STUN/TURN이 필요해 V1에서 추가하지 않았다.
네이티브 WebCrypto PBKDF2-HMAC-SHA256 600,000회, 무작위16바이트 salt, AES-256-GCM, 매번 무작위12바이트 IV 및 형식 AAD. 파일 전용15~128자 비밀번호는 저장/전송하지 않는다. 로그인 비밀번호 정책은 변경하지 않는다. 브라우저 메모리의 완전 삭제나 악성 확장/XSS 방어를 암호화만으로 보장하지 않는다. 계정 UUID는 암호화 내부에만 포함, 다른 계정 적용을 UI에서 거부한다. 파일 비밀번호 보유자는 평문을 볼 수 있으므로 계정 비교를 암호학적 접근 통제로 주장하지 않는다.
최대128KiB, 필드 whitelist/설정개수500/가중치1~5/키검사/중복배열검사. 적용 전 명시적 확인·취소, 실패 시 기존값 보존. 현재 사전에 없는 유효한 설정 ID도 보존하고 병합 매핑 적용. 내보낸 파일은 서버/탈퇴/브라우저 초기화로 회수되지 않는다. 수동 이전 이후 각 기기는 독립적이며 최신 파일을 다시 옮겨야 한다.
근거(2026-10-28 확인): https://developer.mozilla.org/en-US/docs/Web/API/IndexedDB_API ; https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto/deriveKey ; https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto/encrypt ; https://developer.mozilla.org/en-US/docs/Web/API/WebRTC_API/Protocols ; https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html (PBKDF2 작업량 참고이며 파일 암호화 전체의 보안 보증 아님).

## 공개 지면과 약관 판 (2026-10-28)
최신 사용자 지시로 비회원 실제뉴스 차단을 제한 공개 조회로 대체. 공개 endpoint는 고정30개/no cursor/분당 전체600회 및 전체 API3000회 한도, 개인·IP 저장 없음. 공유 한도의 가용성 절충과 공개된 응답 복사 가능성을 인정한다. 회원 cursor/권한/개인 설정 비전송 유지. 공통 대표 관심사만 DB에 저장하고 ADMIN이 수정한다. 초기값은 기존6개 태그 가중치3이며 관리자가 화면에서 변경 가능. 미설정 또는 개인화 끄기는 대표값 사용; 제외 조건은 계속 우선한다.
약관 표시 판과 Git 원본 대응: {"terms":[{"number":1,"sha":"a11320a"},{"number":2,"sha":"56bdb94"},{"number":3,"sha":"7f11c8b"}],"privacy":[{"number":1,"sha":"a11320a"},{"number":2,"sha":"56bdb94"},{"number":3,"sha":"7f11c8b"},{"number":4,"sha":"742a83a"}]}. 신규 동의 ID terms-4. 이력의 내용은 실제 이전 본문이며 표시 날짜만 판 번호로 바꿨다. 실제 동의 timestamp/이전 ID는 DB에서 유지. 법률 검토 완료로 주장하지 않는다.

## 최초판과 편집 지면
공식 개시 전이라는 사용자 결정으로 공개 약관 이력을 제거하고 두 문서를 제1판으로 시작한다. 기존 계정의 실제 동의 기록·Git 개발 이력은 변조하지 않는다. 신규 가입만 terms-1을 받는다. 대표 문구는 출처를 사칭하지 않는 자체 문구이며 assets/js/config.js의 mastheadQuote 한 곳에서 교체한다. 자동 주기 작업은 추가하지 않는다. 스크랩은 외부 사진·폰트 없이 CSS 다단과 종이색/테이프 장식으로 구현하고 DOM 읽기 순서·원문 링크·검색 팝업은 유지한다.
