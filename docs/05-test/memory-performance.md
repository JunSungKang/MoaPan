# 메모리·성능 최적화 검증서

문서번호: MP-DOC-053 · 기준일: 2026-11-11 · 상태: 기능·성능·보안·운영 반영 PASS, 최종 문서 리뷰·병합은 PR33

검증 소스는 develop71ecb39와 이번 변경만 별도 디렉터리로 내보낸 것이다. 기존 미커밋 사업·화면·스킬79개 파일은 빌드와 커밋에서 제외한다. [측정 증빙](evidence/memory-performance-20261111.json), [실행 인계](../06-transition/memory-runtime.md), [Sprint](../01-management/sprints/memory-performance-20261111.md).

## 1. JVM·DB·HTTP 비교

동일 앱 이미지, Linux/arm64, CPU1.5, 메모리 한도768MiB, 합성 기사300건, 동시에8요청, 최초120회 준비와240회 측정, 추가32동시·80회 burst를 기준/개선/개선/기준 순으로 실행했다. 각 variant2회 평균이다. 운영 DB·회원 데이터는 사용하지 않았다. 두 설정 모두 같은768MiB 한도이며 절감은 실제 측정값이다.

| 측정 | 기준 | 개선 | 변화 |
|---|---:|---:|---:|
| 웹 부하 후 RSS | 385.8MiB | 263.3MiB | -31.8% |
| worker RSS | 254.6MiB | 215.6MiB | -15.3% |
| 합산 RSS | 640.4MiB | 478.9MiB | -25.2% |
| 합산 cgroup working set | 619.1MiB | 457.3MiB | -26.1% |
| 각 실행 p95 평균 | 89.28ms | 88.28ms | -1.1% |
| 처리량 | 231.8건/초 | 224.3건/초 | -3.2% |
|32동시 burst p95 평균|201.51ms|159.25ms|-21.0%|
|95초 유휴 후 웹 DB 연결|12|2|-83.3%|

전체 응답의 기사 payload 지문이 동일했고 HTTP 오류0, worker 종료0.5초 이내다. 메모리 감소와 p95/처리량 악화20% 이내의 사전 기준을 충족한다. 처리량은 약3% 낮아졌으므로 모든 성능이 향상됐다고 주장하지 않는다. 서로 다른 시각의 다른 프로젝트 부하가 있을 수 있는 개발 호스트이며 결과는 운영 SLA가 아니다. p95는 실행별 값의 평균이며 전체 표본을 합친 p95가 아니다.

## 2. AI 유휴 생명주기

기존 실행 파일·모델 체크섬·4096 context·CPU2스레드를 유지했다. 실제 새 serve 스크립트를 사용해 모델별 두 차례 sleep/wake를 검증했다. 시험 유휴10초, 운영300초다. 출력은 같은 합성 입력·seed42·temperature0에서 동일했고 health200, 무인증401, 재시작0이었다.

| 모델 | 활성 RSS | 유휴 RSS(두 번째) | 활성 anon | 유휴 anon(두 번째) | 준비 상태 요청 | 재로딩 요청(두 번째) |
| translation | 2130.8MiB | 181.3MiB | 215.3MiB | 175.4MiB | 1618ms | 2494ms |
| classification | 2982.1MiB | 211.1MiB | 371.8MiB | 205.0MiB | 3743ms | 5212ms |

모델 RSS와 실제 Docker 비용을 혼동하지 않는다. 두 AI의 cgroup anonymous 메모리는 합계 약587→380MiB로 감소했다. OS 파일 캐시는 남아 다음 로딩에 재사용되고 필요할 때 OS가 회수한다. shared file page의 비용은 다른 cgroup에 귀속될 수도 있다. RSS 약92% 감소를 전체 VM 물리 메모리92% 절감으로 표시하지 않는다. 연속 추론 중에는 모델 메모리가 필요하다. 유휴 후 첫 배경 처리에 약1~2초 추가 지연을 관측했으며 이미 저장한 뉴스 열람은 추론을 호출하지 않는다. 경량 모델 전체 품질을 새로 평가한 시험은 아니다.

## 3. 기능·보안

- 단위195·PostgreSQL 통합158·Playwright86, 총439. 실패·오류·skip0. 최종 aggregate 실행에서 변경 없는 Java 시험은 Gradle의 이전 성공 결과를 재사용했다.
- 역할별 launcher/운영자 override/입력 검증/인증서 지문 의미/4개 서비스 override Node 시험10개 PASS.
- SMTP STARTTLS·인증서 chain·hostname·AUTH235, TLS1.3 PASS. 메일 발송·받은편지함 도착은 시험하지 않았다.
- 기존117개 Java 신뢰 anchor와14개 AI class/policy 지문 유지. 정상 HTTPS 및 공식14개 잘못된 체인 TLS 거부 PASS. 공통 base가 다시 포함한 emSign G1을 기존 공식 완화 도구로 제거했으며 OS에서도4개 root가 없음을 확인했다. 예전 cacerts를 복사하지 않았다.
- Trivy0.76.2 DB 동기화 2026-11-11T11:18:55.984524Z; 최종 앱/AI 및 변경 없는 proxy HIGH/CRITICAL0·secret0. MEDIUM/LOW는 보고서에 남긴다. JAR102개 패키지/SBOM107개 구성요소와 공급자 advisory/JOSE 실제 부정 대조 검증 PASS.

## 4. 실패와 수정

초기 docsCheck는 기존 PR32 번역문 검증서 대장 누락 때문에 실패했다. MP-DOC-052로 등록하고 기존 본문과 판정을 바꾸지 않았다. 격리 fixture의 공통 PG 전용 진입점·내부망 포트·Host 조건을 바로잡은 뒤 결정적 시험을 완료했다. 예비 측정은 빌드와 겹쳐 최종 수치에서 제외했다.

별도 소스 내보내기의 scanner symlink는 Docker bind 안에서 해석되지 않아 실패했다. 검증한 scanner를 해당 workspace 안에 복사했다. 기존 보안 스크립트는 warm cache를 전제로 원본 JOSE 모듈을 복사하다 실패했다. 정확한 v3.0.5를 checksum 검증 다운로드하도록 보완해 원본의5개 부정 시험 실패와 패치본 통과를 재현했다. 검사/판정/기준을 끄거나 예외를 추가하지 않았다.

## 5. 운영·GitHub

2026-11-11 21:31~21:32(Asia/Seoul), 공통 native flock을 보유한 상태에서 platform의 DamGoX2 네 컨테이너만 교체했다. web/worker는 앱 이미지 bdda575ee9dd, translator/classifier는 AI 이미지867bdad6857d를 사용한다. 전체 image ID와 원래 이미지, 검사 결과는 측정 증빙에 기록한다. 공통 운영 Compose는 수정하지 않았으며 proxy·공유 PostgreSQL·MailBox 등 다른 운영 컨테이너12개의 ID와 시작 시각은 같았다. 기존 네트워크·볼륨·인증서·secret·공개80/443·API20100을 보존했다.

교체 전 현재 컨테이너의 전체 ENV와 실행 구성을 age로 암호화하고0600으로 저장했으며 복호화 일치를 확인했다. 운영자 ENV 보존, 실제 JVM의 web Xmx256MiB/worker Xmx192MiB와 compact headers, web8/2·worker6/1 풀, worker 캐시의 논리 상한8MiB를 실행 프로세스에서 확인했다. DB 이관·스키마 변경·마이그레이션 없이10개 안정 테이블의 지문이 같았다. worker는644ms에 종료하고 재시작 후 heartbeat를 갱신했다. 운영 HTTPS의 소개/지면200, 비인증 계정401, 미구독 뉴스레터404가 정상이다. SMTP는 앞 절의 strict STARTTLS·인증 검증 범위이며 실제 메일 발송은 포함하지 않는다.

21:37에는 두 운영 모델 모두 sleeping=true·health 정상·재시작0을 확인했다. 유휴 RSS는 번역132.5MiB/분류134.4MiB였지만 파일 캐시가 각각 약1292/1197MiB 남았으므로 이를 VM 물리 메모리 전체 절감으로 계산하지 않는다. 교체 전 AI는 이미 일부 swap된 상태였으므로 운영 전후 AI RSS만으로 절감률을 산출하지 않는다. 운영 JDBC 연결은 교체 전24개, 직후4개, 유휴 확인 때5개였으며 실제 처리 상태에 따라 바뀐다. 성능 비교의 기준은 1~2절의 격리 측정이다.

[PR33](https://github.com/JunSungKang/MoaPan/pull/33)의 head1d82337에 독립 재리뷰와 App APPROVED를 받은 후 위 서비스 교체를 수행했다. 이 운영 증빙을 반영한 최종 head는 새 리뷰 대상이며 최종 승인·병합 SHA는 해당 PR을 기준으로 확인한다. main은 대상이 아니다. 기존 미커밋 사업·화면·스킬79개 파일은 이번 납품과 분리해 보존한다.

## 6. PR33 독립 리뷰 보완

최초 head88a8692의 독립 리뷰는 CHANGES_REQUESTED였다. F1은 현재 컨테이너에만 있는 운영자 ENV가 새 base/image에서 사라질 수 있는 단방향 검사였다. 현재 Config.Env 전체를 고정하고 새 이미지 기본값까지 합친 전체 유효 ENV를 양방향 비교한다. 사용자 JVM override는 보존하고 dollar 문자는 Compose 입력에서 한 번만 escape한다. 컨테이너에만 있는 연결 timeout과 dollar 포함 ENV 회귀를 추가했다.

F2는 서비스 중지 후 공통 파일/보간 입력이 바뀌면 정상 교체와 rollback이 모두 같은 변경된 파일에서 막히는 문제였다. 자체 네 서비스와 필요한 외부 리소스만 메모리에 고정해 stdin으로 실행한다. 정상 경로는 공통 변경을 감지해 중단하고 복구는 고정한 이전 구성만 사용한다. 별도의 age 암호화 복구 구성은 작업 폴더에0600으로 보관하고 복호화 일치를 교체 전에 검사한다. 비밀값이 든 평문 구성은 파일/로그/CLI 인자에 남기지 않는다.

실제 Docker 합성 네 컨테이너를 중지하고 웹만 새 구성으로 시작한 뒤, 합성 공통 구성 파일을 변경하고 삭제했다. 실패를 탐지하고 공통 파일 없이 이전 네 컨테이너의 image/ENV/command와 실행 상태를 복원했다. 기존7개+신규3개 Node 시험10개 실패·skip0이다. 이 보완 시점에는 운영 서비스를 변경하지 않았고, 이후1d82337 독립 재리뷰에서 F1/F2 RESOLVED·APPROVE를 확인했다. 실제 운영 교체 결과는 5절과 같다. 운영에서 장애를 주입하거나 rollback을 실행한 것은 아니다.
