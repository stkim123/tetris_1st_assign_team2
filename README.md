# Tetris Monster

> 2026 소스코드분석 팀 프로젝트 · 팀 "진전조"

대학 생활을 소재로 한 "전투형 테트리스"입니다. 줄을 지우면 상대의 HP가 줄고 상대 보드에 방해 줄이 올라갑니다. 상대 HP를 0으로 만들거나 상대 보드를 끝까지 채우면 이깁니다.

기존 Java Swing 테트리스(`tetris2026`)를 분석한 뒤, 게임 규칙·전투·AI·통신·화면을 분리하는 구조로 다시 설계했습니다.

---

## 팀 구성

| 이름 | 담당 |
|---|---|
| 심은진 | AI·Network, 게임 모드·사용자 데이터 |
| 전예랑 | Core/Battle, 기본 게임·전투 규칙 |
| 조성은 | Frontend, 화면·공통 기능 |

---

## 주요 기능

| 구분 | 내용 |
|---|---|
| 기본 게임 | 이동·회전·낙하·줄 삭제, Hold, Ghost, NEXT 3개, 7-bag 블록 공급 |
| 전투 | HP·피해, 방해 줄(대기·상쇄), 콤보, 퍼펙트 클리어, Fever |
| 캐릭터 | 기본형·공격형·방어형·유틸형 (스토리 전용, 상점에서 구매) |
| 아이템 | 회복, 방어, 피해 증가, 가비지 폭탄, 라인 클리너, Fever 충전, 시간 왜곡, 무효화 (8종) |
| 스토리 PvE | 3과정 9전투, 일반·엘리트·보스 몬스터 AI, 전투별 대사 |
| 로컬 모드 | 튜토리얼(5단계), 무한 모드, 40줄 스프린트 |
| 온라인 PvP | 회원가입·로그인, 방 생성·입장·준비, 서버 판정 대전, 랭킹 |
| 기타 | 코인·상점(캐릭터·배경), 진행 저장, BGM·효과음, 설정 |

### 스토리 구성

| 과정 | 전투 1 | 전투 2 | 전투 3 |
|---|---|---|---|
| 대학교 | 술 | 교양 | 교수 |
| 졸업 | 재수강 F | GPT | 캡스톤 |
| 취업 | 코딩 테스트 | 면접관 | 기업 |

앞 전투를 이겨야 다음 전투가 열리며, 뒤로 갈수록 몬스터 HP·속도·AI 수준이 올라갑니다.

---

## 실행 방법

### 요구 사항

- JDK 8 이상
- 처음 빌드할 때 Maven 의존성을 내려받기 위한 인터넷 연결

### Windows

저장소 루트에서 `run.cmd`를 실행합니다. 빌드된 JAR가 없으면 자동으로 빌드한 뒤 게임을 시작합니다.

PowerShell에서 직접 실행할 수도 있습니다. `JAVA_HOME`이 설정되어 있으면 `-JdkHome`은 생략해도 됩니다.

```powershell
.\build.ps1 -Task Build   # 빌드 → out/tetris.jar
.\build.ps1 -Task Run     # 빌드 후 실행
.\build.ps1 -Task Test    # 전체 테스트 실행
```

### macOS / Linux

```bash
./mvnw -DskipTests package
java -jar target/tetris-client.jar
```

---

## 조작법

| 키 | 동작 |
|---|---|
| ← / → | 좌우 이동 |
| ↑ / ↓ | 회전 |
| D | 한 칸 내리기 |
| Space | 즉시 낙하 |
| C | Hold |
| 1 ~ 4 | 아이템 슬롯 사용 |
| P | 일시정지 (로컬) |
| Esc | 이전 화면으로 |

설정 화면에서 음량, 조작 튜토리얼, 온라인 계정을 관리할 수 있습니다.

---

## 온라인 대전

온라인 PvP는 Firebase Authentication으로 로그인하고, Google Cloud Run의 대전 서버에 WebSocket(WSS)으로 연결합니다. 승패는 서버가 판정하며, 공식 경기 결과만 전적과 랭킹(Elo)에 반영됩니다.

- 기본 접속 설정은 `src/main/resources/online-defaults.properties`에 들어 있습니다. 공개용 값만 포함합니다.
- 서버 운영 상태에 따라 온라인 대전을 이용하지 못할 수 있습니다. 이 경우에도 스토리·로컬 모드는 정상적으로 플레이할 수 있습니다.
- 온라인 대전에서는 캐릭터를 사용할 수 없고 모든 참가자가 기본형으로 고정됩니다.

---

## 프로젝트 구조

```
src/main/java/kr/ac/jbnu/se/tetris/
├── core        테트리스 규칙 엔진 (보드, 블록, 회전·충돌, 줄 삭제, 7-bag)
├── battle      전투 규칙 (HP, 피해 계산, 방해 줄 대기·상쇄, Fever, 아이템 효과)
├── character   캐릭터 능력치
├── item        아이템 정의
├── ai          몬스터 AI (착지 후보 탐색, 보드 평가, 유형별 정책)
├── controller  플레이어·AI 입력 전달
├── story       스테이지·진행·대사
├── app         앱 실행, 모드별 세션, 진행 저장
├── ui          화면 (로비, 전투, 상점, 설정 등)
├── audio       BGM·효과음
├── network     온라인 클라이언트·통신 프로토콜·대전 서버
├── auth        회원가입·로그인
├── ranking     공식 전적·랭킹
└── firebase    Firebase 연동
```

모든 조작은 `GameAction` 명령으로 `GameEngine`에 전달되고, 엔진은 결과를 이벤트와 상태 스냅샷으로 돌려줍니다. 사람 입력·AI·온라인 서버가 같은 엔진과 전투 규칙(`BattleManager`)을 사용하므로, 규칙을 한 곳에서 고치면 모든 모드에 반영됩니다.

### 밸런스 설정 파일

코드를 수정하지 않고 아래 파일로 수치를 조정할 수 있습니다. 변경한 뒤에는 다시 빌드해야 합니다.

| 파일 | 내용 |
|---|---|
| `src/main/resources/battle/balance.properties` | 줄 수·콤보별 피해와 방해 줄, 퍼펙트 클리어 수치 |
| `src/main/resources/character/characters.properties` | 캐릭터 능력치 |
| `src/main/resources/story/stages.properties` | 9전투의 몬스터 HP, 낙하 속도, AI 수준, 공격 배율, 아이템 레벨 |
| `src/main/resources/ai/profiles.properties` | AI 보드 평가 가중치 |
| `src/main/resources/story/dialogues.properties` | 스토리 전투 대사 |

---

## 저장 데이터

진행 상황과 설정은 사용자 홈 디렉터리의 `.tetris-monster` 폴더에 저장됩니다.

| 파일 | 내용 |
|---|---|
| `save.properties` | 코인, 보유·선택 캐릭터, 배경, 클리어한 전투 |
| `audio.properties` | 음량, 음소거 |
| `display.properties`, `tutorial.properties` | 화면·튜토리얼 설정 |

저장 파일이 손상되면 임시 초기 진행으로 시작하며, 기존 파일을 덮어쓰지 않습니다.

---

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| 언어·화면 | Java 8, Swing |
| 빌드 | Maven |
| 통신 | Netty 4.2 (WebSocket), Gson |
| 인증·저장 | Firebase Authentication, Firestore |
| 서버 배포 | Google Cloud Run |
| 협업 | Git, Notion, Google Drive |

---

## 리소스 출처

- 효과음: Kenney의 [UI Audio](https://kenney.nl/assets/ui-audio), [Impact Sounds](https://kenney.nl/assets/impact-sounds), [Digital Audio](https://kenney.nl/assets/digital-audio) (CC0)
- 글꼴: 여기어때 잘난체, 나눔스퀘어라운드 (각 라이선스 파일은 `src/main/resources/ui/fonts`에 포함)
- `audio/university`의 배경음과 `ui/university`의 일부 이미지는 팀의 `University_Simulation` 프로젝트에서 가져왔습니다.
- `ui/campus-rpg`, `ui/puzzle-rpg`, `ui/characters`의 이미지는 이 게임을 위해 제작했습니다.
