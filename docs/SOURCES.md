# 기술 근거와 확인 범위

확인일: 2026-10-29. 공식 자료를 기준으로 구현 제약을 정리한다. 스택과 모듈·한도·IPC는 이 프로젝트의 설계 선택이다.

| 공식 자료 | 설계에 적용한 내용 |
|---|---|
| [DynamicMessage](https://protobuf.dev/reference/java/api-docs/com/google/protobuf/DynamicMessage.html) | descriptor로 타입별 생성 코드 없이 메시지 표현 가능 |
| [ProtoJSON](https://protobuf.dev/programming-guides/json/) | 64비트 수·bytes·enum·presence·well-known types와 JSON 변환 제약 |
| [proto3 가이드](https://protobuf.dev/programming-guides/proto3/) | 메시지 정의·필드 변경·호환성·unknown field 취급 |

ApiLens 구성은 기존 로컬 프로젝트의 pubspec과 한국어 README에서 Flutter/Dart·Dio·Riverpod를 확인했다. 해당 소스나 문서를 이 공개 저장소에 복사하지 않았다. 특정 내부 클래스·기존 플러그인 API·웹 연동 가능성은 검증되지 않았으므로 통합 구현 전에 확인한다.

기존 도구 대비 경쟁 우위, 실사용 성능, OS별 배포, 실제 ApiLens 통합은 아직 검증되지 않았다. 공개 저장소는 기획·설계의 출발점이며 공식 Protobuf 또는 ApiLens 프로젝트의 후원·제휴를 뜻하지 않는다.
