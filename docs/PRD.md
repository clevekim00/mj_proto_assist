# mj_proto_assist 제품 기획서

작성일: 2026-10-29. 상태: 초기 구현 진행, 전체 MVP 수용 전.

## 2026-10-29 초기 구현 상태

이번 구현은 descriptor 기반 로컬 도구의 첫 동작 경로입니다. 아래의 원래 MVP 요구사항과 설계는 후속 목표도 포함하며, 표에 없는 기능의 완료를 뜻하지 않습니다.

| 요구사항 | 현재 구현과 검증 범위 | 남은 수용 기준 |
|---|---|---|
| F01–F04 | descriptor 등록·타입 탐색·encode/decode, import 의존성·중복·proto2 거절, int64·bytes·optional·oneof·map·중첩·unknown field 테스트 | .proto 컴파일 어댑터, 전체 WKT·Any·enum fixture 확대, 상세 오류 경로 |
| F05–F06 | HTTPS/명시 HTTP POST, bytes 보존, 타임아웃, 10MiB 상한, redirect·자동 재시도 없음, raw 결과·상태별 디코딩 | 실행 중 취소, 압축 응답 지원 |
| F07 | types·encode·decode·serve-stdio, 바이너리 stdout·진단 stderr 분리 | CLI call, 세분화 exit code |
| F08 | React UI, 동일 origin Spring Boot, bootstrap 세션·Origin/Host 검증, schema·편집·전송·오류·raw 탭 | 취소 UI, 세션 재접속 UX·자동 UI 회귀 테스트 확대 |
| F09 | 자동 저장 없음 | 컬렉션·환경변수 저장 및 재실행 전체 |
| I01/I02/C01 | IPC major 1 검사, 합성 fixture, stdio 변환 계약 테스트 | Dart 어댑터·실제 ApiLens/mj_fav·TypeScript 검증, 동시성·취소·재시작 |
| S01 | 로그에 대상 본문·헤더 미기록, 오류 원문 비반사, 로컬 인증·Origin 차단 테스트 | 공개 베타 전 추가 보안 검토 |

실행·테스트 절차는 [README](../README.md)를 따릅니다. 런타임 동봉 배포나 성능 목표 달성은 아직 주장하지 않습니다.


## 1 문제와 대상

일반 HTTP API에서 요청과 응답을 Protobuf 바이너리로 주고받으면 원문을 눈으로 읽거나 JSON 요청을 바로 보내기 어렵다. mj_proto_assist는 타입 선택, 요청 작성, 전송, 응답 확인을 하나의 로컬 도구로 연결한다.

우선 대상은 Kotlin/Spring Boot 백엔드와 TypeScript 또는 Dart 클라이언트를 개발하는 개발자다. mj_fav를 실제 API 검증 사례로, ApiLens를 외부 개발 도구 통합 사례로 삼는다. 시장 수요나 기존 제품 대비 우위는 아직 검증되지 않았으며 공개 베타 전에 대체 도구 비교를 수행한다.

## 2 제품 원칙

- 대상 프로토콜은 일반 HTTP와 Protobuf다. gRPC 기능으로 혼동하지 않게 표시한다.
- 메시지 구조를 추측하지 않는다. 정확한 디코딩에는 schema와 완전한 메시지 타입 이름이 필요하다.
- 데이터 변환과 HTTP 실행은 독립 기능이다. 오프라인 디코딩에 네트워크가 필요하지 않게 한다.
- 공통 Core는 화면·mj_fav·ApiLens에 의존하지 않는다.
- ApiLens는 독립 UI를 삽입하지 않고 기존 화면에서 Core의 기능을 호출한다.
- 분석용 원문 수집·클라우드 계정·중앙 저장 서버를 MVP에서 요구하지 않는다.

## 3 주요 흐름

1. 스키마 등록: descriptor set을 선택하거나 `.proto`와 import 루트를 지정한다. 컴파일 결과에서 메시지 목록을 보여주고 요청·성공·오류 타입을 선택한다.
2. 요청 작성: ProtoJSON을 입력한다. 필드 오류를 경로와 함께 표시하고 인코딩된 바이트 크기를 확인한다.
3. API 실행: URL·헤더·타임아웃·환경변수를 설정하고 전송 버튼을 누른다. POST에 Protobuf 본문을 담아 전송한다.
4. 응답 확인: HTTP 상태·헤더·크기·시간과 디코딩된 JSON을 표시한다. 실패해도 원시 바이트를 확인할 수 있다.
5. 파일 변환: 저장된 바이너리와 타입을 선택해 JSON으로 변환하거나 JSON 파일을 바이너리로 저장한다.
6. 재사용: 비밀정보를 제외한 요청 설정을 컬렉션으로 저장하고 환경변수로 자격증명을 주입한다.
7. ApiLens 연동: 보조 프로세스를 시작해 schema를 등록한 뒤 기존 요청·응답 화면에서 인코딩·디코딩한다. HTTP 전송은 ApiLens가 계속 담당한다.

## 4 MVP 요구사항

| ID | 요구사항 | 수용 기준 |
|---|---|---|
| F01 | descriptor set 및 `.proto` 등록 | import·중첩 타입 해석, 누락·중복 심벌 진단, 실패 시 기존 schema 유지 |
| F02 | 타입 탐색 | 완전한 타입 이름·필드·enum 표시, schema별 격리 |
| F03 | ProtoJSON 인코딩 | scalar·nested·repeated·map·oneof·optional·주요 well-known types 검증 |
| F04 | Protobuf 디코딩 | 지정 타입으로 JSON 출력, 잘못된 바이트 오류, unknown field 존재 경고 |
| F05 | HTTP POST 호출 | 바이너리 보존, 인증 헤더, 타임아웃·취소, TLS 기본 검증, 자동 재시도 0 |
| F06 | 성공과 오류 분석 | 상태별 응답 타입 매핑, 타입 누락·JSON/HTML 오류·빈 본문에서도 원시 결과 유지 |
| F07 | CLI | types·encode·decode·call·serve-stdio 계약, 바이너리 stdout과 진단 stderr 분리 |
| F08 | 독립 로컬 UI | schema 선택·요청 편집·전송·결과·오류 상태 제공, 로컬 API 보호 |
| F09 | 요청 저장과 재실행 | 버전 있는 컬렉션, schema 식별, 환경변수 참조 보존, 비밀 기본 제외 |
| I01 | ApiLens 연결 | Dart 어댑터가 프로세스와 handshake·변환·취소·재시작 처리, 기존 HTTP 흐름 유지 |
| I02 | mj_fav 연결 | 합성 schema·샘플 payload로 Kotlin/TypeScript 상호운용, 오류·revision·64비트 수 검증 |
| S01 | 민감정보 보호 | 기본 로그·내보내기에 토큰·원문 없음, 저장·전송 범위 확인 |
| C01 | 호환성 | 프로토콜 major 불일치 거절, 지원되지 않는 schema 기능 명시적 거절 |

## 5 지원 범위와 비목표

MVP는 proto3와 명시적으로 검증한 well-known types를 지원한다. proto2, Editions, 사용자 정의 option 처리, 커스텀 확장, gRPC reflection은 후속이다. descriptor에서 지원하지 않는 문법·기능을 발견하면 조용히 무시하지 않는다.

입력 JSON은 임의 JSON이 아니라 ProtoJSON 규칙을 따른다. int64는 문자열, bytes는 Base64, 필드 presence와 oneof를 보존한다. JSON 편집을 통한 재인코딩은 원본 바이너리와 바이트 단위 동일성을 보장하지 않으며 unknown field가 손실될 수 있다.

패킷 스니핑·HTTPS 가로채기·브라우저 확장 자동 캡처·부하 테스트·협업 클라우드·계정 동기화·프로덕션 프록시는 MVP에서 제외한다. 기존 ApiLens의 오케스트레이션을 복제하지 않는다.

## 6 UX와 실패 처리

schema 미등록, 타입 미선택, 입력 오류, 전송 중, 취소, 네트워크 실패, 디코딩 실패를 구분한다. 바이너리가 디코딩되었다는 이유만으로 올바른 타입이라고 보장하지 않는다. Protobuf wire 형식상 다른 타입도 우연히 파싱될 수 있으므로 사용자가 선택한 타입을 항상 표시한다.

파괴적인 API 호출도 가능하므로 전송 대상·메서드·환경 이름을 실행 전에 보여준다. 저장이나 단순 디코딩이 네트워크 호출을 일으키지 않게 한다. 비밀정보가 포함된 원시 파일 저장은 별도 사용자 동작으로 한다. 예제와 화면 캡처에는 합성 데이터만 사용한다.

## 7 품질 목표

초기 성능 가설은 warm 상태에서 1MiB 메시지 인코딩·디코딩 각각 p95 300ms 이내다. HTTP 시간과 스키마 컴파일은 별도 측정한다. 장비·런타임·샘플 구조를 고정하고 중첩·대형 repeated·bytes fixture를 포함한다. 첫 출시의 최대 본문은 10MiB, 재귀 깊이는 64를 제안하며 초과를 명시적으로 거절한다.

출시 조건은 실제 서비스 원문 없이 전체 시나리오 재현, 비밀 로그 누출 0, Kotlin/TypeScript fixture 상호운용, ApiLens Dart 어댑터 계약 통과다. 사용량 텔레메트리는 기본 수집하지 않는다. 성과는 실제 개발 과제의 호출 성공률·문제 진단 시간과 기여자 피드백으로 평가한다.

## 8 공개와 미결정

MIT 라이선스로 공개하고 초기 저장소는 문서부터 시작한다. 제품 구현·ApiLens 코드 변경·패키지 배포는 후속 작업이다. 초기 CLI·보조 프로세스는 JVM 실행을 기준으로 하며 OS별 런타임 동봉, 코드 서명, 패키지 레지스트리와 설치 경로는 배포 실험에서 결정한다.

미결정 사항은 TypeScript 코드 생성 도구, 데스크톱 배포 방식, 정식 프로세스 배포 버전, HTTP 프록시·클라이언트 인증서 지원, 비밀정보 OS 키체인 저장, 기존 도구와 비교한 차별화 범위다. 공개 저장소 생성은 이 기능들이 이미 구현됐음을 뜻하지 않는다.
