# 환경 결함 기록

## ENV-001 Docker Desktop 시작 및 bind mount 정체

2026-10-28, macOS Apple Silicon, Docker Desktop4.91.0(239619), Apple Virtualization Framework + VirtioFS. 최신 스캔 DB/proxy 이미지 재생성 시 start 요청이 완료되지 않았다. 동일 PostgreSQL 이미지의 bind mount 없는 postgres --version은18.6으로 즉시 성공했다.

일반 재시작은 stopping에서 정체했고, 승인된 공식 강제 종료 후 새 세션에서도 복원 단계가 정체했다. UI starting만 반복 조회해서는 진행을 판단할 수 없었다. backend에는 docker daemon did not become ready: context deadline exceeded가 기록됐다. 로컬 진단에서 runc init 두 프로세스의 D 상태와 host_mnt의 VirtioFS를 확인했다. 이는 파일 I/O 경로를 의심할 근거지만 커널 스택으로 원인을 확정한 것은 아니다.

대응: gRPC FUSE 선택은 적용 대기 후 재기동에서 유지되지 않아 적용 성공으로 처리하지 않았다. 공식4.97.0(244584)에는 진단 지연·응답 불능 및 truststore/로그 보안 수정이 있어 제품 내 업데이트로 교체를 진행한다. 업데이트 후 실제 엔진·bind mount·Compose·수집·이미지 digest 일치로 확인해야 해결 판정한다.

재발 시 무기한 대기를 피하도록 scripts/local-start.mjs에 Engine10초 사전 검사와 Compose90초 health 대기/120초 프로세스 상한을 추가했다. 실패는 비정상 종료하며 DB 볼륨 삭제·공장 초기화를 자동 수행하지 않는다. 이는 엔진 결함 자체의 수정 완료를 뜻하지 않는다.

근거: https://docs.docker.com/desktop/release-notes/#4970 및 https://docs.docker.com/desktop/settings-and-maintenance/settings/ . 진단 묶음은 외부 업로드하지 않았고 민감 로그 가능성이 있어 공개 산출물에서 제외·정리한다.

상태: 원인 조사 및 공급자 업데이트 진행 중. 해결 미검증.

2026-10-28T04:17Z:4.97.0 설치와 CLI/UI 버전 확인 완료. 동일 시작 정체가 재현되어 업데이트만으로 해결되지 않았다. VM 정상 종료 요청도 init services stop timeout을 반환했고, 승인된 재시작 범위에서 해당 VM 프로세스를 종료했다. backend 설정 적용이 engine stopping에서 대기하는 별도 증거를 확인했다. 프로젝트의 두 고정 설정을 이미지 COPY로 변경해 runtime bind mount 의존을 제거했다. 소스 검사·check/bootJar/SBOM/securityCheck 성공, 새 이미지 빌드·컨테이너 회귀 검증은 아직 미실행이다.
