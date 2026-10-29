# mj_proto_assist 개발 로드맵

현재 단계는 기획·설계 문서 공개다. 아래 단계는 후속 개발 계획이며 완료된 기능이 아니다. 기간은 기술 실험 후 산정한다.

| 단계 | 산출물 | 완료 조건 |
|---|---|---|
| 0 설계와 비교 | PRD·설계·연동 계약, 기존 도구 조사 | 일반 HTTP Protobuf 시나리오·차별화 범위 확정 |
| 1 Core 실험 | Gradle Kotlin 프로젝트, descriptor loader, codec, fixtures | F01~F04, int64·presence·WKT·unknown field·입력 제한 검증 |
| 2 CLI와 HTTP | types·encode·decode·call·stdio, 합성 echo 서버 | F05~F07·S01, 상태별 오류·바이너리 stdout·네트워크 실패 검증 |
| 3 ApiLens 통합 | Dart 어댑터와 실제 ApiLens 통합 변경 | I01·C01, bytes 보존·취소·재시작·기존 JSON 회귀 통과 |
| 4 독립 UI | Spring Boot loopback API, React 화면, 컬렉션 | F08~F09, 로컬 인증·Origin·비밀 제외 저장 검증 |
| 5 mj_fav 검증과 베타 | Kotlin/TypeScript fixture, 패키징·체크섬·사용 가이드 | I02, 두 사용처의 실제 시나리오, 의존성 라이선스 검토 |

초기 독립 UI보다 Core·CLI·ApiLens 연동을 먼저 검증한다. 외부 API 호출에 의존하지 않는 합성 테스트 서버를 제공해 공개 기여자가 계정·토큰 없이 재현할 수 있게 한다.

## 출시 전 결정

- 지원 JDK·Kotlin·Spring Boot·Protobuf·protoc 버전 조합과 업그레이드 절차
- TypeScript fixture 코드 생성기, descriptor 생성 명령·재현성
- 운영체제별 JVM 동봉·설치·배포·코드 서명
- payload·schema·메모리 상한의 성능 평가
- 초기 지원 WKT 목록과 지원하지 않는 기능의 오류 코드
- ApiLens 실행 파일 탐색·업데이트·프로토콜 버전 정책
- 관리 API JSON Schema와 IPC 문서의 자동 검증 방식

## 베타 판정

최소 한 개의 일반 HTTP Protobuf API에서 요청 작성·전송·오류 해석을 수행하고, ApiLens 데스크톱과 CLI가 동일 fixture를 해석해야 한다. 성능 목표는 PRD의 실험 가설로 관리한다. 실제 데이터 유출·TLS 검증 우회·공개 로컬 API 바인딩 문제는 출시 전에 해결한다. 문서 단계에서는 실행·성능·통합 완료를 주장하지 않는다.
