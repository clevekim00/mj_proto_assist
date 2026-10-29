# 구현 테스트 케이스

합성 schema와 payload만 사용한다. `./scripts/test.sh`로 실행한다.

| 영역 | 케이스 | 기대 결과 |
|---|---|---|
| Core | int64 > JS 안전 정수, bytes, repeated, map, nested, optional 빈 값, oneof | 알려진 필드 값과 presence 보존 |
| Core | 알려지지 않은 JSON 필드, oneof 동시 선택 | 입력 오류 |
| Core | 잘못된 wire bytes·타입 미등록·10MiB 초과 | 디코딩/입력 거절 |
| Core | 최상위·중첩 unknown binary field | UNKNOWN_FIELDS_PRESENT 경고 |
| Registry | 빈 schema, proto2, 누락 import, 중복 파일·심벌 | 등록 거절, 기존 schema 유지 |
| Registry | 역순 import, schema 간 타입 격리, release | 순서 무관 등록, 다른 schema 접근 거절, 개별 해제 |
| WKT | Timestamp, 등록된 Any, enum | ProtoJSON 왕복, 미등록 Any 거절 |
| HTTP | Protobuf POST, 합성 Authorization, 400 | 요청 바이트·헤더 전달, 응답 바이트 보존, 호출 1회 |
| HTTP | 302 redirect | 추적하지 않음 |
| HTTP | 204 빈 본문, 500 HTML | HTTP 상태·raw body 보존 |
| HTTP | HTTP 미허용, 비HTTP scheme, URL userinfo | 전송 전 거절 |
| HTTP | 타임아웃, 10MiB 초과 응답 | 제한 내 종료·응답 거절 |
| Local API | 외부 Origin, 잘못된 Host, 세션 없음, 과대 본문 | controller 호출 전 403/401 |
| Session | bootstrap 중복 교환 | 최초만 성공, 세션은 메모리 보관 |
| IPC | hello, major 불일치, 중복 id, 잘못된 JSON, shutdown | 정상 handshake 또는 구조화 오류·종료 |
| IPC | descriptor 등록→encode→decode→release | int64 문자열 보존, 해제 응답 |
| UI 단위 | 상태별 타입 규칙, JSON 관리 요청, 비JSON 오류 | 타입 매핑·문자열 보존·오류 표시 |
| 통합 smoke | 실제 서버·세션·스키마·codec·200/400/500/204·Origin·오류 마스킹 | 전체 경로 통과, 대상 호출은 4회 |
| 수동 브라우저 | echo.pb 업로드→Echo 선택→인코딩→명시 HTTP POST | HTTP 200, 원본 Base64·디코딩 JSON·64비트 revision 확인 |

자동 테스트는 JUnit 18개와 UI 단위 테스트 3개다. 실제 브라우저 검증은 수동 검증 기록이며 자동 E2E 회귀 테스트로 계산하지 않는다. 아직 ApiLens·mj_fav·TypeScript 상호운용, 작업 취소, 컬렉션, 부하·성능 목표는 검증하지 않았다.
