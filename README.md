# QAS Server

웹사이트 또는 GitHub 저장소를 대상으로 AI QA를 제공할 서비스의 Spring Boot 백엔드입니다. 현재는 GitHub 로그인·로그아웃, GitHub App 설치 검증, 설치 계정과 접근 가능한 저장소 조회를 제공합니다.

## 실행 환경

- Java 25
- Spring Boot 4.1.1
- Gradle 9.8.1 Wrapper

기존 프로젝트 패키지 `com.qas.server`, 애플리케이션 진입점, Gradle 버전과 GitHub Actions CI를 유지합니다. 프론트엔드 소스는 이 저장소에 포함하지 않습니다.

## 로컬 실행

IntelliJ에서 프로젝트 루트를 열고 Project SDK / Gradle JVM을 JDK 25로 설정합니다. 실행 구성의 Working directory는 프로젝트 루트로 지정합니다.

`config/local.properties.example`을 `config/local.properties`로 복사한 뒤 GitHub App 정보를 채웁니다. 실제 설정 파일은 Git에서 제외합니다.

```properties
app.github.client-id=YOUR_CLIENT_ID
app.github.client-secret=YOUR_CLIENT_SECRET
app.github.app-id=YOUR_NUMERIC_APP_ID
app.github.slug=your-app-slug
app.frontend.base-url=http://localhost:3000
```

환경 변수로 설정할 수도 있습니다.

| 환경 변수 | 용도 | 기본값 |
| --- | --- | --- |
| `GITHUB_CLIENT_ID` | GitHub App의 Client ID | 없음 |
| `GITHUB_CLIENT_SECRET` | GitHub App의 Client secret | 없음 |
| `GITHUB_APP_ID` | 숫자 App ID | 없음 |
| `GITHUB_APP_SLUG` | GitHub App 페이지 URL의 마지막 이름 | 없음 |
| `FRONTEND_BASE_URL` | 로그인·설치 이후 이동할 프론트엔드 origin, CORS 허용 origin | `http://localhost:3000` |
| `PORT` | 서버 포트 | `8080` |
| `COOKIE_SECURE` | HTTPS용 세션 쿠키 설정 | `false` |

설정 파일과 환경 변수 중 한 방식을 선택하세요. 로컬 properties가 기본 application.yaml보다 우선합니다. `FRONTEND_BASE_URL`은 실제 클라이언트 주소에 맞춰 변경하며, 경로·쿼리 없이 scheme/host/port만 지정합니다.

```sh
./gradlew bootRun
```

진입점은 `com.qas.server.QasServerApplication`입니다. GitHub 인증값이 없어도 서버를 실행할 수 있으며, `/api/config`의 `githubConfigured`는 `false`를 반환합니다.

## GitHub App 등록

[GitHub App 등록 페이지](https://github.com/settings/apps/new)에서 다음과 같이 설정합니다.

| 항목 | 로컬 설정 |
| --- | --- |
| Homepage URL | 실제 프론트엔드 주소 |
| Callback / Redirect URI | `http://localhost:8080/login/oauth2/code/github` |
| Setup URL | `http://localhost:8080/api/github/installations/callback` |
| Request user authorization (OAuth) during installation | 체크 해제: 로그인 후 설치하는 별도 흐름 |
| Expire user authorization tokens | 체크 유지 |
| Redirect on update | 체크 해제: 화면에서 설치 정보를 다시 조회 |
| Webhook → Active | 체크 해제: 현재 웹훅 미구현 |
| Repository permissions → Metadata | Read-only |
| Contents 및 나머지 권한 | 현재 목록 조회에는 추가 권한 불필요. 향후 코드 QA에 Contents Read-only 필요 |

Client ID와 App ID는 서로 다른 값입니다. 현재는 사용자 토큰으로 설치와 저장소를 조회하므로 App private key는 필요하지 않습니다. [GitHub 사용자 토큰 문서](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app)

## API와 프론트엔드 연결

| 메서드 | 경로 | 기능 |
| --- | --- | --- |
| GET | `/api/config` | GitHub 설정 여부 |
| GET | `/api/csrf` | CSRF token / headerName |
| GET | `/api/auth/login` | GitHub 로그인 시작 |
| GET | `/login/oauth2/code/github` | Spring Security OAuth 콜백 |
| GET | `/api/me` | 현재 로그인 사용자 공개 프로필 |
| POST | `/api/auth/logout` | 세션 폐기, 성공 시 204 |
| GET | `/api/github/installations` | 현재 앱의 활성 설치 중 사용자에게 접근 가능한 설치 |
| POST | `/api/github/installations/start` | 설치 URL 발급 (`url` 필드) |
| GET | `/api/github/installations/callback` | state 및 설치 접근 권한 검증 |
| GET | `/api/github/installations/{id}/repositories?page=1` | 설치 권한 검증 후 저장소 조회, 30개씩 |

프론트엔드 연결 시 다음을 적용합니다.

1. 로그인은 브라우저를 서버의 `/api/auth/login`으로 이동시켜 시작합니다.
2. API 요청에는 `credentials: 'include'`를 사용합니다. 허용 origin은 `FRONTEND_BASE_URL` 하나입니다.
3. POST 전 `/api/csrf`를 호출하고 응답의 `headerName`에 `token`을 넣습니다. 로그인·로그아웃 이후에는 새 토큰을 받습니다.
4. 설치 API가 반환한 `url`로 브라우저를 이동합니다.
5. 로그인 성공 시 프론트엔드 `/`로, 실패 시 `/?authError=...`로 돌아옵니다.
6. 설치 완료 시 프론트엔드 `/?installation=...`로 돌아옵니다. 상태값은 `success`, `pending`, `invalid-state`, `failed`, `unverified`입니다.

OAuth 토큰을 프론트엔드에 전달하지 않습니다. `/api/me`는 인증이 없으면 401을 반환합니다. 로그인 오류는 인증값·세션·콜백·토큰 교환 등의 고정된 범주로 분류하며 예외 메시지나 비밀값은 로그에 기록하지 않습니다.

로컬에서는 서버와 클라이언트 모두 `localhost`를 사용하세요. `127.0.0.1`과 섞으면 세션 쿠키가 공유되지 않습니다. 배포 시 HTTPS와 `COOKIE_SECURE=true`를 사용합니다. 현재 SameSite=Lax 정책에서는 프론트와 서버가 같은 site에 있어야 합니다. 서로 다른 site에 배포하려면 쿠키 정책과 CSRF 구성을 별도로 검토해야 합니다.

## 코드 구조

```text
com.qas.server
  controller/   AuthController, AppConfigController, GitHubInstallationController
  service/      AuthService, GitHubInstallationService, GitHubInstallationStateService
  client/       GitHubApiClient
  dto/          API 응답 DTO (*Response)
  exception/    GitHubIntegrationException, GlobalExceptionHandler
  config/       SecurityConfig, GitHubClientConfig, 인증 및 프론트엔드 설정
```

`Controller → Service → GitHubApiClient → GitHub API` 순서로 처리합니다. 컨트롤러는 HTTP 요청·응답, 서비스는 설치 검증과 권한 확인, 클라이언트는 외부 API 통신을 담당합니다. DB를 사용하지 않으므로 Entity / Repository는 없습니다. DB 저장소를 추가할 때 `repository/*Repository` 명명을 사용합니다.

## 테스트

```sh
./gradlew build --no-daemon
```

기존 애플리케이션 시작 테스트와 함께 OAuth 리다이렉트·PKCE, 미인증 접근 차단, CSRF, CORS, 공개 프로필, 설치 state 만료·재사용 방지, 설치 접근 권한, 앱 ID와 중지 상태 필터링, 인증 취소 응답, 오류 정보 노출 방지를 검증합니다. 자동 테스트의 GitHub API는 대역을 사용하며 실제 GitHub 승인·설치는 등록한 앱과 프론트엔드를 연결한 뒤 확인해야 합니다.

## 현재 범위

회원·설치 정보의 DB 저장, 웹훅, 앱 private key 기반 백그라운드 작업, LLM QA 실행은 아직 구현하지 않았습니다. 세션과 access/refresh token은 서버 메모리에서 유지되며 재시작·로그아웃·세션 만료 시 다시 로그인해야 합니다.
