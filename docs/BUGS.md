# 환경 결함 기록

## ENV-001 Docker Desktop 시작 및 bind mount 정체

2026-10-28, macOS Apple Silicon, Docker Desktop4.91.0(239619), Apple Virtualization Framework + VirtioFS. 최신 스캔 DB/proxy 이미지 재생성 시 start 요청이 완료되지 않았다. 동일 PostgreSQL 이미지의 bind mount 없는 postgres --version은18.6으로 즉시 성공했다.

일반 재시작은 stopping에서 정체했고, 승인된 공식 강제 종료 후 새 세션에서도 복원 단계가 정체했다. UI starting만 반복 조회해서는 진행을 판단할 수 없었다. backend에는 docker daemon did not become ready: context deadline exceeded가 기록됐다. 로컬 진단에서 runc init 두 프로세스의 D 상태와 host_mnt의 VirtioFS를 확인했다. 이는 파일 I/O 경로를 의심할 근거지만 커널 스택으로 원인을 확정한 것은 아니다.

대응: gRPC FUSE 선택은 적용 대기 후 재기동에서 유지되지 않아 적용 성공으로 처리하지 않았다. 공식4.97.0(244584)에는 진단 지연·응답 불능 및 truststore/로그 보안 수정이 있어 제품 내 업데이트로 교체를 진행한다. 업데이트 후 실제 엔진·bind mount·Compose·수집·이미지 digest 일치로 확인해야 해결 판정한다.

재발 시 무기한 대기를 피하도록 scripts/local-start.mjs에 Engine10초 사전 검사와 Compose90초 health 대기/120초 프로세스 상한을 추가했다. 실패는 비정상 종료하며 DB 볼륨 삭제·공장 초기화를 자동 수행하지 않는다. 이는 엔진 결함 자체의 수정 완료를 뜻하지 않는다.

근거: https://docs.docker.com/desktop/release-notes/#4970 및 https://docs.docker.com/desktop/settings-and-maintenance/settings/ . 진단 묶음은 외부 업로드하지 않았고 민감 로그 가능성이 있어 공개 산출물에서 제외·정리한다.

상태: 원인 조사 및 공급자 업데이트 진행 중. 해결 미검증.

2026-10-28T04:17Z:4.97.0 설치와 CLI/UI 버전 확인 완료. 동일 시작 정체가 재현되어 업데이트만으로 해결되지 않았다. VM 정상 종료 요청도 init services stop timeout을 반환했고, 승인된 재시작 범위에서 해당 VM 프로세스를 종료했다. backend 설정 적용이 engine stopping에서 대기하는 별도 증거를 확인했다. 프로젝트의 두 고정 설정을 이미지 COPY로 변경해 runtime bind mount 의존을 제거했다. 소스 검사·check/bootJar/SBOM/securityCheck 성공, 새 이미지 빌드·컨테이너 회귀 검증은 아직 미실행이다.

### 2026-10-28T04:39Z: 파일 공유 방식을 유지한 재조사
사용자 지시로 VirtioFS를 유지하며 gRPC FUSE 변경 제안과 설정 파일 수정 요청은 철회한다. 대기 중인 GUI 변경은 공식 force stop/start로 종료했고, 새 VM mount 목록에서 VirtioFS 사용을 확인했다.

추가 증거: VM의 dockerd 및 runc init 커널 스택이 d_alloc_parallel → filename_lookup → vfs_statx에서 대기한다. 호스트 Apple Virtualization 프로세스(pid42502) 샘플은 파일 공유 스레드가 openat에서 대기함을 보여준다. macOS 커널 로그는 같은 pid에 대해 'watchdog expired for approval entry ... (kTCCServiceSystemPolicyDesktopFolder, pid 42502)'와 syscall-unix333 거부를 반복 기록했다. 시스템 설정 → 파일 및 폴더 → Docker의 데스크탑 폴더 권한은 on이다. 사용자 tccd(pid85583) 샘플은 CFUserNotificationReceiveResponse에서 대기한다. 디스크 여유309GiB, 시스템 확장0개를 확인했다.

판정: 단순 권한 off 또는 VirtioFS 자체 결함으로 확정할 수 없다. 데스크톱 접근 승인 처리 경로의 정체가 직접 관찰됐으며 대기 알림이 어느 앱의 요청인지 추가 확인이 필요하다. 시스템 알림 앱 UserNotificationCenter 접근은 컴퓨터 제어 도구가 안전상 차단했다. 사용자에게 보이는 권한 알림의 앱·내용 확인을 요청했으며 TCC DB 수정·권한 초기화·전체 디스크 권한 부여·공유 방식 변경은 하지 않았다. 진단 bundle은 외부 전송하지 않고 분석 후 삭제한다.

## ENV-001 복구 확인 — 2026-10-28T05:20Z
사용자 권한 서비스 tccd의 응답 대기가 지속되어 해당 사용자 프로세스만 재시작했다. 저장된 권한·TCC DB·파일 공유 설정은 변경하지 않았다. 새 tccd는 Docker 요청에 authValue=2/authReason=2를 즉시 반환했고 공식 Docker 재시작 후 Engine29.9.2가 준비됐다. VirtioFS를 유지한 채 새 이미지 빌드와 PostgreSQL 통합17개 재실행이 성공했다. 이는 권한 응답 경로 정체를 뒷받침하며 Apple 내부 결함이나 특정 알림의 정체 원인까지 확정하지 않는다. 사용자 화면 조작은 필요하지 않았다.

## ENV-002 proxy 이미지 설정 디렉터리 권한
COPY --chmod=0444로 새 경로에 설정을 복사할 때 /etc/caddy 디렉터리에도 탐색 권한이 없어 비루트 Caddy가 설정을 읽지 못했다. 실제 컨테이너에서 Permission denied를 재현했다. 디렉터리0755·설정파일0444를 별도로 지정하고, HTTP /about.html healthcheck를 추가했다. 재빌드 후 Compose db/web/proxy healthy와 worker 실행을 확인했다. 이전 시작 상태만으로 성공을 판정하지 않는다.
