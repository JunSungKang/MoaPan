# Linux 자동 번역 운영

사용자 승인에 따라 Linux Docker Compose를 운영 기준으로 한다. Mac native 모델/Metal/host.docker.internal/launchd가 필요하지 않다. CPU 기본, GPU profile은 장비와 드라이버가 지정되기 전 제공·검증됐다고 주장하지 않는다. 앱과 번역 이미지 모두 amd64/arm64 build 경로가 있으며 실제 검증 아키텍처는 테스트 보고서에 기록한다.

## 준비 및 실행
Docker Engine/Compose(기존 !override 설정을 지원하는 버전), 충분한 디스크/메모리, HTTPS 인입과 DNS/방화벽 설정이 필요하다. CPU 번역 기본 제한2 CPU/4GiB 외에 web/worker/DB/OS 자원이 필요하다. 4GiB 서버 전체에서 실행 가능하다는 뜻이 아니다. 정확한 최소 사양과 처리량은 실제 서버에서 측정한다.

1. 저장소와 검증한 이미지/소스 버전을 준비한다. `.env.example`을 참고해 `.env`에 별도 DB 비밀값·MOAPAN_SECRET·PUBLIC_BASE_URL=https://승인주소·MOAPAN_PUBLIC_HOST=승인주소를 설정한다. 비밀값은 Git에 넣지 않는다.
2. `sh scripts/translation/translation-prepare.sh` — workspace secret 준비, Linux CPU 이미지 빌드, 모델 영속 볼륨에 공식2B Q8 다운로드와 SHA256 검증. 기존 모델이 있으면 다운로드 없이 검증한다. 손상 파일을 실행하지 않는다. 중단된 임시 파일은 다음 초기화 때 다시 다운로드하며 flock은 프로세스 종료 시 해제된다.
3. `docker compose -f compose.yaml -f scripts/deploy/compose-server.yaml -f scripts/deploy/compose-translation.yaml build`
4. 검증 runner에서 `sh scripts/security/image-scan.sh moapan-local:1.0.0 moapan-translator:cpu-b11960 moapan-proxy-public:2.11.7 moapan-db-local:18.6` 및 프로젝트 보안검사를 실행한다. 실패를 통과 처리하지 않는다. 이미지 이관 시 검증 digest를 대조한다.
5. `docker compose -f compose.yaml -f scripts/deploy/compose-server.yaml -f scripts/deploy/compose-translation.yaml up -d --no-build`
6. `docker compose -f compose.yaml -f scripts/deploy/compose-server.yaml -f scripts/deploy/compose-translation.yaml ps`와 관리자 번역 현황을 확인한다.

로컬 테스트는 server overlay 대신 기본 loopback 구성을 사용한다. 현재 승인된 Mac 공개IP 실행에는 기존 compose-public.yaml을 유지한다. Linux server overlay는 공개IP가 서버 NIC에 직접 할당되지 않은 NAT 환경에서도 listen 주소와 사이트 주소를 분리한다.

translator 포트는 publish하지 않는다. worker만 전용 internal network에 연결하며 모델은 외부 인터넷에 접근하지 않는다. model-init만 다운로드할 수 있다. 모델·키는 이미지에 포함하지 않는다. 공개proxy에 번역 endpoint를 추가하지 않는다.

## 자동 작업
입력은 허용된 저장 제목/요약뿐. 기존 본문/PDF/원문 전문이나 회원정보를 보내거나 저장하지 않는다.

PENDING → RUNNING → DONE 또는 RETRY → ORIGINAL. 권리철회/숨김은 CANCELLED. 처리 버전 변경은 해당 파생 번역을 무효화하고 한번 재등록한다. V9는 기존 파생 번역을 재검사하기 위해 비우지만 기사/회원/출처는 유지한다.

제목·요약 항목별로 성공한 번역만 임시 checkpoint에 저장하고 재사용한다. 실패 출력 전문은 저장하지 않는다. 모두 통과하면 기사 단위로 공개하며 종결 시 checkpoint를 삭제한다. 실패 항목은 최대3회(최초+2회 보정). 수치/식별자 보호표식, 용어·부정·방향 보존 지시를 실패 코드에 맞춰 적용한다. 세 번째 실패는 자동 ORIGINAL로 종결하고 다른 기사를 계속 처리한다.

모델 연결/HTTP 장애는 품질 실패 횟수와 별도로 관리한다. 상태 점검은30초→2분→10분 backoff, 정상 시 자동 재개한다. worker가 죽으면90초 lease 만료 후 회수한다. 실행 중20초 heartbeat로 lease를 연장하되 요청시간은10~600초 설정 범위(기본300초)로 제한한다. DB 장애는 결과 공개/무제한 호출로 우회하지 않으며 lease/상태 시각으로 진단한다.

Docker restart 정책은 프로세스 종료를 복구한다. healthcheck만으로 재시작되지 않는 점을 고려해 번역 이미지의 감독 스크립트가 시작 유예30초 후 10초 간격 점검12회 연속 실패 시 모델을 종료하고 컨테이너를 실패 종료한다. Docker daemon은 서버 운영 설정에서 부팅 시 시작돼야 한다. 정전/디스크 고장/메모리 부족의 무한 자동 복구를 보장하지 않는다.

## 검증 범위
숫자·시간 단위/규모 표기 정규화, 순서·단위·식별자·일부 명시적 부정/증감/비교 표현과 핵심 용어를 검사한다. 코드·URL·CVE·버전은 정확히 보존한다. 의미상 정상인 어순 변경이나 지원하지 않는 숫자 표현도 보수적으로 제외될 수 있다. 임의의 모든 의미 오류/환각을 탐지하지 못한다. 상태는 자동 검사 통과이며 정확성 인증이 아니다. 같은 모델의 자기 채점이나 역번역만으로 통과시키지 않는다. 자동 모델/용어표 업데이트는 없다.

관리 API GET /api/v1/admin/translations는 상태 집계·실패코드·최근20개 작업의 내부ID/시각을 ADMIN에게만 제공한다. 관리자 화면의 새로고침은 선택적 관측이며 일상 작업 승인 단계가 아니다. 실제 article body, member interest, cookie, prompt/output 전문을 로그에 남기지 않는다.

## 이관/복구
기존 encrypted backup/restore 절차를 따른다. DB volume을 다른 OS의 파일 경로로 직접 복사하는 대신 PostgreSQL 백업·복원을 사용한다. 모델 volume은 고정 모델을 재다운로드할 수 있으므로 회원 DB처럼 취급할 필요가 없다. 키는 별도로 안전하게 전달하거나 새서버에서 재발급하여 worker/translator에 함께 적용한다. 이전세션·탈퇴회원 복원 정책은 DEPLOYMENT.md의 기존 정책을 따른다.

입력/번역/큐가 PostgreSQL에 있으므로 재시작 후 이어서 처리한다. 다른 서버에서 가져온 RUNNING lease는 만료 후 재개한다. 동시에 두 서버가 같은 DB를 사용할 때도 lease token으로 결과 소유권을 검증한다. 실서버 이관 시험 전에는 원격 Linux 검증 완료로 보고하지 않는다.
