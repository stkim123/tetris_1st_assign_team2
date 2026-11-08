package kr.ac.jbnu.se.tetris.app;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.GraphicsEnvironment;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import kr.ac.jbnu.se.tetris.ai.DifficultyProfileCatalog;
import kr.ac.jbnu.se.tetris.character.CharacterCatalog;
import kr.ac.jbnu.se.tetris.character.CharacterSpec;
import kr.ac.jbnu.se.tetris.app.session.LocalMatchSession;
import kr.ac.jbnu.se.tetris.app.session.CommandOutcome;
import kr.ac.jbnu.se.tetris.app.session.MatchSession;
import kr.ac.jbnu.se.tetris.app.session.OnlineMatchSession;
import kr.ac.jbnu.se.tetris.app.session.SessionPhase;
import kr.ac.jbnu.se.tetris.app.session.SessionSnapshot;
import kr.ac.jbnu.se.tetris.app.session.Subscription;
import kr.ac.jbnu.se.tetris.battle.BattleState;
import kr.ac.jbnu.se.tetris.battle.BattleEvent;
import kr.ac.jbnu.se.tetris.core.GameEvent;
import kr.ac.jbnu.se.tetris.core.GameAction;
import kr.ac.jbnu.se.tetris.core.GameState;
import kr.ac.jbnu.se.tetris.core.PlayerIntent;
import kr.ac.jbnu.se.tetris.network.ConnectionOptions;
import kr.ac.jbnu.se.tetris.network.NetworkSubscription;
import kr.ac.jbnu.se.tetris.network.NetworkUpdate;
import kr.ac.jbnu.se.tetris.network.RoomCommand;
import kr.ac.jbnu.se.tetris.network.RoomState;
import kr.ac.jbnu.se.tetris.network.NetworkClient;
import kr.ac.jbnu.se.tetris.network.WebSocketNetworkClient;
import kr.ac.jbnu.se.tetris.network.RankedOnlineConfig;
import kr.ac.jbnu.se.tetris.auth.AuthSession;
import kr.ac.jbnu.se.tetris.auth.AuthService;
import kr.ac.jbnu.se.tetris.auth.UsernameIdentity;
import kr.ac.jbnu.se.tetris.auth.SignUpResult;
import kr.ac.jbnu.se.tetris.ranking.LeaderboardService;
import kr.ac.jbnu.se.tetris.ranking.LeaderboardEntry;
import kr.ac.jbnu.se.tetris.audio.AudioService;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.GameArt;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.LeaderboardPanel;
import kr.ac.jbnu.se.tetris.story.CampaignProgress;
import kr.ac.jbnu.se.tetris.story.EncounterRun;
import kr.ac.jbnu.se.tetris.story.MonsterSpec;
import kr.ac.jbnu.se.tetris.story.MonsterTier;
import kr.ac.jbnu.se.tetris.story.Stage;
import kr.ac.jbnu.se.tetris.story.StageCatalog;
import kr.ac.jbnu.se.tetris.story.StoryProgressService;
import kr.ac.jbnu.se.tetris.story.StoryDialogueCue;
import kr.ac.jbnu.se.tetris.story.StoryDialogueService;
import kr.ac.jbnu.se.tetris.ui.ScreenRouter;
import kr.ac.jbnu.se.tetris.ui.SessionUiBinding;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.UniversityPixelTheme;
import kr.ac.jbnu.se.tetris.ui.seongeun.model.PlayerData;
import kr.ac.jbnu.se.tetris.ui.seongeun.model.RoomData;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.BattlePanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.CharacterShopPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.LocalGamePanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.LocalModePanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.LoginPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.MainLobbyPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.ResultPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.SettingsPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.RoomListPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.SignUpPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.StoryStageSelectPanel;
import kr.ac.jbnu.se.tetris.ui.seongeun.panels.WaitingRoomPanel;

/** 성은 원본 화면과 이동 버튼에 현재 게임 세션을 붙이는 실행 조립부. */
public final class SeongeunApplication implements AutoCloseable {
    private static final String LOGIN = "LOGIN";
    private static final String SIGN_UP = "SIGN_UP";
    private static final String LOBBY = "LOBBY";
    private static final String STORY_STAGE = "STORY_STAGE";
    private static final String ROOM_LIST = "ROOM_LIST";
    private static final String WAITING_ROOM = "WAITING_ROOM";
    private static final String BATTLE = "BATTLE";
    private static final String RESULT = "RESULT";
    private static final String LOCAL_MODE = "LOCAL_MODE";
    private static final String LOCAL_GAME = "LOCAL_GAME";
    private static final String CHARACTER_SHOP = "CHARACTER_SHOP";
    private static final String LEADERBOARD = "LEADERBOARD";
    private static final String SETTINGS = "SETTINGS";

    static {
        // 대화상자·툴팁도 게임 화면과 같은 색으로 보이도록 화면 생성 전에 기본값을 바꾼다.
        UniversityPixelTheme.installDefaults();
    }

    private final CardLayout cards = new CardLayout();
    private final JPanel screens = new JPanel(cards);
    private final JMenuBar menu = new JMenuBar();
    private JMenuItem characterMenuItem;
    private final Random seeds;
    private final StageCatalog stages = StageCatalog.loadDefault();
    private final StoryProgressService progress = new StoryProgressService(stages);
    private final StoryDialogueService dialogues = new StoryDialogueService(stages);
    private final CharacterCatalog characters = CharacterCatalog.loadDefault();
    private final Map<String, Integer> characterPrices = new LinkedHashMap<String, Integer>();
    private final LoginPanel login = new LoginPanel();
    private final SignUpPanel signUp = new SignUpPanel();
    private final MainLobbyPanel lobby = new MainLobbyPanel();
    private final StoryStageSelectPanel storySelect = new StoryStageSelectPanel();
    private final RoomListPanel roomList = new RoomListPanel();
    private final WaitingRoomPanel waitingRoom = new WaitingRoomPanel();
    private final BattlePanel battle = new BattlePanel();
    private final ResultPanel result = new ResultPanel();
    private final LocalModePanel localMode = new LocalModePanel();
    private final LocalGamePanel localPanel = new LocalGamePanel();
    private final CharacterShopPanel characterShop = new CharacterShopPanel();
    private final LeaderboardPanel leaderboard = new LeaderboardPanel();
    private final SettingsPanel settings = new SettingsPanel();
    private final AudioService audio = new AudioService();
    private final kr.ac.jbnu.se.tetris.ui.seongeun.components.ScreenTransition transition =
            new kr.ac.jbnu.se.tetris.ui.seongeun.components.ScreenTransition();
    private final TutorialPreferenceStore tutorialPreferences;
    private final DisplayPreferenceStore displayPreferences;
    private boolean skipTutorial;
    private boolean transitionsOff;
    private String tutorialReturnScreen = LOBBY;
    private String loginReturnScreen = LOBBY;
    private enum PostLoginDestination { NONE, ONLINE_BATTLE, LEADERBOARD }
    private PostLoginDestination postLoginDestination = PostLoginDestination.NONE;
    private String accountStatus = "로컬 플레이";
    private RankedOnlineConfig rankedConfig;
    private AuthService auth;
    private AuthSession accountSession;
    private boolean authBusy;
    private long authEpoch;
    private int completedAuthRequests;
    private final Timer authRefresh;
    private long refreshRequestId = -1;
    private String rankedSaveStatus;
    private String resultPresentedMatchId;
    private Timer resultDelay;
    private boolean rankingBusy;
    private boolean rankingReloadPending;
    private final PlayerSaveStore saveStore;
    private PlayerSaveStore.Data saveData;
    private boolean saveWritable = true;
    private boolean saveDirty;
    private final Timer localGravity;
    private JFrame frame;
    private int lastViewWidth, lastViewHeight;
    private boolean correctingWindowSize;
    private String currentScreen;
    private String lastMessage;
    private LocalGameSession localGame;
    private TutorialSession tutorial;
    private MatchSession match;
    private Subscription matchSubscription;
    private NetworkClient network;
    private NetworkSubscription networkSubscription;
    private RoomState roomState;
    private String requestedRoomName;
    private boolean onlineConnected;
    private boolean onlineResultReturned;
    private boolean storyBattle;
    private String displayedBattleMatchId;
    private boolean closed;
    private final Set<Long> itemRequests = new HashSet<Long>();
    private int earnedReward;
    private List<StoryDialogueCue> resultDialogue = Collections.emptyList();

    public SeongeunApplication() { this(new Random(), PlayerSaveStore.defaultStore()); }

    SeongeunApplication(Random seeds) { this(seeds, null); }

    SeongeunApplication(Random seeds, PlayerSaveStore saveStore) {
        ScreenRouter.requireEdt();
        if (seeds == null) throw new IllegalArgumentException("Seed generator is required");
        this.seeds = seeds;
        this.saveStore = saveStore;
        this.tutorialPreferences = saveStore == null ? null : TutorialPreferenceStore.defaultStore();
        this.displayPreferences = saveStore == null ? null : DisplayPreferenceStore.defaultStore();
        if (tutorialPreferences != null) {
            try { skipTutorial = tutorialPreferences.loadSkip(); }
            catch (IOException ignored) { skipTutorial = false; }
        }
        if (displayPreferences != null) {
            try { transitionsOff = displayPreferences.loadTransitionsOff(); }
            catch (IOException ignored) { transitionsOff = false; }
        }
        try {
            rankedConfig = RankedOnlineConfig.load();
            if (rankedConfig != null) auth = rankedConfig.createAuthService();
        } catch (IOException invalid) { lastMessage = "온라인 설정을 읽지 못했습니다. 로컬 플레이는 가능합니다."; }
        login.setOnlineAvailable(auth != null);
        authRefresh = new Timer(30000, event -> refreshAccount());
        authRefresh.setCoalesce(true);
        authRefresh.start();
        battle.setAudio(audio);
        characterPrices.put("student", 0);
        characterPrices.put("attacker", 100);
        characterPrices.put("defender", 100);
        characterPrices.put("utility", 120);
        try {
            saveData = saveStore == null ? PlayerSaveStore.Data.initial() : saveStore.load();
            progress.restoreCompletedEncounterIds(saveData.getCleared());
        } catch (IOException | IllegalArgumentException invalid) {
            saveData = PlayerSaveStore.Data.initial();
            saveWritable = false;
            lastMessage = "로컬 저장 데이터를 읽지 못해 임시 진행으로 시작합니다: " + invalid.getMessage();
        }
        localGravity = new Timer(400, event -> advanceGravity());
        localGravity.setCoalesce(true);
        addScreens();
        applyBackgroundSkin();
        bindActions();
        setAccountStatus(accountStatus);
        buildMenu();
        UniversityPixelTheme.apply(screens);
        screens.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "navigate-back");
        screens.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "navigate-back");
        screens.getActionMap().put("navigate-back", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { navigateBack(); }
        });
        show(LOBBY);
    }

    private void addScreens() {
        screens.add(login, LOGIN);
        screens.add(signUp, SIGN_UP);
        screens.add(lobby, LOBBY);
        screens.add(storySelect, STORY_STAGE);
        screens.add(roomList, ROOM_LIST);
        screens.add(waitingRoom, WAITING_ROOM);
        JScrollPane battleScroll = new JScrollPane(battle);
        battleScroll.setBorder(null);
        battleScroll.getVerticalScrollBar().setUnitIncrement(16);
        screens.add(battleScroll, BATTLE);
        screens.add(result, RESULT);
        screens.add(localMode, LOCAL_MODE);
        screens.add(localPanel, LOCAL_GAME);
        screens.add(characterShop, CHARACTER_SHOP);
        screens.add(leaderboard, LEADERBOARD);
        screens.add(settings, SETTINGS);
    }

    private void bindActions() {
        // 원본의 로그인 버튼은 계정 확인 기능이 아니라 로컬 로비 진입이었다.
        login.setLoginAction(event -> {
            clearSecrets(login);
            postLoginDestination = PostLoginDestination.NONE;
            loginReturnScreen = LOBBY;
            show(LOBBY);
            if (LOBBY.equals(currentScreen) && !skipTutorial && frame != null && frame.isShowing())
                SwingUtilities.invokeLater(() -> {
                    if (!closed && LOBBY.equals(currentScreen) && !skipTutorial)
                        startTutorial(LOBBY);
                });
        });
        login.setBackAction(event -> cancelLogin());
        login.setOnlineLoginAction(event -> loginOnline());
        login.setSignUpAction(event -> show(SIGN_UP));
        signUp.setBackAction(event -> cancelSignUp());
        signUp.setRegisterAction(event -> registerOnline());
        lobby.setStoryAction(event -> showStories());
        lobby.setOnlineBattleAction(event -> openOnline());
        lobby.setLocalModeAction(event -> show(LOCAL_MODE));
        lobby.setCharacterAction(event -> showCharacters());
        lobby.setTutorialAction(event -> startTutorial(LOBBY));
        lobby.setServerAction(event -> showLeaderboard());
        lobby.setSettingsAction(event -> showSettings());
        settings.setAccountAction(event -> {
            if (accountSession == null) {
                openLogin(SETTINGS, PostLoginDestination.NONE);
            }
            else signOutOnline();
        });
        settings.setBackAction(event -> show(LOBBY));
        settings.setTutorialAction(event -> startTutorial(SETTINGS));
        settings.setBgmMuteAction(audio::setBgmMuted);
        settings.setSfxMuteAction(audio::setMuted);
        settings.setBgmVolumeAction(audio::setBgmVolume);
        settings.setSfxVolumeAction(audio::setVolume);
        settings.setSkipTutorialAction(this::saveSkipTutorial);
        settings.setReduceTransitionsAction(this::saveTransitionsOff);
        leaderboard.setBackAction(event -> show(LOBBY));
        leaderboard.setReloadAction(event -> loadLeaderboard());
        characterShop.setBackAction(event -> show(LOBBY));
        characterShop.setSelectionAction(this::selectCharacter);
        characterShop.setSkinSelectionAction(this::selectBackgroundSkin);
        storySelect.setBackAction(event -> show(LOBBY));
        for (int index = 0; index < stages.getStages().size(); index++) {
            final int stageIndex = index;
            for (MonsterSpec monster : stages.getStages().get(index).getEncounters()) {
                final String selected = monster.getId();
                storySelect.setStageAction(stageIndex, selected, event -> startStory(stageIndex, selected));
            }
        }
        roomList.setBackAction(event -> leaveOnlineLobby());
        roomList.setRooms(Collections.<RoomData>emptyList(), room -> joinRoom(room.getRoomName()));
        roomList.setCreateRoomAction(event -> createRoom());
        roomList.setJoinByIdAction(event -> promptJoin());
        roomList.setRetryAction(event -> retryOnlineConnection());
        waitingRoom.setBackAction(event -> leaveRoom());
        waitingRoom.setReadyAction(event -> toggleReady());
        battle.setResultAction(event -> {
            if (match != null && match.getSnapshot().getPhase() == SessionPhase.FINISHED) renderMatch();
            else message("전투가 끝나면 실제 결과가 자동으로 표시됩니다.");
        });
        result.setReturnAction(event -> returnFromResult());
        result.setLobbyAction(event -> { closeSession(); show(LOBBY); });
        result.setNextAction(event -> startNextStory());
        result.setRetryAction(event -> retryStory());
        localMode.setBackAction(event -> show(LOBBY));
        localMode.setInfiniteAction(event -> startLocal(LocalGameSession.Mode.INFINITE));
        localMode.setSprintAction(event -> startLocal(LocalGameSession.Mode.SPRINT));
        localMode.setTutorialAction(event -> startTutorial(LOCAL_MODE));
        localPanel.setBackAction(event -> backFromGame());
        localPanel.getPlayerBoard().setInputHandlers(this::submit, this::togglePause);
        battle.getPlayerBoard().setInputHandlers(this::submit, this::togglePause);
        battle.getPlayerBoard().setItemHandler(this::useItem);
        battle.setItemAction(this::useItem);
        battle.setBackAction(event -> backFromGame());
    }

    private void buildMenu() {
        // 원본 화면에 없던 기능은 패널 좌표와 기존 버튼을 바꾸지 않고 메뉴로 연다.
        JMenu functions = new JMenu("기능");
        addMenuItem(functions, "튜토리얼", () -> startTutorial());
        addMenuItem(functions, "HOLD", () -> submit(GameAction.Type.HOLD));
        addMenuItem(functions, "현재 게임 안내", this::showGameInformation);
        addMenuItem(functions, "일시정지 / 계속", this::togglePause);
        addMenuItem(functions, "대전 포기", this::forfeit);
        characterMenuItem = new JMenuItem("캐릭터 / 상점");
        characterMenuItem.addActionListener(event -> showCharacters());
        characterMenuItem.setEnabled(false);
        functions.add(characterMenuItem);
        menu.add(functions);
        JMenu online = new JMenu("온라인");
        addMenuItem(online, "방 번호로 입장...", this::promptJoin);
        addMenuItem(online, "방 번호 보기", this::showRoomId);
        addMenuItem(online, "온라인 PvP 랭킹", this::showLeaderboard);
        menu.add(online);
        JMenu sound = new JMenu("소리");
        javax.swing.JCheckBoxMenuItem mute = new javax.swing.JCheckBoxMenuItem("효과음 끄기", audio.isMuted());
        mute.addActionListener(event -> audio.setMuted(mute.isSelected())); sound.add(mute);
        addMenuItem(sound, "효과음 음량...", () -> {
            javax.swing.JSlider volume = new javax.swing.JSlider(0, 100, Math.round(audio.getVolume() * 100));
            volume.setMajorTickSpacing(25); volume.setPaintTicks(true); volume.setPaintLabels(true);
            volume.addChangeListener(event -> audio.setVolume(volume.getValue() / 100f));
            withLocalGamePaused(() -> JOptionPane.showMessageDialog(frame, volume, "효과음 음량", JOptionPane.PLAIN_MESSAGE));
        });
        menu.add(sound);
    }

    private static String tierName(MonsterTier tier) {
        switch (tier) {
            case NORMAL: return "일반";
            case ELITE: return "엘리트";
            default: return "보스";
        }
    }

    private void addMenuItem(JMenu parent, String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(event -> action.run());
        parent.add(item);
    }

    private void show(String id) {
        ScreenRouter.requireEdt();
        if (closed) return;
        if (!id.equals(currentScreen)) audio.play(AudioService.Event.BUTTON);
        if (LOBBY.equals(id)) refreshLobbyProgress();
        if (!transitionsOff && !id.equals(currentScreen) && frame != null && frame.isShowing())
            transition.play(BATTLE.equals(id) ? UniversityPixelTheme.CORAL
                    : LOBBY.equals(id) || STORY_STAGE.equals(id) ? UniversityPixelTheme.GOLD : UniversityPixelTheme.MINT);
        cards.show(screens, id);
        currentScreen = id;
        characterMenuItem.setEnabled(LOBBY.equals(id) || CHARACTER_SHOP.equals(id));
        if (LOCAL_GAME.equals(id)) SwingUtilities.invokeLater(() -> localPanel.getPlayerBoard().requestFocusInWindow());
        if (BATTLE.equals(id)) SwingUtilities.invokeLater(() -> battle.getPlayerBoard().requestFocusInWindow());
    }

    private void refreshLobbyProgress() {
        int cleared = 0, total = 0;
        CampaignProgress campaign = progress.getCampaignProgress();
        MonsterSpec next = null, last = null;
        int nextLevel = 0, chapterIndex = 0, nextChapter = 0;
        for (Stage stage : stages.getStages()) {
            for (MonsterSpec monster : stage.getEncounters()) {
                total++;
                last = monster;
                if (campaign.isEncounterCleared(monster.getId())) cleared++;
                else if (next == null && campaign.isEncounterUnlocked(stage.getId(), monster.getId())) {
                    next = monster; nextLevel = total; nextChapter = chapterIndex;
                }
            }
            chapterIndex++;
        }
        boolean allCleared = next == null;
        if (allCleared) { next = last; nextLevel = total; nextChapter = Math.max(0, chapterIndex - 1); }
        lobby.setStoryProgress(cleared, total);
        if (next != null) {
            String[] chapters = { "대학교 과정", "졸업 과정", "취업 과정" };
            lobby.setNextBattle(nextLevel, next.getName(), tierName(next.getTier()),
                    chapters[Math.min(chapters.length - 1, nextChapter)], next.getTier() == MonsterTier.BOSS, allCleared);
        }
        lobby.setCoins(saveData.getCoins());
    }

    private void showStories() {
        storySelect.updateProgress(progress.getCampaignProgress(), stages);
        show(STORY_STAGE);
    }

    private void showSettings() {
        settings.update(audio.isBgmMuted(), audio.getBgmVolume(), audio.isMuted(),
                audio.getVolume(), skipTutorial, transitionsOff);
        settings.setAccountStatus(accountStatus, accountSession != null, auth != null, authBusy);
        show(SETTINGS);
    }

    /** 로그인을 요구한 기능을 기억해 인증 후 메뉴를 다시 찾지 않게 한다. */
    private void openLogin(String returnScreen, PostLoginDestination destination) {
        loginReturnScreen = returnScreen;
        postLoginDestination = destination;
        login.setOnlineOnly(true);
        login.setBackLabel(SETTINGS.equals(returnScreen) ? "설정으로" : "취소");
        login.clearPassword();
        login.setStatus("");
        show(LOGIN);
        SwingUtilities.invokeLater(login::focusUsername);
    }

    private void cancelLogin() {
        invalidatePendingAuth();
        clearSecrets(login);
        postLoginDestination = PostLoginDestination.NONE;
        String destination = loginReturnScreen;
        loginReturnScreen = LOBBY;
        if (SETTINGS.equals(destination)) showSettings(); else show(LOBBY);
    }

    private void cancelSignUp() {
        invalidatePendingAuth();
        clearSecrets(signUp);
        show(LOGIN);
        SwingUtilities.invokeLater(login::focusUsername);
    }

    private void invalidatePendingAuth() {
        authEpoch++;
        authBusy = false;
        login.setBusy(false);
        signUp.setBusy(false);
    }

    private void discardStaleAuthSession(AuthSession stale) {
        if (stale == null || auth == null) return;
        new javax.swing.SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                auth.discardSessionIf(stale);
                return null;
            }
        }.execute();
    }

    private void resumeAfterLogin() {
        PostLoginDestination destination = postLoginDestination;
        postLoginDestination = PostLoginDestination.NONE;
        String fallback = loginReturnScreen;
        loginReturnScreen = LOBBY;
        if (!LOGIN.equals(currentScreen) && !SIGN_UP.equals(currentScreen)) return;
        if (destination == PostLoginDestination.ONLINE_BATTLE) openOnline();
        else if (destination == PostLoginDestination.LEADERBOARD) showLeaderboard();
        else if (SETTINGS.equals(fallback)) showSettings();
        else show(LOBBY);
    }

    private void setAccountStatus(String status) {
        accountStatus = status;
        lobby.setAccountStatus(status);
        settings.setAccountStatus(status, accountSession != null, auth != null, authBusy);
    }

    private void saveSkipTutorial(boolean skip) {
        skipTutorial = skip;
        if (tutorialPreferences == null) return;
        try { tutorialPreferences.saveSkip(skip); }
        catch (IOException failed) { lastMessage = "튜토리얼 설정을 저장하지 못했습니다."; }
    }

    private void saveTransitionsOff(boolean off) {
        transitionsOff = off;
        if (off) transition.stop();
        if (displayPreferences == null) return;
        try { displayPreferences.saveTransitionsOff(off); }
        catch (IOException failed) { lastMessage = "화면 설정을 저장하지 못했습니다."; }
    }

    private void showCharacters() {
        if (!LOBBY.equals(currentScreen) && !CHARACTER_SHOP.equals(currentScreen)) return;
        characterShop.setData(saveData.getCoins(), saveData.getOwned(),
                saveData.getSelected(), characterPrices);
        characterShop.setSkinData(saveData.getCoins(), saveData.getOwnedBackgroundSkins(),
                saveData.getEquippedBackgroundSkin());
        show(CHARACTER_SHOP);
    }

    private void selectCharacter(String id) {
        try {
            PlayerSaveStore.Data next = saveData.getOwned().contains(id)
                    ? saveData.withSelected(id)
                    : saveData.withCharacter(id, characterPrices.get(id));
            if (commitSave(next)) {
                showCharacters();
                message("선택한 캐릭터: " + selectedCharacter().getName());
            }
        } catch (IllegalArgumentException invalid) { message("캐릭터 구매 조건을 확인하세요."); }
    }

    private void applyBackgroundSkin() {
        BackgroundSkin selected = BackgroundSkin.fromId(saveData.getEquippedBackgroundSkin());
        lobby.setBackgroundSkin(selected);
        battle.setBackgroundSkin(selected);
        storySelect.setBackgroundSkin(selected);
    }

    private void selectBackgroundSkin(BackgroundSkin skin) {
        if (skin == null) return;
        try {
            if (skin == BackgroundSkin.WINTER &&
                    !kr.ac.jbnu.se.tetris.ui.seongeun.components.BackgroundSkinAssets.isWinterPackReady()) {
                message("겨울 배경 이미지 4개가 모두 설치되어 있는지 확인하세요.");
                return;
            }
            PlayerSaveStore.Data next = saveData.getOwnedBackgroundSkins().contains(skin.getId())
                    ? saveData.withEquippedBackgroundSkin(skin.getId())
                    : saveData.withPurchasedBackgroundSkin(skin.getId());
            if (commitSave(next)) {
                applyBackgroundSkin();
                showCharacters();
                message(skin.getDisplayName() + " 장착 완료");
            }
        } catch (IllegalArgumentException invalid) {
            message("배경 스킨 구매 조건을 확인하세요.");
        }
    }

    private CharacterSpec selectedCharacter() {
        for (CharacterSpec character : characters.all())
            if (character.getId().equals(saveData.getSelected())) return character;
        return characters.basic();
    }

    boolean commitSave(PlayerSaveStore.Data next) {
        saveData = next;
        if (saveStore != null && !saveWritable) {
            saveDirty = true;
            return true;
        }
        try {
            if (saveStore != null) saveStore.save(next);
            saveDirty = false;
            return true;
        } catch (IOException failed) {
            saveDirty = true;
            message("진행은 현재 실행 중에 보관 중입니다. 저장 실패: " + failed.getMessage()
                    + "\n다음 변경 때 모든 진행을 다시 저장합니다.");
            return true;
        }
    }

    private static int storyReward(MonsterTier tier) {
        switch (tier) {
            case NORMAL: return 30;
            case ELITE: return 50;
            default: return 80;
        }
    }

    private void startStory(int stageIndex, String encounterId) {
        ScreenRouter.requireEdt();
        if (closed || stageIndex >= stages.getStages().size()) return;
        String stageId = stages.getStages().get(stageIndex).getId();
        CampaignProgress campaign = progress.getCampaignProgress();
        if (!campaign.isEncounterUnlocked(stageId, encounterId)) {
            message("아직 잠긴 전투입니다.");
            return;
        }
        closeSession();
        earnedReward = 0;
        resultDialogue = Collections.emptyList();
        EncounterRun run = progress.startEncounter(stageId, encounterId, newRunId(), "local", "monster");
        MonsterSpec spec = run.getMonster();
        kr.ac.jbnu.se.tetris.ai.DifficultyProfile difficulty = DifficultyProfileCatalog.forEncounter(spec);
        MonsterSession engine = new MonsterSession(seeds.nextLong(), spec.getName(),
                DifficultyProfileCatalog.forEncounter(spec), selectedCharacter(),
                run.getRunId(), run.getLocalParticipantId(), run.getMonsterParticipantId());
        storyBattle = true;
        battle.setEncounter(stageId, difficulty.getLevel(), spec.getTier(), GameArt.keyForLevel(difficulty.getLevel()));
        installMatch(new LocalMatchSession(engine));
        result.setReturnButtonText("Story로");
        show(BATTLE);
        ((LocalMatchSession) match).startClock();
        renderMatch();
        StoryDialogueCue encounter = dialogues.onEncounterStart(run);
        if (encounter != null) battle.showStoryDialogue(encounter.getSpeaker(), encounter.getText());
    }

    private void startLocal(LocalGameSession.Mode mode) {
        ScreenRouter.requireEdt();
        closeSession();
        localGame = new LocalGameSession(mode, seeds.nextLong());
        localPanel.startMode(mode == LocalGameSession.Mode.INFINITE ? "Infinite" : "Sprint");
        show(LOCAL_GAME);
        renderLocal();
    }

    private void startTutorial() { startTutorial(SETTINGS); }

    private void startTutorial(String returnScreen) {
        ScreenRouter.requireEdt();
        closeSession();
        tutorialReturnScreen = returnScreen;
        tutorial = new TutorialSession(seeds.nextLong());
        localPanel.startMode("Tutorial");
        show(LOCAL_GAME);
        renderLocal();
    }

    void submit(GameAction.Type action) {
        ScreenRouter.requireEdt();
        if (closed || (!LOCAL_GAME.equals(currentScreen) && !BATTLE.equals(currentScreen))) return;
        if (localGame != null && !localGame.isFinished()) {
            GameState before = localGame.getPlayerState(); localGame.submit(action);
            localInputEffects(action, before, localGame.getPlayerState()); renderLocal();
        } else if (tutorial != null) {
            GameState before = tutorial.getPlayerState(); tutorial.submit(action);
            localInputEffects(action, before, tutorial.getPlayerState()); renderLocal();
        }
        else if (match != null && match.getSnapshot().getCapabilities().canSubmit()) {
            match.submit(new PlayerIntent(action));
        }
    }

    private void localInputEffects(GameAction.Type action, GameState before, GameState after) {
        if (before == null || after.getVersion() <= before.getVersion()) return;
        if (action == GameAction.Type.HARD_DROP && before.getActivePiece() != null) {
            localPanel.getPlayerBoard().showPlacement(before.getActivePiece(), before.getPieceX(), before.getGhostY());
            audio.play(AudioService.Event.DROP);
        } else if (action == GameAction.Type.ROTATE_LEFT || action == GameAction.Type.ROTATE_RIGHT)
            audio.play(AudioService.Event.ROTATE);
        else if (action == GameAction.Type.MOVE_LEFT || action == GameAction.Type.MOVE_RIGHT) audio.play(AudioService.Event.MOVE);
    }

    private void advanceGravity() {
        ScreenRouter.requireEdt();
        if (closed || !LOCAL_GAME.equals(currentScreen)) return;
        if (localGame != null && !localGame.isPaused()) { localGame.tick(); renderLocal(); }
        else if (tutorial != null && !tutorial.isPaused()) { tutorial.tick(); renderLocal(); }
    }

    private void renderLocal() {
        GameState state = localGame != null ? localGame.getPlayerState()
                : tutorial != null ? tutorial.getPlayerState() : null;
        if (state == null) return;
        GameState previous = localPanel.getPlayerBoard().getGameState();
        localPanel.setState(state);
        if (tutorial != null)
            localPanel.setTutorialInstruction(tutorial.getStep(), tutorial.getInstruction());
        if (previous != null && state.getLinesCleared() > previous.getLinesCleared()) audio.play(AudioService.Event.LINE_CLEAR);
        boolean finished = localGame != null ? localGame.isFinished() : tutorial.isFinished();
        if (finished) {
            localGravity.stop();
            if (localGame != null) {
                if (localGame.isCompleted()) localPanel.setCompleted(true);
                return;
            }
            if (tutorial.isCompleted()) saveSkipTutorial(true);
            result.setResultDetails(tutorial.isCompleted() ? "COMPLETE" : "GAME OVER", "Player",
                    Integer.toString(state.getLinesCleared()), "—", "—", "—");
            result.setReturnButtonText(tutorialReturnScreen.equals(SETTINGS) ? "설정으로" : "로비로");
            result.setRankedStatus(""); result.setStoryActions(false, false, "");
            result.setStoryDialogue(Collections.emptyList());
            show(RESULT);
        } else if (state.getStatus() == GameState.Status.PAUSED) localGravity.stop();
        else localGravity.start();
    }

    private void installMatch(MatchSession session) {
        match = session;
        matchSubscription = SessionUiBinding.bind(session, update -> {
            if (closed || session != match) return;
            if (storyBattle && session instanceof LocalMatchSession) {
                LocalMatchSession local = (LocalMatchSession) session;
                CampaignProgress before = progress.getCampaignProgress();
                StoryProgressService.ResultDisposition disposition =
                        progress.recordBattleResult(update.getSnapshot().getMatchId(), local.getLastBattleResult());
                List<StoryDialogueCue> cues = dialogues.onResult(progress.getActiveRun(),
                        disposition, before, progress.getCampaignProgress());
                if (!cues.isEmpty()) resultDialogue = cues;
                if (disposition == StoryProgressService.ResultDisposition.APPLIED_WIN) {
                    EncounterRun won = progress.getActiveRun();
                    boolean firstClear = !saveData.getCleared().contains(won.getEncounterId());
                    int baseReward = storyReward(won.getMonster().getTier());
                    int reward = firstClear ? baseReward : baseReward / 5;
                    PlayerSaveStore.Data updated = firstClear
                            ? saveData.withFirstClear(won.getEncounterId(), reward)
                            : saveData.withReplayReward(won.getEncounterId(), reward);
                    if (commitSave(updated)) earnedReward = reward;
                }
            }
            CommandOutcome outcome = update.getOutcome();
            if (outcome != null && itemRequests.remove(outcome.getRequestId()) && !outcome.isAccepted())
                battle.setFeedback("아이템 사용 불가 · " + outcome.getReasonCode());
            renderMatch();
            battle.applyEvents(update.getEvents(), update.getSnapshot().getLocalParticipantId());
            if (storyBattle) {
                showStoryDialogues(update.getSnapshot(), update.getEvents());
                showStoryHpDialogue(update.getSnapshot());
            }
        });
    }

    /** Queued story dialogue (strong attack and timed exchange) remains event-driven. */
    private void showStoryDialogues(SessionSnapshot snapshot, List<BattleEvent> events) {
        if (!storyBattle || snapshot == null || snapshot.getBattleState() == null) return;
        EncounterRun run = progress.getActiveRun();
        if (run == null || run.isFinished()) return;
        if (events != null) {
            for (BattleEvent event : events) {
                if (event == null || event.getType() != BattleEvent.Type.CORE_EVENT
                        || !snapshot.getLocalParticipantId().equals(event.getActorId())) continue;
                GameEvent core = event.getCoreEvent();
                if (core == null || core.getType() != GameEvent.Type.LINE_CLEAR) continue;
                StoryDialogueCue cue = dialogues.onPlayerLineClear(run, core.getLineCount());
                if (cue != null) battle.showStoryDialogue(cue.getSpeaker(), cue.getText());
            }
        }
        for (StoryDialogueCue cue : dialogues.onElapsed(run, snapshot.getBattleState().getElapsedMillis()))
            battle.showStoryDialogue(cue.getSpeaker(), cue.getText());
    }

    private void showStoryHpDialogue(SessionSnapshot snapshot) {
        EncounterRun run = progress.getActiveRun();
        BattleState state = snapshot.getBattleState();
        if (run == null || state == null || state.getParticipant(run.getMonsterParticipantId()) == null) return;
        kr.ac.jbnu.se.tetris.battle.ParticipantState monster =
                state.getParticipant(run.getMonsterParticipantId());
        StoryDialogueCue cue = dialogues.onMonsterHp(run, monster.getHp(), monster.getMaxHp());
        if (cue != null) battle.showStoryDialogue(cue.getSpeaker(), cue.getText());
        StoryDialogueCue crisis = dialogues.onMonsterCrisis(run, monster.getHp(), monster.getMaxHp());
        if (crisis != null) battle.showStoryDialogue(crisis.getSpeaker(), crisis.getText());
    }

    private void renderMatch() {
        if (match == null) return;
        SessionSnapshot snapshot = match.getSnapshot();
        if (snapshot.getPhase() == SessionPhase.FAILED) {
            if (match instanceof OnlineMatchSession && displayedBattleMatchId == null) {
                show(ROOM_LIST);
                return;
            }
            result.setResultDetails("CONNECTION LOST", "Player", "—", "—", "—", "—");
            result.setReturnButtonText("대기방으로");
            result.setStoryDialogue(Collections.emptyList());
            show(RESULT);
            return;
        }
        BattleState state = snapshot.getBattleState();
        if (state == null) return;
        battle.setMode(match instanceof OnlineMatchSession);
        if (!snapshot.getMatchId().equals(displayedBattleMatchId)) {
            String localId = snapshot.getLocalParticipantId();
            String opponentId = null;
            for (String id : state.getParticipants().keySet()) {
                if (!id.equals(localId)) { opponentId = id; break; }
            }
            if (opponentId != null) {
                battle.setPlayers(new PlayerData(state.getParticipant(localId).getName(), 0, "—", false),
                        new PlayerData(state.getParticipant(opponentId).getName(), 0, "—", false));
            }
            displayedBattleMatchId = snapshot.getMatchId();
        }
        battle.setState(state, snapshot.getLocalParticipantId());
        if (snapshot.getPhase() == SessionPhase.FINISHED) {
            if (match instanceof OnlineMatchSession && onlineResultReturned) {
                show(WAITING_ROOM);
                return;
            }
            boolean won = snapshot.getLocalParticipantId().equals(state.getWinnerId());
            int lines = state.getParticipant(snapshot.getLocalParticipantId()).getGameState().getLinesCleared();
            String outcome = state.getWinnerId() == null ? "DRAW" : won ? "VICTORY" : "DEFEAT";
            kr.ac.jbnu.se.tetris.battle.ParticipantState local =
                    state.getParticipant(snapshot.getLocalParticipantId());
            String reward = storyBattle && won ? Integer.toString(earnedReward) : "0";
            result.setResultDetails(outcome, local.getName(), Integer.toString(lines),
                    Integer.toString(local.getMaxCombo()), Integer.toString(local.getTotalDamage()), reward);
            result.setReturnButtonText(storyBattle ? "Story로" : "대기방으로");
            result.setRankedStatus(match instanceof OnlineMatchSession && network instanceof WebSocketNetworkClient
                    ? rankStatusText(rankedSaveStatus) : "");
            EncounterRun active = storyBattle ? progress.getActiveRun() : null;
            int storyLevel = active == null ? 0 : DifficultyProfileCatalog.forEncounter(active.getMonster()).getLevel();
            String badge = !won ? "" : storyLevel == 3 ? "교양 이수증 획득!"
                    : storyLevel == 6 ? "캡스톤 통과 · 졸업장 획득!"
                    : storyLevel == 9 ? "취업 성공 · 사원증 획득!" : "";
            result.setStoryActions(storyBattle, won && storyLevel < 9, badge);
            result.setStoryDialogue(storyBattle ? resultDialogue : Collections.emptyList());
            if (!snapshot.getMatchId().equals(resultPresentedMatchId)) {
                resultPresentedMatchId = snapshot.getMatchId();
                audio.play(won ? AudioService.Event.VICTORY : AudioService.Event.DEFEAT);
                battle.finish(won);
                if (!"FORFEIT".equals(state.getReason())) {
                    final String finishedMatch = snapshot.getMatchId();
                    resultDelay = new Timer(850, event -> {
                        if (match != null && finishedMatch.equals(match.getSnapshot().getMatchId())
                                && BATTLE.equals(currentScreen)) show(RESULT);
                    });
                    resultDelay.setRepeats(false); resultDelay.start();
                } else show(RESULT);
            }
        } else if (match instanceof OnlineMatchSession) show(BATTLE);
    }

    private void togglePause() {
        ScreenRouter.requireEdt();
        if (localGame != null && LOCAL_GAME.equals(currentScreen)) {
            if (localGame.isPaused()) localGame.resume(); else localGame.pause();
            renderLocal();
        } else if (tutorial != null && LOCAL_GAME.equals(currentScreen)) {
            if (tutorial.isPaused()) tutorial.resume(); else tutorial.pause();
            renderLocal();
        } else if (match != null && BATTLE.equals(currentScreen)
                && match.getSnapshot().getCapabilities().canPause()) {
            match.requestPause(match.getSnapshot().getPhase() != SessionPhase.PAUSED);
        } else if (match != null && BATTLE.equals(currentScreen)) {
            battle.setFeedback("온라인 대전은 일시정지를 지원하지 않습니다.");
        }
    }

    private void useItem(int slot) {
        ScreenRouter.requireEdt();
        if (match == null || !BATTLE.equals(currentScreen)
                || !match.getSnapshot().getCapabilities().canSubmit()) return;
        SessionSnapshot snapshot = match.getSnapshot();
        BattleState state = snapshot.getBattleState();
        if (state == null) return;
        kr.ac.jbnu.se.tetris.battle.ParticipantState participant =
                state.getParticipant(snapshot.getLocalParticipantId());
        if (participant == null || slot < 0 || slot >= participant.getItems().size()) return;
        String item = participant.getItems().get(slot);
        if (item == null) return;
        if (participant.getItems().indexOf(item) != slot) {
            battle.setFeedback("같은 아이템은 앞 슬롯부터 사용합니다.");
            return;
        }
        if ("damage_boost".equals(item) || "shield".equals(item)) {
            battle.setFeedback("이 아이템은 조건이 맞으면 자동으로 사용됩니다.");
            return;
        }
        String target = participant.getId();
        if ("garbage_bomb".equals(item) || "nullify".equals(item)) {
            for (String candidate : state.getParticipants().keySet())
                if (!candidate.equals(target)) { target = candidate; break; }
        }
        long requestId = match.submit(new PlayerIntent(GameAction.Type.USE_ITEM,
                new GameAction.ItemUse(item, target)));
        itemRequests.add(requestId);
    }

    private void forfeit() {
        ScreenRouter.requireEdt();
        if (match != null && BATTLE.equals(currentScreen) && match.getSnapshot().getCapabilities().canLeave()) {
            match.leave();
        }
    }

    private void navigateBack() {
        if (LOGIN.equals(currentScreen)) cancelLogin();
        else if (SIGN_UP.equals(currentScreen)) cancelSignUp();
        else if (BATTLE.equals(currentScreen) || LOCAL_GAME.equals(currentScreen)) backFromGame();
        else if (RESULT.equals(currentScreen)) returnFromResult();
        else if (WAITING_ROOM.equals(currentScreen)) leaveRoom();
        else if (ROOM_LIST.equals(currentScreen)) leaveOnlineLobby();
        else if (SETTINGS.equals(currentScreen) || LEADERBOARD.equals(currentScreen)
                || STORY_STAGE.equals(currentScreen) || LOCAL_MODE.equals(currentScreen)
                || CHARACTER_SHOP.equals(currentScreen)) show(LOBBY);
    }

    private void leaveOnlineLobby() {
        closeSession();
        show(LOBBY);
    }

    private void backFromGame() {
        ScreenRouter.requireEdt();
        if (closed) return;
        if (LOCAL_GAME.equals(currentScreen)) {
            String destination = tutorial != null ? tutorialReturnScreen : LOCAL_MODE;
            closeSession();
            if (SETTINGS.equals(destination)) showSettings(); else show(destination);
        } else if (BATTLE.equals(currentScreen)) {
            boolean returnToStory = storyBattle;
            if (match != null && match.getSnapshot().getCapabilities().canLeave()) match.leave();
            closeSession();
            if (returnToStory) showStories(); else show(LOBBY);
        }
    }

    private void showGameInformation() {
        if (tutorial != null) message(tutorial.getInstruction());
        else if (localGame != null) message(localGame.getInstruction());
        else if (match != null && match.getSnapshot().getBattleState() != null) {
            BattleState state = match.getSnapshot().getBattleState();
            String localId = match.getSnapshot().getLocalParticipantId();
            GameState local = state.getParticipant(localId).getGameState();
            message("지운 줄: " + local.getLinesCleared() + " · HOLD: " + local.getHoldPiece()
                    + " · 다음: " + local.getNextPieces());
        } else message("진행 중인 게임이 없습니다.");
    }

    private void returnFromResult() {
        if (storyBattle) {
            closeSession();
            showStories();
        } else if (tutorial != null) {
            String destination = tutorialReturnScreen;
            closeSession();
            if (SETTINGS.equals(destination)) showSettings(); else show(destination);
        } else if (match instanceof OnlineMatchSession) {
            if (match.getSnapshot().getPhase() == SessionPhase.FAILED) {
                closeSession();
                show(ROOM_LIST);
                return;
            }
            onlineResultReturned = true;
            if (roomState != null) show(WAITING_ROOM);
            else show(ROOM_LIST);
            sendRoomCommand(RoomCommand.getRoomState());
        } else {
            closeSession();
            show(LOCAL_MODE);
        }
    }

    private void retryStory() {
        if (!storyBattle || progress.getActiveRun() == null) return;
        String encounter = progress.getActiveRun().getEncounterId();
        for (int i = 0; i < stages.getStages().size(); i++)
            for (MonsterSpec monster : stages.getStages().get(i).getEncounters())
                if (monster.getId().equals(encounter)) { startStory(i, encounter); return; }
    }

    private void startNextStory() {
        if (!storyBattle || progress.getActiveRun() == null) return;
        String encounter = progress.getActiveRun().getEncounterId();
        boolean found = false;
        for (int i = 0; i < stages.getStages().size(); i++)
            for (MonsterSpec monster : stages.getStages().get(i).getEncounters()) {
                if (found) { startStory(i, monster.getId()); return; }
                if (monster.getId().equals(encounter)) found = true;
            }
        closeSession(); showStories();
    }

    private void openOnline() {
        if (auth == null || rankedConfig == null) {
            message("온라인 설정이 없습니다. 공개 설정을 포함한 게임 패키지로 실행하세요."); return;
        }
        if (accountSession == null) {
            openLogin(LOBBY, PostLoginDestination.ONLINE_BATTLE);
            return;
        }
        openOnline(new ConnectionOptions(rankedConfig.getServerUri(), accountSession.getAccessToken()));
    }

    private void loginOnline() {
        if (auth == null || authBusy) return;
        final String username = login.getUsername();
        final char[] password = login.getPassword();
        login.clearPassword();
        final boolean legacyEmail = username.contains("@");
        if (password.length == 0 || (legacyEmail && (username.length() > 254 || username.indexOf('@') < 1))) {
            java.util.Arrays.fill(password, '\0'); login.setStatus("아이디와 비밀번호를 입력하세요."); return;
        }
        if (!legacyEmail) {
            try { UsernameIdentity.normalize(username); }
            catch (IllegalArgumentException invalid) {
                java.util.Arrays.fill(password, '\0');
                login.setStatus("아이디는 영문 시작, 영문·숫자·_ 3~20자로 입력하세요."); return;
            }
        }
        final String accountLabel = legacyEmail ? username : UsernameIdentity.normalize(username);
        final long epoch = ++authEpoch;
        if (network instanceof WebSocketNetworkClient) closeSession();
        accountSession = null; clearLeaderboardForIdentityChange(); setAccountStatus("로컬 플레이");
        authBusy = true; login.setBusy(true); login.setStatus("로그인 중...");
        new javax.swing.SwingWorker<AuthSession, Void>() {
            @Override protected AuthSession doInBackground() throws Exception {
                try { return legacyEmail ? auth.signIn(username, new String(password))
                        : auth.signInUsername(username, new String(password)); }
                finally { java.util.Arrays.fill(password, '\0'); }
            }
            @Override protected void done() {
                completedAuthRequests++;
                if (closed || epoch != authEpoch || !LOGIN.equals(currentScreen)) {
                    try { discardStaleAuthSession(get()); }
                    catch (Exception ignored) { /* No session to discard. */ }
                    return;
                }
                authBusy = false; login.setBusy(false);
                try {
                    accountSession = get();
                    login.setStatus("온라인 로그인 완료");
                    setAccountStatus("온라인 PvP 로그인 완료 · " + accountLabel);
                    resumeAfterLogin();
                } catch (Exception failed) { accountSession = null; login.setStatus("로그인 실패 · 계정 또는 서버 연결을 확인하세요."); }
            }
        }.execute();
    }

    private void registerOnline() {
        if (auth == null || authBusy) return;
        final String username = signUp.getUsername();
        final char[] password = signUp.getPassword();
        char[] confirmation = signUp.getPasswordConfirmation();
        boolean matches = java.util.Arrays.equals(password, confirmation);
        java.util.Arrays.fill(confirmation, '\0'); signUp.clearPasswords();
        try { UsernameIdentity.normalize(username); }
        catch (IllegalArgumentException invalid) {
            java.util.Arrays.fill(password, '\0');
            message("아이디는 영문 시작, 영문·숫자·_ 3~20자로 입력하세요."); return;
        }
        if (password.length < 8 || !matches) {
            java.util.Arrays.fill(password, '\0'); message("8자 이상 비밀번호와 확인란을 확인하세요."); return;
        }
        final long epoch = ++authEpoch;
        if (network instanceof WebSocketNetworkClient) closeSession();
        accountSession = null; clearLeaderboardForIdentityChange(); setAccountStatus("로컬 플레이");
        authBusy = true; signUp.setBusy(true);
        new javax.swing.SwingWorker<SignUpResult, Void>() {
            @Override protected SignUpResult doInBackground() throws Exception {
                try { return auth.signUpUsername(username, new String(password)); }
                finally { java.util.Arrays.fill(password, '\0'); }
            }
            @Override protected void done() {
                completedAuthRequests++;
                if (closed || epoch != authEpoch || !SIGN_UP.equals(currentScreen)) {
                    try { discardStaleAuthSession(get().getSession()); }
                    catch (Exception ignored) { /* No session to discard. */ }
                    return;
                }
                authBusy = false; signUp.setBusy(false);
                try {
                    SignUpResult registered = get();
                    accountSession = registered.getSession();
                    if (accountSession == null) {
                        login.setStatus("가입은 접수됐지만 서버의 계정 확인 설정 때문에 로그인할 수 없습니다. 운영자에게 알려주세요.");
                        if (SIGN_UP.equals(currentScreen)) show(LOGIN);
                    } else {
                        setAccountStatus("온라인 PvP 로그인 완료 · " + UsernameIdentity.normalize(username));
                        resumeAfterLogin();
                    }
                } catch (Exception failed) { message("가입 실패 · 계정 정보 또는 서버 연결을 확인하세요."); }
            }
        }.execute();
    }

    private void refreshAccount() {
        if (closed || authBusy || accountSession == null || auth == null) return;
        if (accountSession.getExpiresAtEpochSecond() - System.currentTimeMillis() / 1000 > 120) return;
        final long epoch = authEpoch;
        authBusy = true;
        settings.setAccountStatus(accountStatus, accountSession != null, auth != null, authBusy);
        new javax.swing.SwingWorker<AuthSession, Void>() {
            @Override protected AuthSession doInBackground() throws Exception { return auth.refresh(); }
            @Override protected void done() {
                if (closed || epoch != authEpoch) return;
                authBusy = false;
                settings.setAccountStatus(accountStatus, accountSession != null, auth != null, authBusy);
                try {
                    accountSession = get();
                } catch (Exception failed) {
                    if (accountSession.getExpiresAtEpochSecond() <= System.currentTimeMillis() / 1000) {
                        accountSession = null;
                        clearLeaderboardForIdentityChange();
                    } else rankingReloadPending = false;
                    setAccountStatus("온라인 인증 갱신에 실패했습니다. 다시 로그인하세요.");
                    if (LEADERBOARD.equals(currentScreen))
                        leaderboard.setError("인증 갱신에 실패했습니다. 다시 로그인하거나 새로고침하세요.");
                    if (network instanceof WebSocketNetworkClient && BATTLE.equals(currentScreen))
                        battle.setFeedback("온라인 인증 갱신 실패 · 서버 연결을 확인하세요.");
                    return;
                }
                if (network instanceof WebSocketNetworkClient && onlineConnected) {
                    try { refreshRequestId = network.refreshAuthentication(accountSession.getAccessToken()); }
                    catch (IllegalStateException disconnected) {
                        onlineConnected = false; roomList.setConnected(false);
                        battle.setFeedback("로그인 유지 · 대전 서버에 다시 연결하세요.");
                    }
                }
                if (rankingReloadPending && LEADERBOARD.equals(currentScreen)) {
                    rankingReloadPending = false;
                    loadLeaderboard();
                }
            }
        }.execute();
    }

    private void signOutOnline() {
        if (auth == null || authBusy) return;
        if (BATTLE.equals(currentScreen) || LOCAL_GAME.equals(currentScreen)) {
            message("현재 게임을 마친 뒤 로그아웃하세요."); return;
        }
        if (network instanceof WebSocketNetworkClient) closeSession();
        final long epoch = ++authEpoch;
        accountSession = null; clearLeaderboardForIdentityChange(); authBusy = true;
        String returnScreen = SETTINGS.equals(currentScreen) ? SETTINGS : LOBBY;
        setAccountStatus("로컬 플레이");
        if (SETTINGS.equals(returnScreen)) showSettings(); else show(LOBBY);
        new javax.swing.SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception { auth.signOut(); return null; }
            @Override protected void done() {
                if (closed || epoch != authEpoch) return;
                authBusy = false; login.setBusy(false);
                setAccountStatus(accountStatus);
                try { get(); } catch (Exception ignored) { login.setStatus("로그아웃 완료 · 서버 연결을 확인하세요."); }
            }
        }.execute();
    }

    private void showLeaderboard() {
        if (BATTLE.equals(currentScreen) || LOCAL_GAME.equals(currentScreen)) {
            message("게임을 마친 뒤 랭킹을 확인하세요."); return;
        }
        if (auth == null || rankedConfig == null) {
            message("온라인 설정이 없습니다. 공개 설정을 포함한 게임 패키지로 실행하세요."); return;
        }
        if (accountSession == null) {
            openLogin(LOBBY, PostLoginDestination.LEADERBOARD);
            return;
        }
        show(LEADERBOARD); loadLeaderboard();
    }

    private void clearLeaderboardForIdentityChange() {
        rankingReloadPending = false;
        leaderboard.setEntries(Collections.<LeaderboardEntry>emptyList());
    }

    private void loadLeaderboard() {
        if (accountSession == null || rankedConfig == null) return;
        if (rankingBusy) { rankingReloadPending = true; return; }
        if (authBusy) { rankingReloadPending = true; return; }
        // 곧 만료될 토큰으로 랭킹을 요청하면 401 이후 재시도만 남으므로 먼저 갱신한다.
        if (accountSession.getExpiresAtEpochSecond() - System.currentTimeMillis() / 1000 <= 120) {
            rankingReloadPending = true;
            refreshAccount();
            return;
        }
        // 화면을 떠난 동안 남은 대기 플래그는 새 조회를 중복시키지 않는다.
        rankingReloadPending = false;
        final AuthSession requestedAccount = accountSession;
        rankingBusy = true; leaderboard.setBusy(true);
        new javax.swing.SwingWorker<java.util.List<LeaderboardEntry>, Void>() {
            @Override protected java.util.List<LeaderboardEntry> doInBackground() throws Exception {
                return rankedConfig.createLeaderboardService().top100(requestedAccount.getAccessToken());
            }
            @Override protected void done() {
                if (closed) return;
                rankingBusy = false; leaderboard.setBusy(false);
                if (rankingReloadPending || (accountSession != null
                        && requestedAccount.getUserId().equals(accountSession.getUserId())
                        && requestedAccount != accountSession)) {
                    rankingReloadPending = false; loadLeaderboard(); return;
                }
                if (requestedAccount != accountSession) return;
                try { leaderboard.setEntries(get()); }
                catch (Exception failed) { leaderboard.setError("조회 실패 · 로그인 상태 또는 서버 연결을 확인하고 새로고침하세요."); }
            }
        }.execute();
    }

    private static String rankStatusText(String status) {
        if ("SAVED".equals(status)) return "공식 PvP 전적이 저장되었습니다. 랭킹에서 확인하세요.";
        if ("SAVE_FAILED".equals(status)) return "전적 저장 실패 · 서버의 재처리 결과를 기다려 주세요.";
        if ("VOIDED".equals(status)) return "서버에서 무효 처리되어 랭킹에 반영되지 않습니다.";
        return "공식 PvP 전적을 서버에서 저장 중입니다.";
    }

    void openOnline(ConnectionOptions options) {
        closeSession();
        roomList.setRooms(Collections.<RoomData>emptyList(), room -> joinRoom(room.getRoomName()));
        roomList.setConnected(false);
        show(ROOM_LIST);
        connectOnline(options);
    }

    private void retryOnlineConnection() {
        if (accountSession == null) {
            openLogin(LOBBY, PostLoginDestination.ONLINE_BATTLE);
            return;
        }
        if (rankedConfig != null)
            openOnline(new ConnectionOptions(rankedConfig.getServerUri(), accountSession.getAccessToken()));
        else roomList.setConnectionFailed("온라인 설정이 없습니다");
    }

    void connectOnline(ConnectionOptions options) {
        ScreenRouter.requireEdt();
        if (closed) return;
        if (match != null || network != null) closeSession();
        final NetworkClient client = new WebSocketNetworkClient(rankedConfig != null && rankedConfig.isFirebase());
        network = client;
        onlineConnected = false;
        onlineResultReturned = false;
        roomState = null;
        installMatch(new OnlineMatchSession(newRunId(), client));
        networkSubscription = client.subscribe(update -> SwingUtilities.invokeLater(() -> {
            if (closed || network != client) return;
            onNetworkUpdate(update);
        }));
        client.connect(options);
    }

    private void onNetworkUpdate(NetworkUpdate update) {
        switch (update.getType()) {
            case CONNECTED:
                onlineConnected = true;
                roomList.setConnected(true);
                break;
            case ROOM_STATE:
                roomState = update.getRoomState();
                if (roomState.getPhase() == RoomState.Phase.CLOSED) {
                    roomState = null;
                    requestedRoomName = null;
                    show(ROOM_LIST);
                } else {
                    renderRoom();
                    if (roomState.getPhase() == RoomState.Phase.WAITING
                            && (WAITING_ROOM.equals(currentScreen) || ROOM_LIST.equals(currentScreen))) {
                        show(WAITING_ROOM);
                    }
                }
                break;
            case MATCH_STARTED:
                onlineResultReturned = false;
                rankedSaveStatus = "SAVE_PENDING";
                result.setRankedStatus(network instanceof WebSocketNetworkClient ? rankStatusText(rankedSaveStatus) : "");
                break;
            case RANKED_SAVE_STATUS:
                if (match == null || !update.getMatchId().equals(match.getSnapshot().getMatchId())) break;
                rankedSaveStatus = update.getReasonCode();
                result.setRankedStatus(rankStatusText(rankedSaveStatus));
                if ("SAVED".equals(rankedSaveStatus) && LEADERBOARD.equals(currentScreen)) loadLeaderboard();
                break;
            case CONNECTION_FAILED:
            case ERROR:
            case CLOSED: {
                onlineConnected = false;
                String reason = update.getReasonCode();
                if ("SERVICE_UNAVAILABLE".equals(reason)) {
                    roomList.setConnectionFailed("대전 접수가 닫혀 있습니다");
                } else if ("HANDSHAKE_FAILED".equals(reason)) {
                    roomList.setConnectionFailed("게임 서버 주소를 확인하세요");
                } else if ("AUTH_INVALID".equals(reason)) {
                    authEpoch++;
                    accountSession = null;
                    clearLeaderboardForIdentityChange();
                    authBusy = false;
                    setAccountStatus("로그인이 만료되었습니다. 다시 로그인하세요.");
                    roomList.setConnectionFailed("로그인이 만료되었습니다");
                    roomList.setRetryLabel("다시 로그인");
                } else {
                    roomList.setConnectionFailed(reason);
                }
                if (WAITING_ROOM.equals(currentScreen)) show(ROOM_LIST);
                if (BATTLE.equals(currentScreen)) battle.setFeedback("서버 연결이 끊겼습니다 · 결과를 확인하세요.");
                break;
            }
            case REQUEST_OUTCOME:
                if (update.getRequestOutcome().getRequestId() == refreshRequestId) {
                    refreshRequestId = -1;
                    if (!update.getRequestOutcome().isAccepted()) {
                        accountSession = null;
                        clearLeaderboardForIdentityChange();
                        setAccountStatus("인증이 만료되었습니다. 온라인 계정에 다시 로그인하세요.");
                    }
                }
                if (!update.getRequestOutcome().isAccepted()) {
                    if (BATTLE.equals(currentScreen))
                        battle.setFeedback("요청 거절 · " + update.getRequestOutcome().getReasonCode());
                    else message("요청 거절: " + update.getRequestOutcome().getReasonCode());
                }
                break;
            default: break;
        }
    }

    private void renderRoom() {
        if (roomState == null) return;
        Map<String, Boolean> ready = roomState.getReadyByParticipantId();
        String localId = roomState.getLocalParticipantId();
        PlayerData local = new PlayerData(localId, 1, "기본형 캐릭터", ready.get(localId));
        PlayerData opponent = null;
        for (Map.Entry<String, Boolean> entry : ready.entrySet()) {
            if (!entry.getKey().equals(localId)) {
                opponent = new PlayerData(entry.getKey(), 1, "기본형 캐릭터", entry.getValue());
                break;
            }
        }
        waitingRoom.setRoom(new RoomData(requestedRoomName == null ? roomState.getRoomId() : requestedRoomName,
                ready.size(), 2,
                roomState.getPhase() == RoomState.Phase.WAITING ? "Waiting" : "Playing"));
        waitingRoom.setRoomId(roomState.getRoomId());
        waitingRoom.setPlayers(local, opponent);
    }

    private void createRoom() {
        String name = JOptionPane.showInputDialog(frame, "방 이름을 입력하세요.",
                "방 만들기", JOptionPane.PLAIN_MESSAGE);
        if (name == null) return;
        createRoomWithName(name);
    }

    void createRoomWithName(String name) {
        ScreenRouter.requireEdt();
        if (name == null) return;
        name = name.trim();
        if (name.isEmpty()) { message("방 이름을 입력해주세요."); return; }
        if (!onlineConnected || network == null) { message("서버에 먼저 연결하세요."); return; }
        // 서버가 방 이름을 저장하지 않으므로 이 이름은 이 창의 제목에만 사용한다.
        requestedRoomName = name;
        sendRoomCommand(RoomCommand.createRoom(2));
    }

    private void promptJoin() {
        if (BATTLE.equals(currentScreen) || LOCAL_GAME.equals(currentScreen)) {
            message("진행 중인 게임을 끝낸 뒤 방에 입장하세요.");
            return;
        }
        String roomId = JOptionPane.showInputDialog(frame, "입장할 방 번호를 입력하세요.");
        if (roomId != null) joinRoom(roomId);
    }

    void joinRoom(String roomId) {
        if (!onlineConnected || network == null) { message("서버에 먼저 연결하세요."); return; }
        if (roomId.trim().isEmpty()) { message("방 번호를 입력하세요."); return; }
        requestedRoomName = null;
        sendRoomCommand(RoomCommand.joinRoom(roomId.trim()));
    }

    private void showRoomId() {
        message(roomState == null ? "아직 참가한 방이 없습니다." : "방 번호: " + roomState.getRoomId());
    }

    private void toggleReady() {
        if (roomState == null || network == null) return;
        if (roomState.getPhase() != RoomState.Phase.WAITING
                && (match == null || match.getSnapshot().getPhase() != SessionPhase.FINISHED)) return;
        boolean ready = roomState.getReadyByParticipantId().get(roomState.getLocalParticipantId());
        sendRoomCommand(RoomCommand.setReady(!ready));
    }

    private void leaveRoom() {
        if (roomState != null && network != null) sendRoomCommand(RoomCommand.leaveRoom());
        roomState = null;
        requestedRoomName = null;
        show(ROOM_LIST);
    }

    private void sendRoomCommand(RoomCommand command) {
        if (network == null) return;
        try { network.send(command); }
        catch (IllegalStateException error) { message("서버 접속 상태를 확인하세요."); }
    }

    private void message(String text) {
        lastMessage = text;
        if (BATTLE.equals(currentScreen) && match instanceof OnlineMatchSession) {
            battle.setFeedback(text);
            return;
        }
        if (frame != null) withLocalGamePaused(() -> JOptionPane.showMessageDialog(frame, text));
    }

    /** 모달의 중첩 EDT 루프에서도 로컬 게임 시간이 흐르지 않도록 보호한다. */
    void withLocalGamePaused(Runnable dialog) {
        ScreenRouter.requireEdt();
        LocalGameSession pausedGame = localGame != null && !localGame.isFinished()
                && !localGame.isPaused() ? localGame : null;
        TutorialSession pausedTutorial = tutorial != null && !tutorial.isFinished()
                && !tutorial.isPaused() ? tutorial : null;
        MatchSession pausedMatch = match instanceof LocalMatchSession
                && match.getSnapshot().getPhase() == SessionPhase.RUNNING ? match : null;
        if (pausedGame != null) pausedGame.pause();
        if (pausedTutorial != null) pausedTutorial.pause();
        if (pausedGame != null || pausedTutorial != null) localGravity.stop();
        if (pausedMatch != null) pausedMatch.requestPause(true);
        try {
            dialog.run();
        } finally {
            if (!closed) {
                if (pausedGame != null && pausedGame == localGame && !pausedGame.isFinished()) {
                    pausedGame.resume();
                    renderLocal();
                }
                if (pausedTutorial != null && pausedTutorial == tutorial && !pausedTutorial.isFinished()) {
                    pausedTutorial.resume();
                    renderLocal();
                }
                if (pausedMatch != null && pausedMatch == match
                        && pausedMatch.getSnapshot().getPhase() == SessionPhase.PAUSED)
                    pausedMatch.requestPause(false);
            }
        }
    }

    private static void clearSecrets(Container root) {
        for (Component component : root.getComponents()) {
            if (component instanceof JPasswordField) ((JPasswordField) component).setText("");
            else if (component instanceof Container) clearSecrets((Container) component);
        }
    }

    private String newRunId() { return UUID.randomUUID().toString(); }

    private void closeSession() {
        if (resultDelay != null) { resultDelay.stop(); resultDelay = null; }
        localGravity.stop();
        if (matchSubscription != null) { matchSubscription.close(); matchSubscription = null; }
        if (networkSubscription != null) { networkSubscription.close(); networkSubscription = null; }
        if (match != null) { match.close(); match = null; }
        if (network != null) { network.close(); network = null; }
        if (localGame != null) { localGame.close(); localGame = null; }
        if (tutorial != null) { tutorial.close(); tutorial = null; }
        roomState = null;
        requestedRoomName = null;
        onlineConnected = false;
        onlineResultReturned = false;
        storyBattle = false;
        displayedBattleMatchId = null;
        resultPresentedMatchId = null;
        rankedSaveStatus = null;
        refreshRequestId = -1;
        battle.resetFeedback();
        itemRequests.clear();
    }

    // 실제 GUI를 표시하기 전에도 테스트 가능한 시작 화면 준비 단계.
    void prepareFirstWindowScreen() {
        ScreenRouter.requireEdt();
        startTutorial(LOBBY);
    }

    public void openWindow() {
        ScreenRouter.requireEdt();
        if (frame != null) throw new IllegalStateException("Window is already open");
        frame = new JFrame("Tetris Monster");
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        frame.setMinimumSize(new Dimension(Math.min(820, usable.width), Math.min(650, usable.height)));
        frame.setSize(Math.min(1200, usable.width), Math.min(900, usable.height));
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.add(screens);
        frame.setGlassPane(transition);
        // 첫 화면이 실제로 나타나기 전에 튜토리얼 세션과 화면을 준비한다.
        // 기존에는 로비를 먼저 보여 준 뒤 invokeLater로 전환하여 로비가 잠깐 보였다.
        // 실행 시 항상 튜토리얼부터 시작하도록 하며, ESC는 기존처럼 로비로 이동한다.
        prepareFirstWindowScreen();
        frame.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) { keepWindowAspect(); }
        });
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { close(); }
        });
        frame.setVisible(true);
        keepWindowAspect();
        audio.startBgm();
        if (lastMessage != null && !lastMessage.isEmpty()) message(lastMessage);
    }

    /** 화면 내용의 4:3 비율을 유지하면서 너비·높이 어느 쪽으로든 창을 조절한다. */
    private void keepWindowAspect() {
        if (frame == null || !frame.isShowing() || correctingWindowSize) return;
        int viewWidth = frame.getContentPane().getWidth();
        int viewHeight = frame.getContentPane().getHeight();
        if (viewWidth < 1 || viewHeight < 1) return;
        int oldWidth = lastViewWidth, oldHeight = lastViewHeight;
        lastViewWidth = viewWidth; lastViewHeight = viewHeight;
        if (Math.abs(viewWidth * 3 - viewHeight * 4) <= 8) return;
        int targetWidth, targetHeight;
        if (oldWidth == 0 || Math.abs(viewWidth - oldWidth) >= Math.abs(viewHeight - oldHeight)) {
            targetWidth = viewWidth; targetHeight = Math.round(targetWidth * 0.75f);
        } else {
            targetHeight = viewHeight; targetWidth = Math.round(targetHeight * 4f / 3f);
        }
        Insets border = frame.getInsets();
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int maxWidth = Math.max(1, usable.width - border.left - border.right);
        int maxHeight = Math.max(1, usable.height - border.top - border.bottom);
        double scale = Math.min(1d, Math.min((double) maxWidth / targetWidth,
                (double) maxHeight / targetHeight));
        targetWidth = Math.max(1, (int) Math.floor(targetWidth * scale));
        targetHeight = Math.max(1, Math.round(targetWidth * 0.75f));
        if (Math.abs(viewWidth - targetWidth) < 2 && Math.abs(viewHeight - targetHeight) < 2) return;
        correctingWindowSize = true;
        try { frame.setSize(targetWidth + border.left + border.right,
                targetHeight + border.top + border.bottom); }
        finally { correctingWindowSize = false; }
        lastViewWidth = targetWidth; lastViewHeight = targetHeight;
    }

    @Override public void close() {
        ScreenRouter.requireEdt();
        if (closed) return;
        closeSession();
        authRefresh.stop();
        authEpoch++;
        accountSession = null;
        audio.close();
        clearSecrets(login);
        clearSecrets(signUp);
        closed = true;
        if (frame != null && frame.isDisplayable()) frame.dispose();
    }

    String getCurrentScreen() { return currentScreen; }
    String getLastMessage() { return lastMessage; }
    GameState getLocalState() { return localGame != null ? localGame.getPlayerState()
            : tutorial != null ? tutorial.getPlayerState() : null; }
    SessionSnapshot getMatchSnapshot() { return match == null ? null : match.getSnapshot(); }
    RoomState getRoomState() { return roomState; }
    boolean isOnlineConnected() { return onlineConnected; }
    boolean isSignedIn() { return accountSession != null; }
    int getCompletedAuthRequests() { return completedAuthRequests; }
    boolean isLocalGravityRunning() { return localGravity.isRunning(); }
    boolean isStoryClockRunning() { return match instanceof LocalMatchSession
            && ((LocalMatchSession) match).isGravityRunning(); }
    CampaignProgress getCampaignProgress() { return progress.getCampaignProgress(); }
    JPanel getScreens() { return screens; }
    JMenuBar getMenu() { return menu; }
    PlayerSaveStore.Data getSaveData() { return saveData; }
    boolean isSaveDirty() { return saveDirty; }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SeongeunApplication().openWindow());
    }
}
