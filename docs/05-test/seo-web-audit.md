# SEO·웹 표준 점검 결과서

문서번호: MP-DOC-027 · 작성: 개발 담당 · 검토: 독립 리뷰 담당 · 기준선: 해당 PR 커밋

## 범위와 결과
| 항목 | 조치 | 검증 |
|---|---|---|
| 로그인 정렬 | 제호·폼·약관 링크 동일 중심, 중복 로그인 제목 제거 | 390/1440px 좌표 및 화면 |
| 탭 제목 | 9개 초기 HTML 고유 제목과 브랜드, 언어 전환 중복 제거 | 정적 파서/Playwright |
| 설명·공유 정보 | 페이지별 description/OG 제목·설명, 언어에 맞춰 갱신 | 정적 파서/브라우저 |
| 대표 URL | 공개 안내3페이지 canonical, 서버 Link는 설정 PUBLIC_BASE_URL 사용 | PostgreSQL 통합/실제 HTTP |
| 사이트맵 | about/privacy/terms만 포함, 가짜 lastmod 없음 | XML 응답 테스트 |
| 크롤러 | robots에 사이트맵 제공, 민감 HTML은 crawl 가능하되 noindex | 응답/소스 검사 |
| 개인정보 | 회원/관리/API noindex와 기존 인증 유지 | 통합 테스트 |
| 정적 HTML | lang/charset/viewport/main/ID/초기 제목, 비어 있던 heading/time 수정 | Nu checker9개 HTML |
| JS 미지원 | 초기 브랜드·설명·서비스 안내와 소개 링크 제공 | 소스 점검 |
| 아이콘 | 자체 SVG favicon, 외부 추적 없음 | 같은 origin 자산 |

빈 시간 요소는 span으로 시작하고 확인된 데이터가 있을 때 time/dateTime으로 바꾼다. 임의 현재 시각을 발행일로 표시하지 않는다. 검색어·회원 관심사·광고 클릭은 색인 메타데이터로 전송하지 않는다.

## 검증 도구
Nu HTML Checker 26.10.12 (0a70d65), 공식 validator/validator latest release API의 SHA256과 내려받은 JAR을 대조했다. SHA256: 19f89e204cb881e2d88c5d0cd451df6c1ee8334e7fe626f6694b64a129f6a813. 9개 정적 HTML 검사 오류0/경고0(전체 messages0). 이는 접근성 인증이나 모든 런타임 DOM 상태의 검증을 뜻하지 않는다.

재현: 공식 https://github.com/validator/validator/releases/download/latest/vnu.jar 파일을 .local/html-check/vnu.jar에 준비하고 위 고정 checksum이 일치하는지 확인 후 JAVA_HOME을 선택한 JDK로 지정해 sh scripts/test/html-check.sh 실행. latest 바이너리가 바뀌어 checksum이 다르면 검토 없이 실행하지 않는다. 도구 미설치·실패는 NOT_RUN/실패이며 PASS로 처리하지 않는다.

## 남은 검색 노출 조건
- 검색엔진이 실제로 수집·색인할지와 검색 순위는 보장할 수 없다. Search Console/네이버 서치어드바이저 소유권 등록·제출·색인 결과는 실행하지 않았다.
- 현재 공개 주소는 승인된 IP 주소다. 정식 도메인 확보 시 PUBLIC_BASE_URL과 외부 DNS/TLS를 변경하고 sitemap/canonical을 재확인한다. 임의 도메인/운영자 정보를 만들지 않는다.
- 개별 기사는 자체 고유 URL이 없는 REST/JS 지면이다. 사이트/공개 안내의 발견성을 개선한 범위이며 모든 기사에 개별 색인을 제공하지 않는다. 전체 DB dump·공개 RSS·원문 복제·검색용 인증 우회를 추가하지 않는다.
- robots/noindex는 접근 제어가 아니다. 실제 개인정보 보호는 기존 세션/ADMIN 권한과 공개 DTO가 담당한다.
- 같은 URL의 브라우저 언어 설정으로 표시를 바꾸므로 존재하지 않는 영문 URL/hreflang을 만들지 않는다.

## 공식 근거
- [Google 제목 링크](https://developers.google.com/search/docs/appearance/title-link)
- [JavaScript SEO](https://developers.google.com/search/docs/crawling-indexing/javascript/javascript-seo-basics)
- [noindex와 crawl 허용](https://developers.google.com/search/docs/crawling-indexing/block-indexing)
- [대표 URL과 HTTP Link](https://developers.google.com/search/docs/crawling-indexing/consolidate-duplicate-urls)
- [Nu HTML Checker](https://validator.github.io/validator/)

후속 로그인 필수 정책: index는 인증 화면이므로 noindex로 전환하고 sitemap/대표URL 목록에서 제외했다. 검색엔진에는 소개·약관의 공개 안내만 제공하며 뉴스 인증을 색인 때문에 우회하지 않는다.
