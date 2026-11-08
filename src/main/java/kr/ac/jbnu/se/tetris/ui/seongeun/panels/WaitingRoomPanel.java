package kr.ac.jbnu.se.tetris.ui.seongeun.panels;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.*;
import kr.ac.jbnu.se.tetris.ui.seongeun.model.PlayerData;
import kr.ac.jbnu.se.tetris.ui.seongeun.model.RoomData;

public class WaitingRoomPanel extends ScenePanel {
    private final GameButton backButton=new GameButton("방 나가기");
    private final GameButton readyButton=new GameButton("준비 완료");
    private final GameButton copyButton=new GameButton("번호 복사");
    private final JLabel titleLabel;
    private final JLabel roomCode=CampusArt.text("—",24,CampusArt.BROWN);
    private final JPanel playerPanel=new JPanel(new GridBagLayout());
    private String roomId;
    public WaitingRoomPanel(){
        setLayout(new BorderLayout(0,15));setBorder(new EmptyBorder(22,38,22,38));
        backButton.secondary();backButton.setPreferredSize(new Dimension(160,52));
        JPanel header=UniversityPixelTheme.screenHeader("ONLINE BATTLE · WAITING ROOM","대기실",backButton);
        titleLabel=findTitle(header);add(header,BorderLayout.NORTH);
        JPanel body=new JPanel(new BorderLayout(0,18));body.setOpaque(false);
        body.add(codeCard(),BorderLayout.NORTH);
        playerPanel.setOpaque(false);
        body.add(playerPanel,BorderLayout.CENTER);
        add(body,BorderLayout.CENTER);
        readyButton.setName("waitingReady");readyButton.positive();
        readyButton.setFont(UniversityPixelTheme.font(22,Font.BOLD));
        readyButton.setPreferredSize(new Dimension(310,70));
        JPanel footer=new JPanel(new FlowLayout(FlowLayout.CENTER));footer.setOpaque(false);
        footer.add(readyButton);add(footer,BorderLayout.SOUTH);
        setPlayers(null,null);
    }
    private JPanel codeCard(){
        CampusArt.Card card=CampusArt.card(new BorderLayout(16,0),CampusArt.BROWN,14);
        JLabel prefix=CampusArt.text("방 번호",22,CampusArt.BROWN);
        card.add(prefix,BorderLayout.WEST);
        roomCode.setHorizontalAlignment(SwingConstants.CENTER);
        roomCode.setToolTipText("다른 창의 '방 번호로 입장'에 이 번호를 입력하세요.");
        card.add(roomCode,BorderLayout.CENTER);
        copyButton.setPreferredSize(new Dimension(155,48));copyButton.primary();copyButton.setEnabled(false);
        copyButton.addActionListener(event->copyRoomId());
        card.add(copyButton,BorderLayout.EAST);
        card.setPreferredSize(new Dimension(1050,106));return card;
    }
    private void copyRoomId(){
        if(roomId==null)return;
        try{Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(roomId),null);copyButton.setText("복사됨");}
        catch(HeadlessException|IllegalStateException unavailable){copyButton.setText("복사 불가");}
    }
    private static JLabel findTitle(Container parent){
        for(Component child:parent.getComponents()){
            if(child instanceof JLabel&&"screenTitle".equals(child.getName()))return (JLabel)child;
            if(child instanceof Container){JLabel found=findTitle((Container)child);if(found!=null)return found;}
        }return null;
    }
    public void setRoom(RoomData room){if(room!=null&&titleLabel!=null)titleLabel.setText(room.getRoomName());}
    public void setRoomId(String id){roomId=id==null||id.isEmpty()?null:id;
        roomCode.setText(roomId==null?"—":roomId);copyButton.setEnabled(roomId!=null);copyButton.setText("번호 복사");}
    public void setBackAction(ActionListener listener){backButton.addActionListener(listener);}
    public void setReadyAction(ActionListener listener){readyButton.addActionListener(listener);}
    public void setPlayers(PlayerData mine,PlayerData enemy){
        playerPanel.removeAll();GridBagConstraints c=new GridBagConstraints();
        c.gridy=0;c.weightx=0.45;c.weighty=1;c.fill=GridBagConstraints.BOTH;c.insets=new Insets(10,10,10,10);
        c.gridx=0;playerPanel.add(slot("나",mine,CampusArt.GREEN),c);
        JLabel versus=CampusArt.text("VS",55,CampusArt.GOLD);versus.setHorizontalAlignment(SwingConstants.CENTER);
        c.gridx=1;c.weightx=0.1;c.fill=GridBagConstraints.NONE;playerPanel.add(versus,c);
        c.gridx=2;c.weightx=0.45;c.fill=GridBagConstraints.BOTH;playerPanel.add(slot("상대",enemy,CampusArt.BROWN),c);
        boolean ready=mine!=null&&mine.isReady();readyButton.setText(ready?"준비 취소":"준비 완료");
        if(ready)readyButton.secondary();else readyButton.positive();
        playerPanel.revalidate();playerPanel.repaint();
    }
    private static JPanel slot(String role,PlayerData player,Color edge){
        CampusArt.Card card=CampusArt.card(new BorderLayout(0,10),edge,15);
        JLabel name=CampusArt.text(role,22,edge);name.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(name,BorderLayout.NORTH);
        JPanel center=new JPanel(new GridBagLayout());center.setOpaque(false);
        JLabel portrait=player==null?CampusArt.text("?",76,new Color(0xB9B1A5)):CampusArt.icon("avatar_player",146,146);
        center.add(portrait);card.add(center,BorderLayout.CENTER);
        JPanel info=new JPanel();info.setOpaque(false);info.setLayout(new BoxLayout(info,BoxLayout.Y_AXIS));
        String nickname=player==null?"입장 대기 중...":shortName(player.getNickname());
        JLabel user=CampusArt.text(nickname,19,CampusArt.INK);
        if(player!=null)user.setToolTipText(player.getNickname());
        JLabel character=CampusArt.text(player==null?" ":player.getCharacterName(),13,CampusArt.BROWN);
        JLabel status=CampusArt.text(player==null?"● WAITING":player.isReady()?"● READY":"● NOT READY",15,
                player!=null&&player.isReady()?CampusArt.GREEN:CampusArt.BROWN);
        for(JLabel l:new JLabel[]{user,character,status}){l.setAlignmentX(Component.CENTER_ALIGNMENT);info.add(l);info.add(Box.createVerticalStrut(5));}
        card.add(info,BorderLayout.SOUTH);return card;
    }
    private static String shortName(String name){if(name==null)return "";return name.length()>14?name.substring(0,8)+"…":name;}
}
