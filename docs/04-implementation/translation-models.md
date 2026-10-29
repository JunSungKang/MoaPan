# 로컬 번역 모델 선정

> 문서번호: MP-DOC-011 · 기준: 저장소 현재 리비전 · 상태: 유지관리 · 작성: 개발 담당 · 검토: 독립 리뷰 담당

확인: 2026-10-29 KST. 목적은 카드의 허용된 제목·요약을 한국어↔영어로 번역하는 것이다. 전 세계 모델의 절대 순위를 주장하지 않는다.

## 공식 공개 후보

|후보|규모|공식 근거와 판단|
|---|---|---|
|Google TranslateGemma|4B|55개 언어 WMT24++에서 COMET81.6, MetricX5.32를 공개. 다른 발표사의 점수와 평가 설정이 달라 직접 순위로 환산하지 않는다. 최초 후보였지만 최상위 확인 없이 제안했던 모델이다.|
|Tencent Hy-MT2|1.8B|2026-05-21 정식 공개, 한국어를 포함한33언어, Apache2.0, 제작사 공식 GGUF. 작고 빠른 비교 후보.|
|Bilibili Index-Translate|2B|2026-09-30 공개,10-03 공식 양자화 배포. Apache2.0, 공식 GGUF와 번역 지시/용어 지원. 1차 후보. TranslateGemma4B 동일 조건 실측 후 재선정한다.|
|Qwen3.5|2B 일반 모델|같은 제작사 비교표에서 번역 전용 Index보다 낮아 우선순위 제외. 일반 대화 성능과 번역 성능은 구분.|
|7B·9B·12B 및35B-A3B preview|더 큰 모델|이번 경량 조건과 운영 자원 우선순위로 제외. MoE 활성 파라미터가 작아도 전체 가중치 메모리가 작다는 뜻은 아니다.|

Index 발표사의 동일 비교표: FLORES COMET22는 Index2B0.8655/Hy1.8B0.8522, WMT24++는0.8489/0.8401, WMT26 Judge는60.26/49.35. 발표사 자체 평가이며 독립 검증·한국어 전문 분야만의 결과가 아니다. TranslateGemma4B는 이 표에 없어 직접 비교 우승을 주장하지 않는다.

공식 출처:
- https://huggingface.co/google/translategemma-4b-it
- https://huggingface.co/tencent/Hy-MT2-1.8B
- https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF
- https://github.com/bilibili/Index-Translate
- https://github.com/bilibili/Index-Translate/blob/main/docs/evaluation.md
- https://huggingface.co/IndexTeam/Index-Translate-2B-GGUF

## 실제 Mac 표본 평가

Apple Silicon/24GiB, 동일 llama.cpp b11960, Metal, context4096, 병렬1. 합성 기술 문장8개(영→한6/한→영2)를 사용했다. 모델별 공식 권장 prompt/decoding을 사용한 운영 비교이며 통계적 벤치마크나 동등 sampling조건의 과학적 우열 증명은 아니다. 첫 요청 포함 단일 실행, 온도 Index0/Hy0.7. 외부 API·외부 채점 모델 미사용.

- Index 기본:0.86~1.59초/문장. median과 부정/제한 표현·수치 유지. RAG 역방향 용어, readers, approximate nearest neighbor는 어색함.
- Hy 기본:0.83~2.11초/문장. median을 평균으로 오역한 표본, 근사 최근접 이웃 개념 축소, RAG 역방향 용어/인과 관계 오류 관찰.
- Index+용어표:0.99~1.81초/문장. 위 용어 오류 개선, 표본 수치 유지. 문맥상 미묘한 뉘앙스까지 정확하다고 보장하지 않는다. 수치 검사가 의미 오류를 모두 검출하지는 못한다.
- TranslateGemma Ollama 공식 registry 배포본은 checksum 일치했지만 llama.cpp에서 gemma3 layer_norm_rms_epsilon 메타데이터 누락으로 로드 실패. 품질0점으로 처리하지 않고 호환성 미검증으로 분리했다. Google 모델 자체의 품질 실패가 아니다.

1차 잠정 선정(재검토 중): **Index-Translate-2B Q8_0**. 2,076,674,560bytes(약2.08GB), 프로세스 RSS 관측 약2.2GB(전체 GPU/운영체제 메모리 총량과 다름). 극단적 저비트 대신 공식 Q8으로 양자화 손실을 줄인다. 용어표는 입력의 의미를 바꾸기 위한 재작성 규칙이 아니라 번역 용어 지시다.

## 고정 공급망

- 모델 revision:3a0eca6ce931d88d00614d427ab3ed8ff0743902
- 모델 SHA256:a09d287f41d297206947a2e51acc29dd03c8b22356111fdb1e289c0c97c173f3
- 공식 파일: https://huggingface.co/IndexTeam/Index-Translate-2B-GGUF/resolve/3a0eca6ce931d88d00614d427ab3ed8ff0743902/Index-Translate-2B.Q8_0.gguf
- llama.cpp stable v0.8.0이 지정한 b11960, commit8642541b255bfb5dc39cd351a178be09013a55d6, MIT.
- arm64 archive SHA256:58e5a5e1d7c2bf66cedd9c29e0323f3e570660a70a1e2156d32eed08f4cc1e3c
- https://github.com/ggml-org/llama.cpp/releases/tag/v0.8.0
- https://github.com/ggml-org/llama.cpp/releases/tag/b11960

공식 체크섬과 실제 파일을 대조했다. 런타임 advisory 검토는 evidence/translation-runtime-security.json. 원격 연결·RPC·모델 도구·웹UI를 사용하지 않는다. API key는 workspace 비추적 파일, 모델 API는127.0.0.1만 listen. 모델 서버에는 회원정보·관심사·검색어를 보내지 않는다.

## 최종 사용자 결정 (2026-10-29)
사용자가 Index Translate 2B Q8_0 및 원문에 등장하는 용어만 적용하는 용어표를 선택했다. 이전의 잠정 선정/호환성 미검증 상태를 대체한다. Gemma는 공식 Ollama v0.40.12 번들 llama-server로 호환 문제를 해결해 실제 비교했고, 품질 실패로 로드 실패를 계산하지 않았다.

같은 번들 실행기/context4096/parallel1/temp0, 14개 합성 문장(영→한10/한→영4)×2회×기본/용어표로 168회. Index/GemmaQ4/GemmaQ8 기본 중앙값0.985/1.6645/2.077초, P95 1.719/2.534/3.191초. 용어표 중앙값1.025/1.8675/2.314초. 전체 최대RSS 2.1144/2.6762/4.2429GiB. 파일2.0767/3.2989/4.9465GB. RSS는 전체 GPU/시스템 메모리와 다름. 모든 호출정상종료, 반복별동일출력. 작은 표본이며 사후 선택한 5개 의미검토 항목을 일반 정확도로 해석하지 않는다. 전체 출력은 evidence/translation-comparison-*.json.

운영은 Index와 호환되는 공식 upstream b11960을 사용한다. 비교에 사용한 Ollama 번들과 실행기가 다르므로 운영속도가 위 수치와 동일하다고 보장하지 않는다. 운영 prompt는 버전관리된 translation/glossary.tsv 35항목을 단어경계/한국어조사로 매칭해 사용한다. 용어표는 종합 정확성 보장이 아니며 수치·CVE 검증 실패는 원문으로 유지한다. 모델 API에 원문 전문·회원정보·개인 관심사·검색어를 보내지 않는다.
