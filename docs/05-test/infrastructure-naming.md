# MP-DOC-042 인프라 명칭 이관 검증서

2026-11-06 실제 실행 결과. 서비스 교체·데이터 보존 검증은 완료했다. 현재 커밋의 GitHub 전달·독립/App 검토·develop 병합 결과는 최종 PR 기록을 정본으로 확인한다. 실행 전에 병합 성공으로 기록하지 않는다.

## 결과
- 전체 Gradle clean 계약 PASS: 단위192/실제 PostgreSQL150/Playwright Java86, 합계428. FAIL/ERROR/SKIP0. 후속 운영 도구 오류출력 보호 및 프록시 내부 표기 변경 후 check/securityCheck를 다시 통과했다. 추가 이관5시험, Compose local/public/server·SMTP/AUTH 설정 경계, Helm lint/template 및 잘못된 값 거부 PASS.
- 쓰기 중지 후 기존37테이블의 건수·정렬된 전체 행 SHA256이 DB/역할 RENAME 전후 모두 일치했다. DB 역할 OID·SCRAM 비밀번호·권한도 동일하다. 최종 age 암호화 백업을 별도 네트워크 격리 DB에 복원해 같은37테이블 모든 행 지문 일치를 확인했다. 임시 DB/네트워크를 삭제했다. 기존 계정/구독/뉴스/뉴스레터/세션/키를 재생성하지 않았다.
- 8개 볼륨의 소유자/모드/링크/파일 SHA256 일치 후 원래 볼륨을 제거했다. 현재 쓰지 않는 기존 DB/인증서 보관 볼륨도 damgox2 이름으로 내용 그대로 보존했다. 전역 prune/down -v는 사용하지 않았다.
- 새 damgox26서비스 정상. 원래moapan9컨테이너/8볼륨/4네트워크/9이미지태그 제거. 타 프로젝트11컨테이너의 ID·이미지·시작/정지 상태·시작시각과 비밀5파일 SHA256 동일. 기존 미추적 HTTPS 스킬3파일 미변경·커밋 제외.
- 실제 HTTPS13경계·공개 canonical4개 PASS. 정상 인증서 검증 유지. 최종 실제 번역6,809ms/분류23,402ms의 합성 단회 요청, 악성6입력 호출 전 거부, 진행 추론 취소 PASS. 성능 평균/품질 보증으로 확대하지 않는다. SMTP STARTTLS/호스트/운영자 CA/AUTH 연결만 확인했으며 새 메일은 보내지 않았다.
- 호스트/컨테이너 실행 JAR SHA256 동일: `f02802b457298db5df4420d8c88df61370fbe1056aafbfd8e9834251bea3fc28`. Temurin27.0.1.0.2+1/Boot4.1.2/Gradle9.8.2/PostgreSQL18.6.

## 보안과 이미지
Trivy의 기존 검증 빌드를 사용하고 최신 DB 동기화 2026-11-06T08:04:37.259829Z를 확인했다. 실제 JAR/SBOM/소스/Helm과 아래8이미지의 정확한 로컬 ID를 검사했다. 영향 있는 미해결HIGH/CRITICAL0·secret0. 프록시와 빌드 이미지의 raw GO-2026-5932 UNKNOWN은 [공식 OpenPGP 공지](https://pkg.go.dev/vuln/GO-2026-5932)이며 실제 프록시 81216개 심볼에 해당 코드가 없음을 확인했다. UNKNOWN을 자동0으로 바꾸지 않았다. 새 ignore/기한 연장은 없다. builder에만 있던 patch2.8-r0 MEDIUM2는 사용 직후 패키지를 제거해 최종 빌드 이미지에서 사라졌고 Go 회귀를 재실행했다.

| 역할 | 검사한 로컬 이미지 ID |
|---|---|
| app | `sha256:32e21cdaaa9ebc8803866a130d40faf42bfc9c559847a249822120524caf9217` |
| database | `sha256:318a1c98713b45f3115f3a69e27c4d741999d1a29bbc67344c91277532141c1f` |
| proxy | `sha256:553a883f2a14265c66ac70b6e2017b1032540516be34f6bbca02a8327ff12e11` |
| translator | `sha256:04a55c3263edf6a7041f5bd8ffda043392ca9553b18493df38b4c18cd2d37776` |
| classifier | `sha256:c0ea5e516e19ef98d40e85a1586ab3719a3a13908d4bc4ede3e411b1442a44b3` |
| builder | `sha256:ea62e98f55a2bc925a4e383425b0df4a28ae253993c12ac27c20b1feeb80fbf8` |
| proxyBuilder | `sha256:3809d7b015a1203362581fff821850683d8dfafb48b7ef6ad4d6146a37638181` |
| localProxy | `sha256:474a92d8f2d251d07a4a0f7e01ab4aeb3b0eb283335c0488bbdcf15af67e5e2c` |

로컬 image ID와 registry 배포 digest를 구분한다. 검증한 앱/DB/공개 proxy/모델 ID를 비공개 Compose override로 고정해 실행했다. 운영 명령은 [배포 절차](../06-transition/deployment.md#현재-운영-전환--2026-11-06-damgox2-명칭-이관)를 따른다. Helm 실클러스터 설치·새 인터넷망 휴대전화 재시험·포털 검색순위·외부 메일함 전달·사업 개시는 이번 시험 범위 밖이다.

기계 판독 결과·산출물 SHA256: [증빙 JSON](evidence/infrastructure-naming-20261106.json). 호환 식별자를 유지한 이유와 이관 중단 조건은 [MP-DOC-041](../03-design/infrastructure-naming.md)을 따른다.

## 독립 리뷰 F1/F2 수정·복구
첫 head e465ff4의 독립 리뷰가 worker 재개 후 정책 해시 변경에 따른 데이터 무효화(F1)와 evidence 부모 symlink 경계(F2)를 발견해 병합을 보류했다. 실제 번역 수가272→9가 되어 worker/web을 중지하고 백업의272번역·각528개 job·502태그를 복구했다. 기사·사이트/권리·태그 사전·분류 설정·운영자 변경 부재를 확인한 뒤 해당4개 테이블만 복원해 원래 행 SHA256과 일치했다. 첫 복구 트랜잭션은 실패·롤백했고 pg_dump의 빈 search_path를 public으로 맞춰 재실행했다. V21 trigger를 비활성화하거나 TRUNCATE하지 않았으며 카드/분류 캐시 지문 불일치0을 확인했다. 계정/세션/인증 quota·새 edition·실사용 AI 예산은 되돌리지 않았다. 추가 소비된 집계는 증빙에 남기며 pending 작업의 일일 제한은 유지한다.

최종 앱의14개 정책 입력 class bytes와 POLICY/PROVIDER가 기준 배포 JAR와 동일함을 단위·실제 이미지에서 확인했다. 신규 환경 설정은 내부 property에 매핑하고 private model 요청 식별자를 호환 상태로 유지했다. 기존 unlocked 번역의 read/claim 보존, 기존 DONE 분류와 비수동 태그 보존을 PostgreSQL 회귀에 추가했다. 실제 worker 재개 후에도272번역·502태그의 원래 전체 행 지문이 유지된다. fingerprint를 고정/우회하거나 old-provider를 무조건 허용하지 않았다. F2는 실제 부모 경로·기존 파일을 copy 전에 검증하고 작업폴더 내부 합성 symlink로 회귀했다. 전체 clean428·추가5시험·최종8이미지 스캔·실제 모델/HTTPS 재검증 PASS. 최종 현재-head 독립 재리뷰와 App 정식 승인 전에는 병합하지 않는다.
