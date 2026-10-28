# 메뉴·접근 권한 전수 점검

현재 정적 HTML 전부와 공통 JS 생성 메뉴를 guest/USER/ADMIN 기준으로 확인한다. 스크립트 로딩 전에는 보호 메뉴/본문을 hidden으로 시작한다. 메뉴 숨김은 서버 권한 검사를 대신하지 않는다.

|화면/메뉴|비회원|회원|관리자|
|---|---|---|---|
|index 관심지면|공개30개·대표 관심사|공통 후보·개인 설정 없으면 대표값|회원과 동일|
|index 기사 검색|dialog 로컬 검색|동일|동일|
|관심사 설정 링크·settings.html|링크 숨김·직접 방문 로그인 이동|표시/수정·파일 이전|동일|
|계정 관리 링크·account.html|링크 숨김·직접 방문 로그인 이동|자기 계정만|자기 계정만|
|관리 링크·admin.html|숨김·로그인 이동|숨김·지면 이동|대표 관심사/수집/기사/태그 운영|
|상단 로그인·가입|표시|계정 메뉴로 교체|계정 메뉴로 교체|
|login.html/signup.html|가입·로그인 폼|직접 방문 가능|직접 방문 가능|
|about.html|공개 안내|공개 안내|공개 안내|
|terms.html/privacy.html|현재 판과 이전 약관 링크|동일|동일|
|policy-history.html|과거7개 판 링크|동일|동일|
|policies/terms-1..3.html|이전 본문·현재 판/이력 이동|동일|동일|
|policies/privacy-1..4.html|이전 본문·현재 판/이력 이동|동일|동일|
|기사 더 보기/덜 보기 태그 버튼|숨김|브라우저 설정만 변경|동일|
|추가 후보 불러오기|숨김|서명 cursor·quota 적용|동일|
|전체지면 탭/일반 최신 구역|제거|제거|제거|

실제 검사: BrowserTest.guestMenusProtectedPagesAndSearchDialog, memberAndAdminMenusAndRepresentativeEditor, defaultInterestsAndPolicyEditionsAreExplicit. 모든 정적 정책 이력 URL을 포함해 반복 방문한다. 서버 직접 방문/권한은 SecurityIntegrationTest.publicEditionIsBoundedAndProtectedPagesStayPrivate 및 representativeInterestsRequireAdminCsrfAndCurrentVersion에서 별도 검사한다. 검색 dialog는390/768/1440px에서 넘침·닫기·적용·Escape·초기화 및 검색어 비전송을 검사한다.
