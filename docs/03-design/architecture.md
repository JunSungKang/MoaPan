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

## 신문 지면과 광고 자리
정적 index는 중앙 제호·넓은 머리기사·선으로 구분한 다단 지면을 사용한다. 7개 기사 슬라이드와 검색은 유지한다. 우측상단 광고영역 아래에 언어 선택과 검색을 배치한다. assets/js/shared/config.js의 advertisement.imageUrl(같은 origin /assets/ 경로), linkUrl(HTTPS), alt로 교체한다. 미설정은 비클릭 광고문의 자리다. 광고 추적 SDK/클릭 중계/원격 이미지 호출은 사용하지 않는다. 광고이미지의 이용권리와 목적지 정책은 등록자가 확인한다. 사용자 요청에 따라 기존 V1의 광고 제외에서 정적 광고 자리만 추가한 범위다.

advertising 기능은 api/AdvertisingController → application/AdvertisingSettings → PostgreSQL 설정 1행으로 구성한다. 브라우저 advertising/banner.js는 검증된 설정으로 DOM만 생성하며 추적·원격 SDK 의존성이 없다. administration.js는 관리자 설정/미리보기 담당이다. 기존 config.js 광고 설정은 DB로 대체했다.

newsletter: NewsletterController → NewsletterService → PostgreSQL newsletters. 정적 newsletter.html/list·detail과 newsletter-editor.html/admin block editor를 분리한다. newsletter/render.js가 안전한 텍스트 DOM과 제한된 자체 이미지·HTTPS 원문 링크만 출력한다. 개인화·수집·번역 모델의 실행과 독립된 관리자 집필 기능이다. 업로드 이미지는 브라우저 JPEG 변환·축소 후 JSONB snapshot에 포함하므로 DB 백업과 함께 이관한다.


## 공개 뉴스 경계
GET / 및 /index.html과 명시적 공개 edition/tags/interests, POST 공개 검색만 비회원 뉴스 경로다. 서버는 뉴스레터·계정·회원 전용 뉴스 API의 인증과 관리자 권한을 계속 검사한다. 공개 edition은 전역600회/분과 전체 API3000회/분, 검색은 전체120회/분을 PostgreSQL로 제한한다. 회원이 공개 경로를 호출해도 회원 quota를 적용한다. 폐기/만료 계정의 세션은 공개 뉴스 요청에서 익명으로 전환하고 보호 요청에서401 또는 로그인 이동으로 거부한다. 브라우저는 로그인 확인 후 비회원에게 공통 관심사만 적용하고 뉴스레터의 소개·본문을 요청하지 않는다.

## 전 페이지 공통 UI 계층
newspaper.css는 신문 지면·기본 폼, auth.css는 간결한 인증 컬럼, newsletter.css는 잡지 본문/원고 작업 배치를 담당한다. 모든11개 HTML의 마지막 stylesheet인 site.css가 사이트 공통 글꼴 토큰·콘텐츠 메뉴·계정 메뉴·언어 선택·보조 헤더·푸터를 소유한다. 페이지 전용 CSS에 공통 콘텐츠 메뉴의 font/display/gap를 다시 정의하지 않는다. 공통 UI는 system-ui 계열, 읽기 본문/제목은 명조 계열, 브랜드는 Georgia 계열로 역할을 구분한다. 메인은 큰 제호/광고/검색을 유지하고 비인증 폼은 중앙 정렬을 유지하며, 나머지8개 페이지는 동일 compact header 구조를 쓴다. 인증에 따른 링크 생성/삭제와 로그아웃 처리는 shared/common.js, 언어 변환은 shared/i18n.js에 있다. 외부 폰트·UI 프레임워크·템플릿을 추가하지 않는다.

새 페이지는 동일 header/footer 구성과 site.css를 마지막에 연결하고, H1/하위 제목 순서·본문 건너뛰기·공통 메뉴의 실제 간격/글꼴·guest/member/admin·한/영·390/768/1440 검증 행렬에 포함한다. 링크 간격 회귀의 하한은12px이고 현재 설계의 실측 최솟값은16px이다. 신문/잡지/작업 화면의 본문 배치를 동일하게 만드는 규칙은 아니다.

관리자 작업 화면은 admin.css와 administration/navigation.js가 소유한다.6개 주메뉴와 수집/주제의 하위탭은 기존 form·입력·저장 handler를 유지한 채 hidden/ARIA 선택 상태만 바꾼다. URL 해시는 정해진 화면 식별자만 사용하고 미저장 내용은 DOM 메모리에만 남긴다. 새로고침 복구·서버 설정 저장이나 추가 권한을 제공하는 기능은 아니다. 기술 점검/미리보기/수집 접수 결과는 수집주소 작업영역에서 접힘 바깥에 표시한다.
