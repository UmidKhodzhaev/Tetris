import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Тетрис");
            TetrisPanel panel = new TetrisPanel();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(panel);
            frame.setResizable(false);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            panel.requestFocusInWindow();
        });
    }
}

enum Tetromino {
    I(new int[][]{{0, 1}, {1, 1}, {2, 1}, {3, 1}}, 4, new Color(0, 240, 240)),
    O(new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}, 2, new Color(240, 240, 0)),
    T(new int[][]{{0, 1}, {1, 1}, {2, 1}, {1, 0}}, 3, new Color(160, 0, 240)),
    S(new int[][]{{1, 0}, {2, 0}, {0, 1}, {1, 1}}, 3, new Color(0, 240, 0)),
    Z(new int[][]{{0, 0}, {1, 0}, {1, 1}, {2, 1}}, 3, new Color(240, 0, 0)),
    J(new int[][]{{0, 0}, {0, 1}, {1, 1}, {2, 1}}, 3, new Color(0, 0, 240)),
    L(new int[][]{{2, 0}, {0, 1}, {1, 1}, {2, 1}}, 3, new Color(240, 160, 0));

    final int size;
    final Color color;
    final int[][][] rotations;

    Tetromino(int[][] base, int size, Color color) {
        this.size = size;
        this.color = color;
        this.rotations = new int[4][][];
        int[][] current = base;
        for (int r = 0; r < 4; r++) {
            rotations[r] = current;
            int[][] next = new int[4][2];
            for (int i = 0; i < 4; i++) {
                int x = current[i][0], y = current[i][1];
                next[i][0] = size - 1 - y;
                next[i][1] = x;
            }
            current = next;
        }
    }
}

class TetrisPanel extends JPanel implements ActionListener, KeyListener {
    private static final int COLS = 10;
    private static final int ROWS = 20;
    private static final int CELL = 30;
    private static final int SIDEBAR = 160;

    private final Color[][] grid = new Color[ROWS][COLS];
    private final Deque<Tetromino> bag = new ArrayDeque<>();
    private final Random random = new Random();

    private Timer timer;
    private int baseDelay = 500;

    private Tetromino current;
    private Tetromino next;
    private int rotation;
    private int pieceX, pieceY;

    private int score = 0;
    private int lines = 0;
    private int level = 1;

    private boolean gameOver = false;
    private boolean paused = false;

    TetrisPanel() {
        setPreferredSize(new Dimension(COLS * CELL + SIDEBAR, ROWS * CELL));
        setBackground(new Color(20, 20, 20));
        setFocusable(true);
        addKeyListener(this);

        next = nextFromBag();
        spawnPiece();

        timer = new Timer(baseDelay, this);
        timer.start();
    }

    private Tetromino nextFromBag() {
        if (bag.isEmpty()) {
            List<Tetromino> all = new ArrayList<>(Arrays.asList(Tetromino.values()));
            Collections.shuffle(all, random);
            bag.addAll(all);
        }
        return bag.poll();
    }

    private void spawnPiece() {
        current = next;
        next = nextFromBag();
        rotation = 0;
        pieceX = (COLS - current.size) / 2;
        pieceY = 0;
        if (collides(current, rotation, pieceX, pieceY)) {
            gameOver = true;
            timer.stop();
        }
    }

    private boolean collides(Tetromino t, int rot, int px, int py) {
        for (int[] cell : t.rotations[rot]) {
            int bx = px + cell[0];
            int by = py + cell[1];
            if (bx < 0 || bx >= COLS || by >= ROWS) return true;
            if (by >= 0 && grid[by][bx] != null) return true;
        }
        return false;
    }

    private boolean movePiece(int dx, int dy) {
        if (!collides(current, rotation, pieceX + dx, pieceY + dy)) {
            pieceX += dx;
            pieceY += dy;
            return true;
        }
        return false;
    }

    private void rotate() {
        int newRot = (rotation + 1) % 4;
        int[] kicks = {0, -1, 1, -2, 2};
        for (int k : kicks) {
            if (!collides(current, newRot, pieceX + k, pieceY)) {
                rotation = newRot;
                pieceX += k;
                return;
            }
        }
    }

    private void softDrop() {
        if (movePiece(0, 1)) {
            score += 1;
        } else {
            lockPiece();
        }
        repaint();
    }

    private void hardDrop() {
        while (movePiece(0, 1)) {
            score += 2;
        }
        lockPiece();
        repaint();
    }

    private void lockPiece() {
        for (int[] cell : current.rotations[rotation]) {
            int bx = pieceX + cell[0];
            int by = pieceY + cell[1];
            if (by >= 0) grid[by][bx] = current.color;
        }
        clearLines();
        spawnPiece();
    }

    private void clearLines() {
        int cleared = 0;
        for (int row = ROWS - 1; row >= 0; row--) {
            boolean full = true;
            for (int col = 0; col < COLS; col++) {
                if (grid[row][col] == null) {
                    full = false;
                    break;
                }
            }
            if (full) {
                cleared++;
                for (int r = row; r > 0; r--) {
                    grid[r] = grid[r - 1].clone();
                }
                grid[0] = new Color[COLS];
                row++;
            }
        }
        if (cleared > 0) {
            lines += cleared;
            score += switch (cleared) {
                case 1 -> 100;
                case 2 -> 300;
                case 3 -> 500;
                default -> 800;
            } * level;
            int newLevel = lines / 10 + 1;
            if (newLevel != level) {
                level = newLevel;
                baseDelay = Math.max(100, 500 - (level - 1) * 40);
                timer.setDelay(baseDelay);
            }
        }
    }

    private void restart() {
        for (Color[] row : grid) Arrays.fill(row, null);
        bag.clear();
        score = 0;
        lines = 0;
        level = 1;
        baseDelay = 500;
        gameOver = false;
        paused = false;
        next = nextFromBag();
        spawnPiece();
        timer.setDelay(baseDelay);
        timer.start();
        repaint();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gameOver || paused) return;
        if (!movePiece(0, 1)) {
            lockPiece();
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        g2.setColor(new Color(30, 30, 30));
        g2.fillRect(0, 0, COLS * CELL, ROWS * CELL);

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                if (grid[row][col] != null) {
                    drawCell(g2, col, row, grid[row][col]);
                }
                g2.setColor(new Color(50, 50, 50));
                g2.drawRect(col * CELL, row * CELL, CELL, CELL);
            }
        }

        if (!gameOver) {
            for (int[] cell : current.rotations[rotation]) {
                int bx = pieceX + cell[0];
                int by = pieceY + cell[1];
                if (by >= 0) drawCell(g2, bx, by, current.color);
            }
        }

        int sx = COLS * CELL + 15;
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.drawString("Счёт: " + score, sx, 30);
        g2.drawString("Линии: " + lines, sx, 55);
        g2.drawString("Уровень: " + level, sx, 80);
        g2.drawString("Следующая:", sx, 115);

        int previewCell = 20;
        int boxSize = next.size;
        int offsetX = sx + (3 * previewCell - boxSize * previewCell) / 2;
        int offsetY = 130;
        for (int[] cell : next.rotations[0]) {
            int px = offsetX + cell[0] * previewCell;
            int py = offsetY + cell[1] * previewCell;
            g2.setColor(next.color);
            g2.fillRect(px, py, previewCell, previewCell);
            g2.setColor(Color.BLACK);
            g2.drawRect(px, py, previewCell, previewCell);
        }

        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.setColor(Color.WHITE);
        g2.drawString("← → : движение", sx, 230);
        g2.drawString("↑ : поворот", sx, 248);
        g2.drawString("↓ : мягкий сброс", sx, 266);
        g2.drawString("Пробел : жёсткий сброс", sx, 284);
        g2.drawString("P : пауза", sx, 302);

        if (gameOver) {
            g2.setColor(new Color(0, 0, 0, 180));
            g2.fillRect(0, 0, COLS * CELL, ROWS * CELL);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 28));
            g2.drawString("ИГРА ОКОНЧЕНА", 30, ROWS * CELL / 2 - 10);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
            g2.drawString("Нажмите R для перезапуска", 40, ROWS * CELL / 2 + 20);
        } else if (paused) {
            g2.setColor(new Color(0, 0, 0, 150));
            g2.fillRect(0, 0, COLS * CELL, ROWS * CELL);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 28));
            g2.drawString("ПАУЗА", 70, ROWS * CELL / 2);
        }
    }

    private void drawCell(Graphics2D g2, int col, int row, Color color) {
        int x = col * CELL;
        int y = row * CELL;
        g2.setColor(color);
        g2.fillRect(x + 1, y + 1, CELL - 2, CELL - 2);
        g2.setColor(color.brighter());
        g2.drawRect(x + 1, y + 1, CELL - 2, CELL - 2);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (gameOver) {
            if (e.getKeyCode() == KeyEvent.VK_R) restart();
            return;
        }
        switch (e.getKeyCode()) {
            case KeyEvent.VK_P -> {
                paused = !paused;
                repaint();
            }
            case KeyEvent.VK_LEFT -> {
                if (!paused) {
                    movePiece(-1, 0);
                    repaint();
                }
            }
            case KeyEvent.VK_RIGHT -> {
                if (!paused) {
                    movePiece(1, 0);
                    repaint();
                }
            }
            case KeyEvent.VK_UP -> {
                if (!paused) {
                    rotate();
                    repaint();
                }
            }
            case KeyEvent.VK_DOWN -> {
                if (!paused) softDrop();
            }
            case KeyEvent.VK_SPACE -> {
                if (!paused) hardDrop();
            }
            default -> {
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }
}
