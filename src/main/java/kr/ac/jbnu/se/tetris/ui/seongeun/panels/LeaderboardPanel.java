package kr.ac.jbnu.se.tetris.ui.seongeun.panels;

import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import kr.ac.jbnu.se.tetris.ranking.LeaderboardEntry;
import kr.ac.jbnu.se.tetris.ui.seongeun.components.*;

/** Ranked online list with real-time podium, not placeholder player data. */
public final class LeaderboardPanel extends ScenePanel {
    private final PixelButton back=new CampusBackButton("돌아가기"),reload=new PixelButton("새로고침");
    private final JLabel status=new JLabel("");
    private final CardLayout views=new CardLayout();
    private final JPanel body=new JPanel(views);
    private final JPanel podium=new JPanel(new GridLayout(1,3,14,0));
    private final JLabel emptyTitle=new JLabel("랭킹을 불러오지 않았습니다",SwingConstants.CENTER);
    private final DefaultTableModel model=new DefaultTableModel(new Object[]{"순위","이름","Elo","승","패","경기"},0){
        @Override public boolean isCellEditable(int row,int column){return false;}
    };
    public LeaderboardPanel(){
        setLayout(new BorderLayout(0,12));setBorder(new EmptyBorder(18,30,17,30));
        back.secondary();reload.secondary();
        back.setPreferredSize(new Dimension(190,56));reload.setPreferredSize(new Dimension(165,52));
        add(UniversityPixelTheme.screenHeader("RANKED PvP","온라인 랭킹 TOP 100",reload,back),BorderLayout.NORTH);
        body.setOpaque(false);
        JPanel board=new JPanel(new BorderLayout(0,10));board.setOpaque(false);
        podium.setOpaque(false);podium.setPreferredSize(new Dimension(0,170));
        board.add(podium,BorderLayout.NORTH);
        JTable table=new JTable(model);table.setName("ranked-leaderboard");
        table.setRowHeight(43);
        table.setFont(UniversityPixelTheme.font(16,Font.PLAIN));
        table.setBackground(CampusArt.WHITE);table.setForeground(CampusArt.NAVY);
        table.setSelectionBackground(new Color(0xD9EEFF));table.setSelectionForeground(CampusArt.NAVY);
        table.setShowVerticalLines(false);table.setShowHorizontalLines(false);
        table.getTableHeader().setFont(UniversityPixelTheme.font(16,Font.BOLD));
        table.getTableHeader().setBackground(CampusArt.GREEN);
        table.getTableHeader().setForeground(Color.WHITE);
        table.getTableHeader().setPreferredSize(new Dimension(10,45));
        table.getTableHeader().setReorderingAllowed(false);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(90);
        table.getColumnModel().getColumn(1).setPreferredWidth(260);
        DefaultTableCellRenderer render=new DefaultTableCellRenderer(){
            @Override public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int col){
                super.getTableCellRendererComponent(t,value,selected,false,row,col);
                setHorizontalAlignment(col==1?SwingConstants.LEFT:SwingConstants.CENTER);
                setBorder(new EmptyBorder(0,14,0,14));
                boolean special=row<3;
                setFont(UniversityPixelTheme.font(15,special?Font.BOLD:Font.PLAIN));
                setForeground(special&&col==0?CampusArt.BROWN:CampusArt.NAVY);
                if(!selected)setBackground(row==0?new Color(0xFFF0C2):row==1?new Color(0xE4F0FC):row==2?new Color(0xFFE5D0):
                    row%2==0?CampusArt.WHITE:new Color(0xF8F3EA));
                return this;
            }
        };
        for(int i=0;i<model.getColumnCount();i++)table.getColumnModel().getColumn(i).setCellRenderer(render);
        JScrollPane scroll=new JScrollPane(table);
        scroll.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(CampusArt.GREEN,4),
                new EmptyBorder(7,7,7,7)));
        scroll.getViewport().setBackground(CampusArt.WHITE);
        board.add(scroll,BorderLayout.CENTER);
        body.add(board,"table");body.add(emptyState(),"empty");views.show(body,"empty");
        add(body,BorderLayout.CENTER);
        status.setForeground(CampusArt.NAVY);status.setFont(UniversityPixelTheme.font(13,Font.BOLD));
        add(status,BorderLayout.SOUTH);
    }
    private JPanel emptyState(){
        CampusArt.Card empty=CampusArt.card(new GridBagLayout(),CampusArt.GREEN,25);
        JPanel content=new JPanel();content.setOpaque(false);
        content.setLayout(new BoxLayout(content,BoxLayout.Y_AXIS));
        JLabel icon=CampusArt.icon("graduation_cap",120,120);icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        emptyTitle.setFont(UniversityPixelTheme.font(23,Font.BOLD));
        emptyTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(icon);content.add(Box.createVerticalStrut(14));content.add(emptyTitle);
        empty.add(content);return empty;
    }
    private JPanel podiumCard(int place,LeaderboardEntry player){
        Color color=place==1?CampusArt.GOLD:place==2?new Color(0x91B6D9):new Color(0xD99460);
        CampusArt.Card card=CampusArt.card(new BorderLayout(6,6),color,10);
        JLabel number=CampusArt.text(place==1?"★ 1위":place==2?"2위":"3위",21,CampusArt.NAVY);
        number.setHorizontalAlignment(SwingConstants.CENTER);card.add(number,BorderLayout.NORTH);
        String name=player==null?"—":player.getDisplayName();
        String score=player==null?"—":String.valueOf(player.getRating());
        JLabel portrait=CampusArt.icon("avatar_player",70,70);
        JPanel mid=new JPanel(new FlowLayout(FlowLayout.CENTER,8,0));mid.setOpaque(false);
        mid.add(portrait);
        JLabel user=CampusArt.text(name,18,CampusArt.NAVY);
        if(name.length()>16){user.setText(name.substring(0,13)+"…");user.setToolTipText(name);}
        mid.add(user);card.add(mid,BorderLayout.CENTER);
        JLabel elo=CampusArt.text("✦  "+score+" Elo",19,CampusArt.BROWN);
        elo.setHorizontalAlignment(SwingConstants.CENTER);card.add(elo,BorderLayout.SOUTH);
        return card;
    }
    public void setEntries(List<LeaderboardEntry> entries){
        model.setRowCount(0);podium.removeAll();
        for(LeaderboardEntry entry:entries)model.addRow(new Object[]{entry.getRank(),entry.getDisplayName(),
            entry.getRating(),entry.getWins(),entry.getLosses(),entry.getGames()});
        LeaderboardEntry[] best=new LeaderboardEntry[3];
        for(LeaderboardEntry entry:entries){if(entry.getRank()>=1&&entry.getRank()<=3)best[entry.getRank()-1]=entry;}
        podium.add(podiumCard(2,best[1]));podium.add(podiumCard(1,best[0]));podium.add(podiumCard(3,best[2]));
        podium.revalidate();podium.repaint();
        if(entries.isEmpty()){emptyTitle.setText("전적 없음");views.show(body,"empty");}
        else views.show(body,"table");
        setStatus(entries.isEmpty()?"":entries.size()+"명");
    }
    public void setStatus(String text){status.setText(text);}
    public void setError(String text){setStatus(text);if(model.getRowCount()==0){emptyTitle.setText("랭킹을 불러오지 못했습니다");views.show(body,"empty");}}
    public void setBusy(boolean busy){reload.setEnabled(!busy);if(busy){status.setText("랭킹을 불러오는 중...");if(model.getRowCount()==0)emptyTitle.setText("랭킹을 불러오는 중...");}}
    public void setBackAction(ActionListener action){back.addActionListener(action);}
    public void setReloadAction(ActionListener action){reload.addActionListener(action);}
}
