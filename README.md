# mj_proto_assist

일반 HTTP API의 Protobuf 요청과 응답을 작성하고 확인하는 로컬 개발 도구입니다. JSON 입력을 Protobuf로 인코딩해 HTTP POST로 보내고, 응답을 읽기 쉬운 JSON으로 디코딩하는 것을 목표로 합니다.

**현재는 기획·설계 단계입니다. 실행 가능한 CLI, 서버, UI 및 ApiLens 플러그인은 아직 없습니다.**

## 사용 목표

- `.proto` 또는 descriptor set을 불러와 메시지 타입을 탐색합니다.
- ProtoJSON으로 요청을 작성하고 Protobuf 바이너리로 변환합니다.
- 일반 HTTP POST를 호출하고 성공·오류 응답을 디코딩합니다.
- CLI, 독립 UI, ApiLens에서 같은 Kotlin Core를 사용합니다.
- 데이터와 인증정보는 기본적으로 로컬에서 처리합니다. 사용자가 지정한 대상 API로의 요청 전송은 명시적으로 실행합니다.

초기 통합 대상은 Protobuf API를 사용하는 mj_fav와 Flutter/Dart 기반 API 개발 도구 ApiLens입니다. 두 제품의 비즈니스 로직은 이 저장소에 포함하지 않습니다. gRPC·gRPC-Web·트래픽 캡처는 MVP 범위 밖입니다.

## 기술 방향

| 영역 | 계획 |
|---|---|
| 공통 Core | Kotlin/JVM, Protobuf Java DynamicMessage와 JsonFormat |
| CLI와 ApiLens 보조 프로세스 | Kotlin/JVM, 표준입출력 JSON Lines |
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
| [기여 안내](CONTRIBUTING.md) | 설계 검토와 향후 개발 규칙 |
| [보안 안내](SECURITY.md) | 민감정보 취급과 제보 |

## 라이선스

MIT License. 자세한 내용은 [LICENSE](LICENSE)를 참조하세요.
