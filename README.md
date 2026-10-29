# mj_proto_assist

일반 HTTP API의 Protobuf 요청과 응답을 작성하고 확인하는 로컬 개발 도구입니다. JSON 입력을 Protobuf로 인코딩해 HTTP POST로 보내고, 응답을 읽기 쉬운 JSON으로 디코딩하는 것을 목표로 합니다.

**현재는 초기 구현 단계입니다. Kotlin Core, 오프라인 CLI·stdio, Spring Boot 로컬 API, React 웹 UI가 있습니다. 전체 MVP 및 ApiLens 실제 연동은 아직 완료되지 않았습니다.**

## 실행

검증 환경: JDK 25, Node.js 24, Python 3. Gradle 9.2.1 wrapper를 포함합니다. 첫 빌드에는 Maven Central·npm·Gradle 배포 서버 접근이 필요합니다.

```bash
./scripts/run-ui.sh
```

터미널에 출력되는 `http://127.0.0.1:<임의포트>/#bootstrap=...` 전체 URL을 엽니다. 세션 키는 메모리에만 두므로 새로고침 후에는 서버를 다시 시작해 새 URL로 접속합니다. UI는 Spring Boot가 동일 origin으로 제공합니다.

1. `examples/echo.pb`를 업로드합니다.
2. 요청과 성공 응답 타입을 `sample.v1.Echo`로 선택합니다.
3. 기본 ProtoJSON을 인코딩하거나 Base64를 디코딩합니다.
4. 별도 터미널에서 `python3 scripts/example_server.py`를 실행합니다.
5. 대상 URL을 `http://127.0.0.1:8787/echo`로 설정하고 **개발용 HTTP 전송 허용**을 선택한 뒤 POST를 실행합니다.

오류 타입을 지정하면 4xx/5xx Protobuf 응답을 해석합니다. HTML·빈 응답·디코딩 실패에서도 HTTP 상태·헤더·원본 Base64를 보존합니다. 대상 API 호출은 POST 버튼을 누를 때만 발생하며 인증 헤더·본문은 자동 저장하지 않습니다.

### CLI와 stdio

```bash
./gradlew :cli:installDist
cli/build/install/proto-assist/bin/proto-assist types --descriptor examples/echo.pb
cli/build/install/proto-assist/bin/proto-assist encode --descriptor examples/echo.pb --type sample.v1.Echo --in examples/request.json --out /tmp/request.bin
cli/build/install/proto-assist/bin/proto-assist decode --descriptor examples/echo.pb --type sample.v1.Echo --in /tmp/request.bin --out -
cli/build/install/proto-assist/bin/proto-assist serve-stdio
```

stdio는 JSON Lines protocol major 1을 사용하며 HTTP 전송을 노출하지 않습니다. `system.hello`, schema 등록·조회·검사·해제, encode/decode, shutdown을 지원합니다. 현재 순차 처리이며 취소·프로세스 재시작을 처리하는 Dart 어댑터는 후속입니다.

### 테스트

```bash
./scripts/test.sh
```

JUnit, UI 단위 테스트, 실제 로컬 API와 합성 대상 서버를 사용하는 통합 smoke 테스트를 실행합니다. UI 빌드 후 서버 패키지를 생성해야 최신 화면이 포함됩니다. 개별 검증은 `./gradlew test`, `cd ui && npm test`, `python3 scripts/smoke.py`를 사용합니다.

### 현재 범위와 제한

- descriptor set 등록·중첩 타입 탐색, ProtoJSON 변환, unknown field 경고, loopback API와 웹 UI, HTTPS POST 및 명시적으로 허용한 HTTP
- 10MiB 본문, 64MiB 등록 descriptor 합계, 최대 16개 스키마, 바이너리 재귀 깊이 64, POST 재시도·redirect 추적 없음
- `.proto` 직접 컴파일, 컬렉션·환경변수 저장, HTTP/IPC 작업 취소, CLI `call`, 실제 ApiLens Dart 연동, mj_fav/TypeScript 상호운용과 성능 측정은 후속
- `.proto`는 외부 `protoc --include_imports --descriptor_set_out=...`로 생성한 descriptor를 등록합니다. proto2·Editions는 거절합니다. 모든 well-known type 조합을 검증했다는 의미는 아닙니다.

## 사용 목표

- `.proto` 또는 descriptor set을 불러와 메시지 타입을 탐색합니다.
- ProtoJSON으로 요청을 작성하고 Protobuf 바이너리로 변환합니다.
- 일반 HTTP POST를 호출하고 성공·오류 응답을 디코딩합니다.
- CLI, 독립 UI, ApiLens에서 같은 Kotlin Core를 사용합니다.
- 데이터와 인증정보는 기본적으로 로컬에서 처리합니다. 사용자가 지정한 대상 API로의 요청 전송은 명시적으로 실행합니다.

초기 통합 대상은 Protobuf API를 사용하는 mj_fav와 Flutter/Dart 기반 API 개발 도구 ApiLens입니다. 두 제품의 비즈니스 로직은 이 저장소에 포함하지 않습니다. gRPC·gRPC-Web·트래픽 캡처는 MVP 범위 밖입니다.

## 기술 방향

| 영역 | 구현 방향 |
|---|---|
| 공통 Core | Kotlin/JVM, Protobuf Java DynamicMessage와 JsonFormat |
| CLI와 보조 프로세스 | Kotlin/JVM, 표준입출력 JSON Lines (순차 처리) |
| 독립 UI 로컬 서버 | Spring Boot + Kotlin, loopback 전용 |
| 독립 웹 UI | React + TypeScript + Vite |
| 빌드 | Gradle Kotlin DSL, 프런트엔드 빌드용 Node.js |
| 배포 | 초기 JVM 배포, 운영체제별 런타임 동봉 패키지는 후속 검증 |

## 문서

| 문서 | 내용 |
|---|---|
| [제품 기획서](docs/PRD.md) | 사용자, 범위, 흐름, 수용 기준 |
| [구현 설계서](docs/DESIGN.md) | Core, 스키마, IPC, HTTP, 보안, 패키지 구조 |
| [연동 계획](docs/INTEGRATIONS.md) | mj_fav와 ApiLens 연동 경계 및 검증 |
| [개발 로드맵](docs/ROADMAP.md) | 단계별 산출물과 출시 게이트 |
| [기술 근거](docs/SOURCES.md) | 공식 자료, 확인일, 제약 |
| [테스트 케이스](docs/TEST_CASES.md) | 자동·수동 검증 범위와 기대 결과 |
| [기여 안내](CONTRIBUTING.md) | 설계 검토와 향후 개발 규칙 |
| [보안 안내](SECURITY.md) | 민감정보 취급과 제보 |

## 라이선스

MIT License. 자세한 내용은 [LICENSE](LICENSE)를 참조하세요.
