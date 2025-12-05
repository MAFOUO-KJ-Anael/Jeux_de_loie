package src;
import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * JeuDeLOie.java
 * Exemple complet en Swing: menu, sélection niveau, jeu, stats.
 *
 * Pour compiler:
 *   javac JeuDeLOie.java
 * Pour lancer:
 *   java JeuDeLOie
 *
 * Place les images de plateaux et assets dans ./assets/
 */
public class JeuDeLOie {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame());
    }
}

/* ---------- MainFrame: gère navigation entre panels ---------- */
class MainFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    CardLayout cards = new CardLayout();
    JPanel root = new JPanel(cards);

    MenuPanel menuPanel;
    NewGamePanel newGamePanel;
    GamePanel gamePanel;
    StatsPanel statsPanel;

    MainFrame() {
        setTitle("Jeu de l'Oie");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 900);
        setLocationRelativeTo(null);
        setResizable(false);

        menuPanel = new MenuPanel(this);
        newGamePanel = new NewGamePanel(this);
        gamePanel = new GamePanel(this);
        statsPanel = new StatsPanel(this);

        root.add(menuPanel, "MENU");
        root.add(newGamePanel, "NEWGAME");
        root.add(gamePanel, "GAME");
        root.add(statsPanel, "STATS");

        add(root);
        showMenu();
        setVisible(true);
    }

    void showMenu(){ cards.show(root, "MENU"); }
    void showNewGame(){ cards.show(root, "NEWGAME"); }
    void showGame(GameConfig cfg){ gamePanel.startNewGame(cfg); cards.show(root, "GAME"); }
    void showStats(){ statsPanel.loadScores(); cards.show(root,"STATS"); }
}

/* ---------- Config for a new game ---------- */
class GameConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    String level; // "FACILE" or "DIFFICILE"
    int nPlayers;
    boolean includeAI;

    GameConfig(String level, int nPlayers, boolean includeAI) {
        this.level = level;
        this.nPlayers = nPlayers;
        this.includeAI = includeAI;
    }
}

/* ---------- MenuPanel ---------- */
class MenuPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    MainFrame parent;
    MenuPanel(MainFrame p){
        parent = p;
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(15,15,15,15);
        JLabel title = new JLabel("Jeu de l'Oie");
        title.setFont(new Font("SansSerif", Font.BOLD, 36));
        c.gridx=0; c.gridy=0; add(title,c);

        JButton newGame = new JButton("Nouvelle partie");
        newGame.addActionListener(e -> parent.showNewGame());
        c.gridy=1; add(newGame,c);

        JButton stats = new JButton("Statistique du jeu");
        stats.addActionListener(e -> parent.showStats());
        c.gridy=2; add(stats,c);

        JButton about = new JButton("A propos du jeu");
        about.addActionListener(e ->
            JOptionPane.showMessageDialog(this,
                "Jeu de l'Oie - Prototype\nDéveloppé en Java Swing\nFonctionnalités: plusieurs niveaux, scores, IA simple.")
        );
        c.gridy=3; add(about,c);
    }
}

/* ---------- NewGamePanel ---------- */
class NewGamePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    MainFrame parent;
    JComboBox<String> levelCombo;
    JComboBox<Integer> playersCombo;
    JCheckBox aiCheck;

    NewGamePanel(MainFrame p){
        parent = p;
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10,10,10,10);

        c.gridx=0; c.gridy=0;
        add(new JLabel("Choisir le niveau :"), c);
        levelCombo = new JComboBox<>(new String[] {"FACILE","DIFFICILE"});
        c.gridx=1; add(levelCombo,c);

        c.gridx=0; c.gridy=1;
        add(new JLabel("Nombre de joueurs (1-3) :"), c);
        playersCombo = new JComboBox<>(new Integer[]{1,2,3});
        c.gridx=1; add(playersCombo,c);

        c.gridx=0; c.gridy=2;
        aiCheck = new JCheckBox("Inclure ordinateur (AI)");
        add(aiCheck, c);

        JButton start = new JButton("Valider et Lancer");
        start.addActionListener(e -> {
            String lvl = (String)levelCombo.getSelectedItem();
            int n = (Integer)playersCombo.getSelectedItem();
            boolean ai = aiCheck.isSelected();
            parent.showGame(new GameConfig(lvl, n, ai));
        });
        c.gridx=0; c.gridy=3; c.gridwidth=2; add(start,c);

        JButton back = new JButton("Retour");
        back.addActionListener(e -> parent.showMenu());
        c.gridy=4; add(back,c);
    }
}

/* ---------- GamePanel: logique du jeu et rendu ---------- */
class GamePanel extends JPanel {
    private int targetPosition;
    //  Coordonnées exactes du plateau d'un niveau (FACILE (index 1..47))
    private Point[] easyBoardCoords = new Point[49];
    private static final long serialVersionUID = 1L;

    MainFrame parent;
    GameConfig cfg;
    BufferedImage boardImage;
    int boardSizeX, boardSizeY;

    final int MAX_PLAYERS = 4;
    Color[] pawnColors = new Color[]{Color.RED, Color.BLUE, Color.GREEN, Color.ORANGE};

    List<Player> players;
    int currentPlayer = 0;
    Random rand = new Random();

    Map<Integer,Integer> specialMoves = new HashMap<>();
    int finalSquare = 100;
    JLabel statusLabel;
    JButton rollBtn;
    JLabel diceLabel;

    ScoreManager scoreManager = new ScoreManager();

    GamePanel(MainFrame p){
        parent = p;
        setLayout(null);

        statusLabel = new JLabel("Bienvenue !");
        statusLabel.setBounds(10, 10, 600, 20);
        add(statusLabel);

        rollBtn = new JButton("Lancer le dé");
        rollBtn.setBounds(10,40,130,30);
        rollBtn.addActionListener(e -> doRoll());
        add(rollBtn);

        diceLabel = new JLabel();
        diceLabel.setBounds(150,40,80,80);
        add(diceLabel);

        JButton back = new JButton("Abandonner");
        back.setBounds(10,130,120,30);
        back.addActionListener(e -> parent.showMenu());
        add(back);
    }

    private void initEasyBoardCoords() {
            easyBoardCoords[0] = new Point(382, 588);
            easyBoardCoords[1] = new Point(400, 534);
            easyBoardCoords[2] = new Point(400, 504);
            easyBoardCoords[3] = new Point(399, 468);
            easyBoardCoords[4] = new Point(388, 438);
            easyBoardCoords[5] = new Point(373, 406);
            easyBoardCoords[6] = new Point(364, 373);
            easyBoardCoords[7] = new Point(378, 343);
            easyBoardCoords[8] = new Point(418, 328);
            easyBoardCoords[9] = new Point(459, 334);
            easyBoardCoords[10] = new Point(484, 363);
            easyBoardCoords[11] = new Point(480, 397);
            easyBoardCoords[12] = new Point(469, 427);
            easyBoardCoords[13] = new Point(460, 460);
            easyBoardCoords[14] = new Point(460, 496);
            easyBoardCoords[15] = new Point(457, 531);
            easyBoardCoords[16] = new Point(460, 562);
            easyBoardCoords[17] = new Point(480, 597);
            easyBoardCoords[18] = new Point(511, 609);
            easyBoardCoords[19] = new Point(546, 607);
            easyBoardCoords[20] = new Point(576, 589);
            easyBoardCoords[21] = new Point(580, 556);
            easyBoardCoords[22] = new Point(562, 525);
            easyBoardCoords[23] = new Point(547, 492);
            easyBoardCoords[24] = new Point(537, 457);
            easyBoardCoords[25] = new Point(525, 429);
            easyBoardCoords[26] = new Point(519, 396);
            easyBoardCoords[27] = new Point(514, 364);
            easyBoardCoords[28] = new Point(538, 334);
            easyBoardCoords[29] = new Point(588, 324);
            easyBoardCoords[30] = new Point(633, 336);
            easyBoardCoords[31] = new Point(654, 373);
            easyBoardCoords[32] = new Point(646, 412);
            easyBoardCoords[33] = new Point(634, 447);
            easyBoardCoords[34] = new Point(625, 480);
            easyBoardCoords[35] = new Point(621, 514);
            easyBoardCoords[36] = new Point(618, 549);
            easyBoardCoords[37] = new Point(624, 586);
            easyBoardCoords[38] = new Point(654, 607);
            easyBoardCoords[39] = new Point(687, 610);
            easyBoardCoords[40] = new Point(723, 603);
            easyBoardCoords[41] = new Point(748, 585);
            easyBoardCoords[42] = new Point(753, 552);
            easyBoardCoords[43] = new Point(738, 529);
            easyBoardCoords[44] = new Point(718, 498);
            easyBoardCoords[45] = new Point(711, 472);
            easyBoardCoords[46] = new Point(709, 442);
            easyBoardCoords[47] = new Point(712, 411);
            easyBoardCoords[48] = new Point(720, 361);
        }

    void startNewGame(GameConfig c) {
        this.cfg = c;

        try {
            String path = c.level.equals("FACILE") ? "assets/pj1.jpg" : "assets/pj2.jpg";
            boardImage = ImageIO.read(new File(path));
            boardSizeX = boardImage.getWidth();
            boardSizeY = boardImage.getHeight();
        } catch(Exception ex) {
            boardImage = new BufferedImage(800,800,BufferedImage.TYPE_INT_RGB);
            Graphics2D g = boardImage.createGraphics();
            g.setColor(Color.LIGHT_GRAY); g.fillRect(0,0,800,800);
            g.setColor(Color.BLACK); g.drawString("Image de plateau non trouvée dans ./assets/",10,20);
            g.dispose();
        }

        if(cfg.level.equals("FACILE")) {
            initEasyBoardCoords();
        }

        specialMoves.clear();
        if (cfg.level.equals("FACILE")) {
            finalSquare = 47;
            specialMoves.put(4, 7);
            specialMoves.put(9, 29);
            specialMoves.put(22, 17);
            specialMoves.put(43, 33);
        } else {
            finalSquare = 100;
            specialMoves.put(9, 27);
            specialMoves.put(18, 37);
            specialMoves.put(25, 54);
            specialMoves.put(28, 51);
            specialMoves.put(56, 64);
            specialMoves.put(68, 88);
            specialMoves.put(76, 97);
            specialMoves.put(79, 100);
            specialMoves.put(16, 7);
            specialMoves.put(59, 17);
            specialMoves.put(63, 19);
            specialMoves.put(67, 30);
            specialMoves.put(87, 24);
            specialMoves.put(93, 69);
            specialMoves.put(95, 75);
            specialMoves.put(99, 77);
        }

        players = new ArrayList<>();
        int n = cfg.nPlayers;
        for (int i=0;i<n;i++)
            players.add(new Player("J"+(i+1), pawnColors[i], true));

        if (cfg.includeAI)
            players.add(new Player("AI", pawnColors[Math.min(n,3)], false));

        currentPlayer = 0;
        statusLabel.setText("Tour de " + players.get(currentPlayer).name);

        repaint();
    }

    void doRoll(){
        Player p = players.get(currentPlayer);
        int die = rand.nextInt(6)+1;
        showDiceFace(die);

        int from = p.position;
        targetPosition = from + die;

        if (targetPosition > finalSquare)
            targetPosition = finalSquare - (targetPosition - finalSquare);

        int pointsGained = 0;

        if (targetPosition > from) {
            if (specialMoves.containsKey(targetPosition)) {
                int dest = specialMoves.get(targetPosition);
                if (dest > targetPosition) {
                    pointsGained = (die + (dest - 9)) * 3;
                    targetPosition = dest;
                } else {
                    pointsGained = (targetPosition - from) * 3;
                    targetPosition = dest;
                }
            } else {
                pointsGained = (targetPosition - from) * 3;
            }
        } else {
            pointsGained = (targetPosition - from) * 3;
        }

        Optional<Player> occupant =
                players.stream().filter(pl -> pl!=p && pl.position==targetPosition).findFirst();

        if (occupant.isPresent()) {
            Player opp = occupant.get();

            opp.position = from;
            p.position = targetPosition;

            int lost = die * -3;
            opp.score += lost;
            int gain = die*3 + (-lost);
            p.score += gain;

            statusLabel.setText(
                    p.name + " a pris la place de " + opp.name +
                    ". " + p.name + " gagne " + gain + " pts."
            );

        } else {
            p.position = targetPosition;
            p.score += pointsGained;

            statusLabel.setText(
                p.name + " a lancé " + die +
                " et va en case " + targetPosition + " (+" + pointsGained + " pts)."
            );
        }

        if (specialMoves.containsKey(p.position)) {
            int dest = specialMoves.get(p.position);
            if (dest != p.position) {
                statusLabel.setText(statusLabel.getText() + " -> effet case! va en " + dest);
                p.position = dest;
            }
        }

        if (p.position == finalSquare) {
            int finalScore = p.score;
            JOptionPane.showMessageDialog(this,
                    p.name + " a gagné! Score = " + finalScore);

            if (scoreManager.isTopScore(finalScore)) {
                String initials = JOptionPane.showInputDialog(this,
                    "Entrer vos initiales (3 lettres)", "WINNER",
                    JOptionPane.PLAIN_MESSAGE);

                if (initials != null) {
                    initials = initials.trim().toUpperCase();
                    if (initials.length() > 3)
                        initials = initials.substring(0, 3);
                    scoreManager.addScore(initials, finalScore);
                }
            }

            scoreManager.saveScores();
            parent.showStats();
            return;
        }

        currentPlayer = (currentPlayer + 1) % players.size();
        statusLabel.setText(
            statusLabel.getText() +
            "  |  Tour de " + players.get(currentPlayer).name
        );

        repaint();
    }

    void showDiceFace(int n){
        try {
            BufferedImage d = ImageIO.read(new File("assets/dice_" + n + ".png"));
            ImageIcon ic = new ImageIcon(d.getScaledInstance(60,60,Image.SCALE_SMOOTH));
            diceLabel.setIcon(ic);
            diceLabel.setText("");
        } catch(Exception ex) {
            diceLabel.setIcon(null);
            diceLabel.setText("Dé: " + n);
        }
    }

    public void paintComponent(Graphics g0){
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;

        int w = getWidth();
        int h = getHeight();

        if (boardImage != null) {
            int bw = Math.min(w-200, boardImage.getWidth());
            int bh = Math.min(h-200, boardImage.getHeight());

            g.drawImage(boardImage, 200, 10, bw, bh, null);

            for (Player p : players) {
                Point pt;
                if (cfg.level.equals("FACILE")) {
                    pt = easyBoardCoords[p.position];
                } else {
                    pt = positionToPoint(p.position, 200, 10, bw, bh);
                }
                g.setColor(p.color);
                g.fillOval(pt.x-10, pt.y-10, 20, 20);
                g.setColor(Color.BLACK);
                g.drawString(p.name, pt.x-10, pt.y-14);
            }

        } else {
            g.setColor(Color.GRAY);
            g.fillRect(10,10,w-20,h-20);
        }

        g.setColor(Color.WHITE);
        g.fillRect(10,170,170,300);

        g.setColor(Color.BLACK);
        g.drawRect(10,170,170,300);
        g.drawString("Scores:", 20,190);

        int y = 210;
        for (Player p : players) {
            g.drawString(
                p.name + " : " + p.score +
                " (case " + p.position + ")", 20, y
            );
            y += 20;
        }
    }

    Point positionToPoint(int pos, int x0, int y0, int bw, int bh) {
        if (pos<=0) return new Point(x0 + 20, y0 + bh - 20);

        int cols = (cfg.level.equals("FACILE")) ? 8 : 10;
        int rows = (int)Math.ceil((double)finalSquare/cols);

        int cellW = Math.max(20, bw/cols);
        int cellH = Math.max(20, bh/rows);

        int idx = pos-1;
        int r = rows - 1 - (idx/cols);
        int c = idx % cols;

        if ((rows - r) % 2 == 0)
            c = cols - 1 - c;

        int cx = x0 + c*cellW + cellW/2;
        int cy = y0 + r*cellH + cellH/2;

        return new Point(cx, cy);
    }
}

/* ---------- Player ---------- */
class Player {
    String name;
    Color color;
    int position = 0;
    int score = 0;
    boolean human = true;

    Player(String name, Color col, boolean human) {
        this.name = name;
        this.color = col;
        this.human = human;
    }
}

/* ---------- ScoreManager: gère top 10 sauvegardés ---------- */
class ScoreManager {
    private final String file = "scores.dat";
    List<ScoreEntry> top = new ArrayList<>();

    ScoreManager(){ loadScores(); }

    @SuppressWarnings("unchecked")
    void loadScores(){
        File f = new File(file);
        if (!f.exists()) {
            top = new ArrayList<>();
            for (int i=0;i<10;i++)
                top.add(new ScoreEntry("---",0));
            return;
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
            top = (List<ScoreEntry>) in.readObject();
        } catch(Exception e) {
            top = new ArrayList<>();
            for (int i=0;i<10;i++)
                top.add(new ScoreEntry("---",0));
        }
    }

    void saveScores(){
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
            out.writeObject(top);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    boolean isTopScore(int s){
        loadScores();
        for (ScoreEntry e : top)
            if (s >= e.score) return true;
        return false;
    }

    void addScore(String initials, int s){
        loadScores();
        top.add(new ScoreEntry(initials, s));
        top.sort((a,b)->Integer.compare(b.score, a.score));

        if (top.size()>10)
            top = top.subList(0, 10);

        saveScores();
    }

    List<ScoreEntry> getTop(){
        loadScores();
        return top;
    }
}

class ScoreEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    String name;
    int score;

    ScoreEntry(String n, int s){
        name = n;
        score = s;
    }
}

/* ---------- StatsPanel ---------- */
class StatsPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    MainFrame parent;
    ScoreManager manager = new ScoreManager();
    JTextArea text;

    StatsPanel(MainFrame p){
        parent = p;
        setLayout(new BorderLayout());

        text = new JTextArea();
        text.setEditable(false);
        add(new JScrollPane(text), BorderLayout.CENTER);

        JPanel bottom = new JPanel();

        JButton reset = new JButton("Réinitialiser");
        reset.addActionListener(e -> {
            File f = new File("scores.dat");
            if (f.exists()) f.delete();
            manager.loadScores();
            loadScores();
        });
        bottom.add(reset);

        JButton back = new JButton("Retour");
        back.addActionListener(e -> parent.showMenu());
        bottom.add(back);

        add(bottom, BorderLayout.SOUTH);
    }

    void loadScores(){
        List<ScoreEntry> list = manager.getTop();
        StringBuilder sb = new StringBuilder();

        sb.append("Top 10:\n");
        int i = 1;
        for (ScoreEntry s : list) {
            sb.append(String.format("%2d: %3s  %5d\n", i, s.name, s.score));
            i++;
        }

        text.setText(sb.toString());
    }
}
