package kr.ac.jbnu.se.tetris.ui.seongeun.panels;

import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import kr.ac.jbnu.se.tetris.story.StoryDialogueCue;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.*;

/** Reference-style result scene. The numbers and navigation callbacks remain live. */
public class ResultPanel extends ScenePanel {
    private final JLabel resultLabel = new FancyTitle();
    private JLabel logo;
    private JPanel content;
    private JPanel buttons;
    private final JLabel playerNameLabel = new JLabel("PLAYER", SwingConstants.CENTER);
    private final JLabel lineLabel = number();
    private final JLabel comboLabel = number();
    private final JLabel damageLabel = number();
    private final JLabel rewardLabel = number();
    private final JLabel rankedStatus = CampusArt.text("", 13, CampusArt.NAVY);
    private final JLabel milestone = CampusArt.text("", 13, CampusArt.GREEN);
    private final JTextArea storyDialogue = new JTextArea();
    private final JLabel flavor = CampusArt.text("플레이어: 휴...이번 학기는 살았다...",17,CampusArt.NAVY);
    private final CampusArt.Card dialogueCard=CampusArt.card(new BorderLayout(12,0),CampusArt.GREEN,10);
    private final GameButton returnButton = new GameButton("돌아가기");
    private final GameButton lobbyButton = new GameButton("로비로");
    private final GameButton nextButton = new GameButton("다음 전투");
    private final GameButton retryButton = new GameButton("다시 도전");
    private final JPanel resultCard = CampusArt.card(new BorderLayout(0, 12), CampusArt.BROWN, 20);
    private final Timer countTimer = new Timer(30, event -> refreshCount());
    private String[] targets = { "0", "0", "0", "0" };
    private long countStartedAt;
    private boolean victory = true;

    public ResultPanel() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(16, 12, 16, 12));
        content = new JPanel();content.setOpaque(false);
        content.setLayout(new BoxLayout(content,BoxLayout.Y_AXIS));
        content.setPreferredSize(new Dimension(950,750));
        content.setMaximumSize(new Dimension(1150,860));
        logo = new JLabel() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try { CampusArt.fit(g, CampusArt.image("/ui/campus-rpg/reference-logo.png"),
                        0, 0, getWidth(), getHeight()); }
                finally { g.dispose(); }
            }
        };
        logo.setPreferredSize(new Dimension(320,180));
        logo.setMaximumSize(new Dimension(Integer.MAX_VALUE,180));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(logo);
        resultLabel.setForeground(CampusArt.GREEN);
        resultLabel.setFont(UniversityPixelTheme.font(72,Font.BOLD));
        resultLabel.setPreferredSize(new Dimension(800,120));
        resultLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE,120));
        resultLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(resultLabel);
        content.add(Box.createVerticalStrut(10));
        playerNameLabel.setFont(UniversityPixelTheme.font(20,Font.BOLD));
        playerNameLabel.setForeground(CampusArt.NAVY);
        resultCard.add(playerNameLabel,BorderLayout.NORTH);
        JPanel stats = new JPanel(new GridLayout(1,4,11,0));stats.setOpaque(false);
        stats.add(stat("지운 줄",lineLabel,"drop_speed"));
        stats.add(stat("최대 콤보",comboLabel,"item_fever"));
        stats.add(stat("입힌 피해",damageLabel,"item_damage"));
        stats.add(stat("획득 코인",rewardLabel,"item"));
        resultCard.add(stats,BorderLayout.CENTER);
        JPanel message=new JPanel();message.setOpaque(false);
        message.setLayout(new BoxLayout(message,BoxLayout.Y_AXIS));
        for (JLabel label:new JLabel[]{milestone,rankedStatus}) {
            label.setAlignmentX(Component.CENTER_ALIGNMENT);message.add(label);
        }
        resultCard.add(message,BorderLayout.SOUTH);
        resultCard.setPreferredSize(new Dimension(920,305));
        resultCard.setMaximumSize(new Dimension(Integer.MAX_VALUE,330));
        resultCard.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(resultCard);
        content.add(Box.createVerticalStrut(12));
        dialogueCard.setMaximumSize(new Dimension(Integer.MAX_VALUE,76));
        dialogueCard.setPreferredSize(new Dimension(920,74));
        dialogueCard.add(CampusArt.icon("avatar_player",58,58),BorderLayout.WEST);
        storyDialogue.setEditable(false);storyDialogue.setFocusable(false);
        storyDialogue.setOpaque(false);storyDialogue.setLineWrap(true);
        storyDialogue.setWrapStyleWord(true);
        storyDialogue.setForeground(CampusArt.NAVY);
        storyDialogue.setFont(UniversityPixelTheme.font(15,Font.BOLD));
        dialogueCard.add(flavor,BorderLayout.CENTER);
        storyDialogue.setVisible(false);
        flavor.setVisible(true);
        dialogueCard.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(dialogueCard);
        content.add(Box.createVerticalStrut(14));
        buttons=new JPanel(null) {
            @Override public void doLayout() {
                int count = 0;
                for (Component child : getComponents()) if (child.isVisible()) count++;
                if (count == 0) return;
                int gap = 16;
                int cellW = Math.max(56, Math.min(250, (getWidth() - gap * (count - 1)) / count));
                int cellH = Math.max(32, Math.min(56, getHeight() - 4));
                int left = (getWidth() - cellW * count - gap * (count - 1)) / 2;
                int y = (getHeight() - cellH) / 2;
                int index = 0;
                for (Component child : getComponents()) {
                    if (!child.isVisible()) continue;
                    child.setBounds(left + index++ * (cellW + gap), y, cellW, cellH);
                }
            }
        };
        buttons.setOpaque(false);
        for(GameButton button:new GameButton[]{lobbyButton,returnButton,retryButton,nextButton}){
            button.setFont(UniversityPixelTheme.font(17,Font.BOLD));
            button.setPreferredSize(new Dimension(180,56));buttons.add(button);
        }
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE,62));
        buttons.setAlignmentX(Component.CENTER_ALIGNMENT);
        nextButton.setVisible(false);retryButton.setVisible(false);
        lobbyButton.secondary();
        lobbyButton.setFont(UniversityPixelTheme.font(20, Font.BOLD));
        lobbyButton.setName("resultLobbyButton");
        nextButton.primary();
        content.add(buttons);
        add(content,BorderLayout.CENTER);
        applyButtonRoles();
    }

    @Override public void doLayout() {
        int h = getHeight();
        boolean compact = h < 760;
        int logoH = compact ? 100 : 170;
        int titleH = compact ? 76 : 116;
        int statsH = compact ? 265 : 300;
        int quoteH = compact ? 58 : 76;
        int buttonH = compact ? 49 : 62;
        logo.setMinimumSize(new Dimension(80, logoH));
        logo.setPreferredSize(new Dimension(320, logoH));
        logo.setMaximumSize(new Dimension(Integer.MAX_VALUE,logoH));
        resultLabel.setMinimumSize(new Dimension(100,titleH));
        resultLabel.setPreferredSize(new Dimension(800,titleH));
        resultLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE,titleH));
        resultCard.setMinimumSize(new Dimension(320,statsH));
        resultCard.setPreferredSize(new Dimension(900,statsH));
        resultCard.setMaximumSize(new Dimension(Integer.MAX_VALUE,statsH));
        dialogueCard.setMinimumSize(new Dimension(200,quoteH));
        dialogueCard.setPreferredSize(new Dimension(900,quoteH));
        dialogueCard.setMaximumSize(new Dimension(Integer.MAX_VALUE,quoteH));
        Font statFont = UniversityPixelTheme.font(compact?27:40,Font.BOLD);
        for (JLabel l: new JLabel[]{lineLabel,comboLabel,damageLabel,rewardLabel}) l.setFont(statFont);
        buttons.setMinimumSize(new Dimension(200,buttonH));
        buttons.setPreferredSize(new Dimension(900,buttonH));
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE,buttonH));
        content.setPreferredSize(new Dimension(Math.min(950,Math.max(320,getWidth()-24)),
                Math.min(820,Math.max(260,getHeight()-24))));
        super.doLayout();
    }

    private static final class FancyTitle extends JLabel {
        FancyTitle(){super("VICTORY",SwingConstants.CENTER);setOpaque(false);}
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();
            try{
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w=getWidth(),h=getHeight();
                String title=getText();if(title==null)return;
                Font font=UniversityPixelTheme.font(Math.min(76,Math.max(34,Math.min(w/12,(h-20)*3/4))),Font.BOLD);
                java.awt.font.GlyphVector glyph=font.createGlyphVector(g.getFontRenderContext(),title);
                Rectangle bounds=glyph.getPixelBounds(g.getFontRenderContext(),0,0);
                float x=(w-bounds.width)/2f-bounds.x,y=(h-bounds.height)/2f-bounds.y;
                Shape shape=glyph.getOutline(x,y);
                g.setColor(new Color(0x102443));g.setStroke(new BasicStroke(12f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.draw(shape);
                g.setColor(new Color(0xFFFFFF));g.setStroke(new BasicStroke(4f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.draw(shape);
                Color top="DEFEAT".equals(title)?new Color(0xE4F1FF):new Color(0xEFFFF0);
                Color bottom="DEFEAT".equals(title)?new Color(0x7895D1):new Color(0x00C4DD);
                g.setPaint(new GradientPaint(0,0,top,0,h,bottom));g.fill(shape);
            }finally{g.dispose();}
        }
    }

    private static JLabel number() {
        JLabel label = new JLabel("0",SwingConstants.CENTER);
        label.setFont(UniversityPixelTheme.font(35,Font.BOLD));
        label.setForeground(CampusArt.NAVY);return label;
    }
    private JPanel stat(String name,JLabel value,String icon) {
        CampusArt.Card card=CampusArt.card(new BorderLayout(0,5),new Color(0xF2D8AC),8);
        card.setBackground(CampusArt.WHITE);
        card.add(CampusArt.icon(icon,46,46),BorderLayout.NORTH);
        JPanel bottom=new JPanel(new BorderLayout());bottom.setOpaque(false);
        JLabel label=CampusArt.text(name,15,CampusArt.NAVY);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        bottom.add(label,BorderLayout.NORTH);
        bottom.add(value,BorderLayout.CENTER);
        card.add(bottom,BorderLayout.CENTER);return card;
    }

    public void setResult(String result,String playerName,int line,int combo,int damage,int reward){
        setResultDetails(result,playerName,String.valueOf(line),String.valueOf(combo),String.valueOf(damage),String.valueOf(reward));
    }
    public void setResultDetails(String result,String playerName,String line,String maxCombo,String damage,String reward){
        victory="VICTORY".equals(result)||"COMPLETE".equals(result);
        boolean loss="DEFEAT".equals(result)||"GAME OVER".equals(result)||"CONNECTION LOST".equals(result);
        resultLabel.setText(victory?"VICTORY":loss?"DEFEAT":result);
        resultLabel.setForeground(victory?new Color(0x15C8CB):loss?new Color(0xA9BEDA):CampusArt.GOLD);
        flavor.setText(victory?"플레이어: 휴...이번 학기는 살았다...":loss?"플레이어: 아... 재수강은 피하고 싶었는데...":"플레이어: 다음 도전을 준비하자!");
        playerNameLabel.setText(playerName==null?"PLAYER":playerName);
        targets=new String[]{line,maxCombo,damage,reward};
        countStartedAt=System.currentTimeMillis();refreshCount();countTimer.restart();repaint();
    }
    private void refreshCount(){
        JLabel[] labels={lineLabel,comboLabel,damageLabel,rewardLabel};
        float t=Math.min(1f,(System.currentTimeMillis()-countStartedAt)/800f);
        float ease=1-(1-t)*(1-t)*(1-t);
        for(int i=0;i<labels.length;i++){
            String value=targets[i]==null?"":targets[i];
            try{labels[i].setText(Integer.toString(Math.round(Integer.parseInt(value)*ease)));}
            catch(NumberFormatException notNumber){labels[i].setText(value);}
        }
        if(t>=1f)countTimer.stop();
    }
    @Override protected void paintComponent(Graphics graphics){
        super.paintComponent(graphics);
        if(!victory || countStartedAt==0)return;
        Graphics2D g=(Graphics2D)graphics.create();
        try{
            int w=getWidth(),h=getHeight();long age=System.currentTimeMillis()-countStartedAt;
            Color[] colors={CampusArt.GOLD,CampusArt.GREEN,new Color(0x35DAED),new Color(0xF67E8C)};
            for(int i=0;i<35;i++){
                float t=((age+i*103)%3300)/3300f;
                int x=Math.floorMod(i*197+17,Math.max(1,w));int y=(int)(-15+t*(h+30));
                g.setColor(colors[i%colors.length]);g.fillOval(x,y,4+i%5,4+i%5);
            }
        }finally{g.dispose();}
    }
    public void setReturnButtonText(String text){
        returnButton.setText(text);
        // When returning already leads to the lobby, avoid two identical exits.
        lobbyButton.setVisible(!"로비로".equals(text));
        applyButtonRoles();
    }
    public void setReturnAction(ActionListener listener){returnButton.addActionListener(listener);}
    public void setLobbyAction(ActionListener listener){lobbyButton.addActionListener(listener);}
    public void setRankedStatus(String text){rankedStatus.setText(text);}
    public void setStoryDialogue(List<StoryDialogueCue> cues){
        StringBuilder builder=new StringBuilder();
        if(cues!=null)for(StoryDialogueCue cue:cues){
            if(builder.length()>0)builder.append('\n');
            builder.append(cue.getSpeaker()).append(": ").append(cue.getText());
        }
        storyDialogue.setText(builder.toString());
        boolean hasDialogue=builder.length()>0;
        if (hasDialogue) dialogueCard.add(storyDialogue,BorderLayout.CENTER);
        else dialogueCard.add(flavor,BorderLayout.CENTER);
        storyDialogue.setVisible(hasDialogue);flavor.setVisible(!hasDialogue);
        dialogueCard.revalidate();dialogueCard.repaint();
    }
    public void setStoryActions(boolean story,boolean hasNext,String earnedBadge){
        nextButton.setVisible(story&&hasNext);retryButton.setVisible(story);
        milestone.setText(earnedBadge==null||earnedBadge.isEmpty()?"":"획득 · "+earnedBadge);
        applyButtonRoles();
    }
    public void setNextAction(ActionListener listener){nextButton.addActionListener(listener);}
    public void setRetryAction(ActionListener listener){retryButton.addActionListener(listener);}
    private void applyButtonRoles(){
        returnButton.secondary();retryButton.secondary();nextButton.primary();
        if(!nextButton.isVisible()){
            if(retryButton.isVisible())retryButton.primary();else returnButton.primary();
        }
        buttons.revalidate();
        buttons.repaint();
    }
}
