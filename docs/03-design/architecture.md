# 아키텍처

> 문서번호: MP-DOC-006 · 기준: 저장소 현재 리비전 · 상태: 유지관리 · 작성: 개발 담당 · 검토: 독립 리뷰 담당

브라우저 → proxy → web → PostgreSQL. 별도 worker는 승인된 RSS/Atom/GET JSON → 정규화·추출형 요약·태그 → PostgreSQL. 동일 JAR의 역할을 MOAPAN_ROLE로 선택한다. migrate/admin-cli/worker는 HTTP 포트를 열지 않는다.

Account는 JPA, 기능별 CatalogRepository/JdbcClient는 명시 SQL을 사용한다. Controller→Service→Repository 경계를 유지하고 Entity는 응답으로 반환하지 않는다. 서버에 회원 개인 관심사·검색 이력 저장·클릭 추적 API는 없다. 기사 검색 API는 POST 조건을 일시 처리하며 공개 DB 자료만 조회한다. 관리자 공통 대표 관심사 singleton은 서버 DB에 저장하고 공개 기본값 API로 제공한다. IndexedDB 설정과 로컬 정렬은 내려받은 후보 안에서만 동작한다.

수집의 DNS 검증 결과를 Apache HTTP 연결 resolver에 고정한다. redirect는 같은 공개 HTTPS hostname만 허용한다. worker 결과 저장과 사이트/주소 변경은 site→source 잠금 순서를 사용한다. version과 lease token을 재검사하여 오래된 작업의 반영을 차단한다.

## 기사 원문 미보관
원문·피드 원본·설명문·초록을 별도 DB 칼럼, 파일 캐시, 작업 payload, 수정 이력에 저장하지 않는다. SafeFetch의 응답과 Item.description은 처리 중 메모리 입력이며 Worker는 요약과 메타데이터/링크/해시만 저장한다. 본문 웹페이지/PDF를 별도로 가져오는 기능은 없고 allowBody/allowStore 요청은 서버에서 거부한다. 입력 문자열은 처리가 끝나면 별도 보관하지 않으며 JVM 메모리의 즉시 물리 소거를 보장한다는 의미는 아니다. 기사 수정 이력은 이전 입력 해시만 보관한다.


## 파일 배치와 번역 경계
표준 Gradle src 경로는 유지하고 Java 도메인 패키지(identity/access/catalog/collection/article/edition/translation/classification)로 책임을 나눈다. 운영 지원 파일은 scripts 하위 build/deploy/backup/security/test/governance/translation으로 분류한다. experiments의 Mac 실행기는 과거 비교 재현용이며 운영 Compose에서 참조하지 않는다. 루트에는 빌드와 기본 실행 진입점만 둔다.

worker → 전용 internal network → translator(CPU)로 제목·요약을 전달한다. 모델 초기화는 다운로드 가능한 별도 일회성 서비스이며 실행 모델에는 공개 포트가 없다. 자동 번역 상태/checkpoint는 PostgreSQL에 저장한다. 파일 이동으로 API·DB 스키마·볼륨 이름을 변경하지 않는다. 전체 디렉토리 표는 README, 문서 카테고리는 docs/README.md를 따른다.


## Kubernetes 배치 추가
최신 사용자 요청으로 초기 V1의 Kubernetes 제외를 변경했다. scripts/deploy/helm/moapan에 전체 역할용 chart를 제공한다. Compose는 현 실행 경로, Helm은 향후 서버 배치 경로다. DB StatefulSet/PVC, migrate Job, web/worker/proxy/translator Deployment, 모델 init 다운로드/PVC, ClusterIP/Ingress/NetworkPolicy로 분리한다. 실클러스터·egress 제한·TLS 운영 조건은 차트 README의 미검증 범위를 따른다. CI/CD는 계속 제외한다.

분류: Java worker의 별도 비동기 PostgreSQL 큐 → 내부 classifier:11436(Qwen3.5-4B Q4_K_M) → hash/권리/잠금 재검증 → article_tags. 번역 서비스와 이미지·키·볼륨을 분리한다. 모델이 선택할 주제 목록은 관리자가 운영 DB에 정의한다. 상세 설계와 Compose/Helm 경계는 [분류 설계](classification.md), [도메인 상세설계](domain-design.md)를 따른다.
