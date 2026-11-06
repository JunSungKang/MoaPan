# MP-DOC-042 인프라 명칭 이관 검증서

2026-11-06 실제 실행 결과. 서비스 교체·데이터 보존 검증은 완료했다. 현재 커밋의 GitHub 전달·독립/App 검토·develop 병합 결과는 최종 PR 기록을 정본으로 확인한다. 실행 전에 병합 성공으로 기록하지 않는다.

## 결과
- 전체 Gradle clean 계약 PASS: 단위190/실제 PostgreSQL148/Playwright Java86, 합계424. FAIL/ERROR/SKIP0. 후속 운영 도구 오류출력 보호 및 프록시 내부 표기 변경 후 check/securityCheck를 다시 통과했다. 추가 이관4시험, Compose local/public/server·SMTP/AUTH 설정 경계, Helm lint/template 및 잘못된 값 거부 PASS.
- 쓰기 중지 후 기존37테이블의 건수·정렬된 전체 행 SHA256이 DB/역할 RENAME 전후 모두 일치했다. DB 역할 OID·SCRAM 비밀번호·권한도 동일하다. 최종 age 암호화 백업을 별도 네트워크 격리 DB에 복원해 같은37테이블 모든 행 지문 일치를 확인했다. 임시 DB/네트워크를 삭제했다. 기존 계정/구독/뉴스/뉴스레터/세션/키를 재생성하지 않았다.
- 8개 볼륨의 소유자/모드/링크/파일 SHA256 일치 후 원래 볼륨을 제거했다. 현재 쓰지 않는 기존 DB/인증서 보관 볼륨도 damgox2 이름으로 내용 그대로 보존했다. 전역 prune/down -v는 사용하지 않았다.
- 새 damgox26서비스 정상. 원래moapan9컨테이너/8볼륨/4네트워크/9이미지태그 제거. 타 프로젝트11컨테이너의 ID·이미지·시작/정지 상태·시작시각과 비밀5파일 SHA256 동일. 기존 미추적 HTTPS 스킬3파일 미변경·커밋 제외.
- 실제 HTTPS13경계·공개 canonical4개 PASS. 정상 인증서 검증 유지. 실제 번역7,599ms/분류25,043ms의 합성 단회 요청, 악성6입력 호출 전 거부, 진행 추론 취소 PASS. 성능 평균/품질 보증으로 확대하지 않는다. SMTP STARTTLS/호스트/운영자 CA/AUTH 연결만 확인했으며 새 메일은 보내지 않았다.
- 호스트/컨테이너 실행 JAR SHA256 동일: `952a3695d09658a66176d855de2c15f990f6aae376c215b25e3144bc31797d63`. Temurin27.0.1.0.2+1/Boot4.1.2/Gradle9.8.2/PostgreSQL18.6.

## 보안과 이미지
Trivy의 기존 검증 빌드를 사용하고 최신 DB 동기화 2026-11-06T08:04:37.259829Z를 확인했다. 실제 JAR/SBOM/소스/Helm과 아래8이미지의 정확한 로컬 ID를 검사했다. 영향 있는 미해결HIGH/CRITICAL0·secret0. 프록시와 빌드 이미지의 raw GO-2026-5932 UNKNOWN은 [공식 OpenPGP 공지](https://pkg.go.dev/vuln/GO-2026-5932)이며 실제 프록시 81216개 심볼에 해당 코드가 없음을 확인했다. UNKNOWN을 자동0으로 바꾸지 않았다. 새 ignore/기한 연장은 없다. builder에만 있던 patch2.8-r0 MEDIUM2는 사용 직후 패키지를 제거해 최종 빌드 이미지에서 사라졌고 Go 회귀를 재실행했다.

| 역할 | 검사한 로컬 이미지 ID |
|---|---|
| app | `sha256:a1c123edb96df689f334268bf3226e003a9b4058e4c7881dab4adae27e123886` |
| database | `sha256:318a1c98713b45f3115f3a69e27c4d741999d1a29bbc67344c91277532141c1f` |
| proxy | `sha256:553a883f2a14265c66ac70b6e2017b1032540516be34f6bbca02a8327ff12e11` |
| translator | `sha256:04a55c3263edf6a7041f5bd8ffda043392ca9553b18493df38b4c18cd2d37776` |
| classifier | `sha256:c0ea5e516e19ef98d40e85a1586ab3719a3a13908d4bc4ede3e411b1442a44b3` |
| builder | `sha256:cba204e7645cb4da85682edb06b5ba10a5ac7585c14393a0ecc933dacfd61641` |
| proxyBuilder | `sha256:3809d7b015a1203362581fff821850683d8dfafb48b7ef6ad4d6146a37638181` |
| localProxy | `sha256:474a92d8f2d251d07a4a0f7e01ab4aeb3b0eb283335c0488bbdcf15af67e5e2c` |

로컬 image ID와 registry 배포 digest를 구분한다. 검증한 앱/DB/공개 proxy/모델 ID를 비공개 Compose override로 고정해 실행했다. 운영 명령은 [배포 절차](../06-transition/deployment.md#현재-운영-전환--2026-11-06-damgox2-명칭-이관)를 따른다. Helm 실클러스터 설치·새 인터넷망 휴대전화 재시험·포털 검색순위·외부 메일함 전달·사업 개시는 이번 시험 범위 밖이다.

기계 판독 결과·산출물 SHA256: [증빙 JSON](evidence/infrastructure-naming-20261106.json). 호환 식별자를 유지한 이유와 이관 중단 조건은 [MP-DOC-041](../03-design/infrastructure-naming.md)을 따른다.
