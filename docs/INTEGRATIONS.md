# mj_fav와 ApiLens 연동 계획

작성일: 2026-10-29. 상태: 연동 제안. 두 프로젝트 코드는 이번 저장소 초기화에서 변경하지 않는다.

## 1 공통 경계

mj_proto_assist는 schema 로딩·메시지 탐색·변환·일반 HTTP 요청을 제공한다. 북마크·계정·요약·워크플로우·과금 정책은 소비 프로젝트에 남긴다. 제품의 실제 토큰·북마크·본문·요청 기록을 예제나 테스트 fixture로 복사하지 않는다.

## 2 mj_fav

mj_fav는 React/TypeScript 확장과 Spring Boot/Kotlin 서버를 설계 중이며 런타임 구현은 아직 없다. 일반 HTTP POST + Protobuf 요청·응답의 검증 도구로 mj_proto_assist를 사용한다. 전송 계약 도입은 mj_fav 저장소의 별도 변경으로 진행한다.

프로토콜 원본은 mj_fav의 `contracts/proto/`에 둔다. CI가 import를 포함한 descriptor set과 합성 샘플을 만들고 개발자가 mj_proto_assist에 등록한다. 실제 운영 payload는 공유하지 않는다. 도구가 mj_fav schema를 내장하거나 릴리스 버전에 종속하지 않게 한다.

첫 검증 API는 합성 `SyncPushRequest`, `SyncPushResponse`, `ApiError`다. UUID·requestId·revision·반복 태그·메모 optional을 포함한다. TypeScript 인코딩 → Kotlin 디코딩, Kotlin 응답 → 도구 디코딩, 도구 인코딩 → Kotlin 처리의 왕복을 확인한다. 64비트 revision은 JSON에서 문자열로 보존한다.

확장 런타임에서 Kotlin 보조 프로세스를 호출하지 않는다. 확장은 자체 생성 TypeScript codec으로 서버와 통신하고, mj_proto_assist는 개발자 PC에서 별도 실행한다. Node.js 서버를 추가할 이유가 없다.

## 3 ApiLens

로컬 프로젝트의 pubspec에서 Flutter/Dart, Riverpod, Dio 사용을 확인했다. 이는 어댑터 방향을 정하기 위한 구성 확인이며 내부 네트워크 클래스·플러그인 API 검토는 구현 전에 별도로 수행한다.

ApiLens 데스크톱은 Kotlin 보조 프로세스에 변환만 위임한다. 요청 편집·환경변수·HTTP 실행·워크플로우는 기존 ApiLens가 담당한다. TypeScript 라이브러리나 Kotlin/JVM 라이브러리를 Dart에 직접 import하지 않는다.

권장 Dart 경계는 `ProtoAssistClient` 인터페이스로 `connect`, `registerSchema`, `listTypes`, `encode`, `decode`, `cancel`, `close`를 제공하는 것이다. 앱의 UI 코드와 프로세스 관리를 분리하고 실제 연결 클래스명·파일 위치는 ApiLens의 기존 구조를 확인한 뒤 정한다.

1. 사용자가 보조 프로세스 실행 파일을 선택하고 버전·capability handshake를 수행한다.
2. descriptor를 등록하고 요청에 schema reference와 요청·응답·오류 타입을 연결한다.
3. ProtoJSON 편집값을 encode하여 `Uint8List`로 받는다.
4. Dio의 요청과 응답을 bytes 모드로 설정해 자동 JSON 직렬화를 우회한다.
5. 상태별 응답 타입을 선택해 decode하고 기존 뷰어에서 JSON을 표시한다.
6. 해석 실패 시 HTTP 결과와 raw bytes를 유지한다. 프로세스 재시작 시 schema를 재등록한다.

ApiLens 웹은 로컬 프로세스를 직접 시작할 수 없으므로 MVP 자동 연동 대상에서 제외한다. 후속으로 사용자가 실행한 로컬 bridge의 인증·CORS·브라우저 접근 정책을 별도 검증하거나 다른 codec 배포 방식을 검토한다. 데스크톱 성공을 웹 지원으로 표시하지 않는다.

## 4 호환성과 검증

| 항목 | 책임 |
|---|---|
| 대상 API schema | mj_fav 등 API 소유 프로젝트 |
| 디코더·ProtoJSON 규칙 | mj_proto_assist Core |
| IPC version과 한도 | mj_proto_assist 공개 계약 |
| ApiLens 프로세스 수명·UI·HTTP | ApiLens 어댑터 |
| 인증 토큰·재시도·멱등성 | 소비 프로젝트의 HTTP 정책 |

ApiLens에서는 기존 JSON 요청·컬렉션·워크플로우 회귀 검증을 먼저 유지하고 Protobuf 모드를 선택한 요청에만 어댑터를 적용한다. 타입 연결을 저장할 때 descriptor hash를 함께 기록한다. 보조 프로세스 미설치·지원 안 하는 major 버전·schema 변경·취소·다중 요청·충돌하는 id·큰 본문을 계약 시나리오로 검증한다.

연동 완료 기준은 문서나 예제가 존재하는 것이 아니라 실제 ApiLens 데스크톱에서 합성 POST 요청을 보내 성공·오류 응답을 해석하고, 보조 프로세스 종료 후 안전하게 복구하는 것이다. 실제 통합 PR은 각 저장소에서 별도로 작성한다.
