# 기사 번역문 포장 표시 회귀

기본 운영 점검에서 일부 한국어 제목과 요약에 `{"source_text":"번역문"}`이 그대로 표시되는 현상을 확인했다. 저장된 번역의 단일 `source_text` 문자열 포장만 화면에서 풀어 표시한다. 원문 언어, 잘못된 JSON, 빈 값과 다른 JSON 구조는 그대로 유지한다. 출력은 기존 textContent 경로를 사용하며 원문과 번역 DB, 개인 설정, AI 정책 어댑터를 변경하지 않는다.

검증: `node --test scripts/test/article-text.test.mjs` 4개 통과. 번역/원문 불변성, 불완전·다른 형태 JSON, 일반 텍스트, HTML 유사 문자열과 불완전 번역 fallback을 확인한다. 실제 HTTPS 지면과 검색 결과를 열어 문제를 재현했고 수정 후 한국어 제목·요약, 정적 자산과 콘솔 오류 부재를 확인했다.

현재 working tree의 미커밋 사업·뉴스레터 변경을 운영에 섞지 않기 위해 기존 운영 JAR 사본에서 해당 JS 항목만 교체했다. 다른 모든 JAR 항목의 바이트 일치와 배포 자산 SHA256 일치를 검사했다. Java class·정책14개·의존성·worker·DB는 바뀌지 않는다. 공통 flock 안에서 웹 컨테이너만 새 이미지로 교체하고 health와 다른 컨테이너의 ID/시작 시각 보존을 확인했다. 공개 HTTPS 자산 확인은 성공했고 로컬 HTTP 포트의 HTTPS redirect 경유 검사 오류는 공개 주소로 재확인했다.

플랫폼 이미지는 `platform/damgox2-app:ubuntu26.04-20261109-r2`이며 기존 r1 레이어를 공유한다. 재빌드 시 미커밋 변경을 포함하지 않도록 검토된 commit을 사용하고 새로운 릴리스 태그를 발급한다. 운영 이미지·SBOM·스캔·잠금과 원본 JAR 보존 증거는 로컬 전용 operations에 둔다. 새 이미지 HIGH/CRITICAL 0이며 Ubuntu 미수정 MEDIUM/LOW는 남아 있다. 기존 이미지와 데이터를 복구용으로 보존한다.
