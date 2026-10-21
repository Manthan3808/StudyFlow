import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;

/**
 * StudyFlow - Student Task Manager + Focus Mode
 * Features: Task tracker, Pomodoro focus timer, Quiz, Badges, XP/Levels,
 * streaks and a virtual pet that reacts to what you do.
 * Plain Java + Swing: no Maven or extra libraries needed (Java 8+).
 */
public class StudyFlow extends JFrame {

    // ------------------------------------------------------------ theme
    static final Color BG = new Color(243, 246, 252);
    static final Color PRIMARY = new Color(88, 101, 242);
    static final Color GOOD = new Color(46, 160, 110);
    static final Color BAD = new Color(220, 70, 70);
    static final Font BASE = new Font("Segoe UI", Font.PLAIN, 14);
    static final Font BOLD = new Font("Segoe UI", Font.BOLD, 14);
    static final Font BIG = new Font("Segoe UI", Font.BOLD, 28);

    // ------------------------------------------------------------ model
    static class Task {
        String title, subject, priority;
        LocalDate due;
        boolean done;

        Task(String title, String subject, String priority, LocalDate due, boolean done) {
            this.title = title;
            this.subject = subject;
            this.priority = priority;
            this.due = due;
            this.done = done;
        }

        boolean overdue() {
            return !done && due.isBefore(LocalDate.now());
        }

        int xpValue() {
            return priority.equals("High") ? 30 : priority.equals("Medium") ? 20 : 10;
        }
    }

    static class Question {
        String text;
        String[] options;
        int answer;

        Question(String text, String[] options, int answer) {
            this.text = text;
            this.options = options;
            this.answer = answer;
        }
    }

    static class Badge {
        final String id, name, description;
        final Predicate<StudyFlow> rule;

        Badge(String id, String name, String description, Predicate<StudyFlow> rule) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.rule = rule;
        }
    }

    // ------------------------------------------------------------ state
    static final Path USERS_FILE = Paths.get(System.getProperty("user.home"), "studyflow_users.txt");

    final String username;
    final Path saveFile;

    final List<Task> tasks = new ArrayList<>();
    final List<Question> customQuestions = new ArrayList<>();
    final Set<String> unlocked = new LinkedHashSet<>();
    final List<Badge> badges = new ArrayList<>();

    String petName = "Buddy";
    int xp = 0, happiness = 80, tasksCompleted = 0, focusSessions = 0, quizzesTaken = 0, perfectQuizzes = 0, streak = 0;
    LocalDate lastActive = null;
    long lastActionTime = System.currentTimeMillis();
    long celebrateUntil = 0;
    boolean focusRunning = false;

    // ------------------------------------------------------------ UI refs
    PetPanel pet;
    JLabel lvlVal = new JLabel(), tasksVal = new JLabel(), focusVal = new JLabel(), streakVal = new JLabel();
    JProgressBar xpBar = new JProgressBar(0, 100);
    JTextArea upcomingArea = new JTextArea();
    JLabel tipLabel = new JLabel();
    JLabel headerInfo = new JLabel();
    DefaultTableModel tableModel;
    JTable table;
    JPanel badgeGrid = new JPanel(new GridLayout(0, 3, 14, 14));
    JTextField titleField;
    JComboBox<String> subjectBox, priorityBox;
    JSpinner dateSpinner;

    static final String[] TIPS = {
            "Tip: Study in short bursts, then take a 5 minute break.",
            "Tip: Test yourself instead of re-reading. Try the quiz!",
            "Tip: Start with the hardest task while your brain is fresh.",
            "Tip: Put your phone in another room during focus mode.",
            "Tip: Sleep is part of studying. Memory is stored while you rest.",
            "Tip: Break big tasks into small ones. Small wins build streaks."
    };

    // ============================================================ main
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            showLogin();
        });
    }

    /** Shows the login window; opens the app for that user, or exits if the window is closed. */
    static void showLogin() {
        String user = LoginDialog.ask();
        if (user == null) {
            System.exit(0);
        }
        new StudyFlow(user).setVisible(true);
    }

    javax.swing.Timer idleTimer;

    public StudyFlow(String username) {
        super("StudyFlow - Student Task Manager & Focus Mode  (" + username + ")");
        this.username = username;
        this.saveFile = Paths.get(System.getProperty("user.home"), "studyflow_" + username.toLowerCase() + ".txt");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                save();
                dispose();
                System.exit(0);
            }
        });
        initBadges();
        boolean loaded = load();
        if (!loaded) {
            tasks.add(new Task("Try completing this task!", "Welcome", "Low", LocalDate.now(), false));
        }
        applyAwayDecay();
        buildUI();
        refreshAll();
        idleTimer = new javax.swing.Timer(60000, e -> idleTick());
        idleTimer.start();
        setSize(1000, 700);
        setMinimumSize(new Dimension(900, 640));
        setLocationRelativeTo(null);
    }

    // ============================================================ game logic
    int level() {
        return xp / 100 + 1;
    }

    int effectiveStreak() {
        if (lastActive == null) return 0;
        long gap = ChronoUnit.DAYS.between(lastActive, LocalDate.now());
        return gap > 1 ? 0 : streak;
    }

    int overdueCount() {
        int n = 0;
        for (Task t : tasks) if (t.overdue()) n++;
        return n;
    }

    void changeHappiness(int delta) {
        happiness = Math.max(0, Math.min(100, happiness + delta));
    }

    /** Call whenever the student does something useful: updates streak + resets idle timer. */
    void markActive() {
        lastActionTime = System.currentTimeMillis();
        LocalDate today = LocalDate.now();
        if (lastActive == null) {
            streak = 1;
        } else {
            long gap = ChronoUnit.DAYS.between(lastActive, today);
            if (gap == 1) streak++;
            else if (gap > 1) streak = 1;
        }
        lastActive = today;
    }

    void celebrate() {
        celebrateUntil = System.currentTimeMillis() + 6000;
    }

    void say(String msg) {
        if (pet != null) pet.say(msg);
    }

    /** The pet gets sadder the longer you stay away. */
    void applyAwayDecay() {
        if (lastActive == null) return;
        long days = ChronoUnit.DAYS.between(lastActive, LocalDate.now());
        if (days > 0) {
            changeHappiness((int) -Math.min(80, days * 15));
        }
    }

    /** Runs every minute: pet feels ignored if you do nothing or have overdue tasks. */
    void idleTick() {
        if (focusRunning) return;
        long idleMin = (System.currentTimeMillis() - lastActionTime) / 60000;
        int drop = 0;
        if (idleMin >= 2) drop += 2;
        if (overdueCount() > 0) drop += 1;
        if (drop > 0) {
            changeHappiness(-drop);
            if (happiness < 35) say("I feel ignored... please study with me!");
            save();
            refreshAll();
        }
    }

    void addXp(int amount) {
        int before = level();
        xp += amount;
        if (level() > before) {
            say("LEVEL UP! You are now level " + level() + "!");
            celebrate();
        }
    }

    void completeTask(Task t) {
        if (t.done) return;
        boolean wasLate = t.overdue();
        t.done = true;
        tasksCompleted++;
        addXp(t.xpValue());
        changeHappiness(wasLate ? 8 : 15);
        markActive();
        celebrate();
        say(wasLate ? "Late, but you did it! Proud of you!" : "Task done! Yay, great job!");
        afterChange();
    }

    void afterChange() {
        List<Badge> fresh = checkBadges();
        save();
        refreshAll();
        for (Badge b : fresh) {
            say("New badge: " + b.name + "!");
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "Badge unlocked: " + b.name + "\n" + b.description,
                    "Badge Unlocked!", JOptionPane.INFORMATION_MESSAGE));
        }
    }

    List<Badge> checkBadges() {
        List<Badge> fresh = new ArrayList<>();
        for (Badge b : badges) {
            if (!unlocked.contains(b.id) && b.rule.test(this)) {
                unlocked.add(b.id);
                fresh.add(b);
            }
        }
        return fresh;
    }

    void initBadges() {
        badges.add(new Badge("first", "First Step", "Complete your first task", s -> s.tasksCompleted >= 1));
        badges.add(new Badge("five", "Task Warrior", "Complete 5 tasks", s -> s.tasksCompleted >= 5));
        badges.add(new Badge("twentyfive", "Unstoppable", "Complete 25 tasks", s -> s.tasksCompleted >= 25));
        badges.add(new Badge("focus1", "Focus Starter", "Finish 1 focus session", s -> s.focusSessions >= 1));
        badges.add(new Badge("focus5", "Deep Focus", "Finish 5 focus sessions", s -> s.focusSessions >= 5));
        badges.add(new Badge("quiz1", "Quiz Rookie", "Finish your first quiz", s -> s.quizzesTaken >= 1));
        badges.add(new Badge("quizperfect", "Quiz Master", "Get a perfect quiz score", s -> s.perfectQuizzes >= 1));
        badges.add(new Badge("level3", "Rising Star", "Reach level 3", s -> s.level() >= 3));
        badges.add(new Badge("streak3", "3-Day Streak", "Study 3 days in a row", s -> s.effectiveStreak() >= 3));
        badges.add(new Badge("bestfriend", "Best Friends", "Get your pet to 100 happiness", s -> s.happiness >= 100));
    }

    // ============================================================ persistence
    String clean(String s) {
        return s.replace("|", "/").replace("\n", " ").replace("\r", " ").trim();
    }

    void save() {
        List<String> lines = new ArrayList<>();
        lines.add("petName=" + clean(petName));
        lines.add("xp=" + xp);
        lines.add("happiness=" + happiness);
        lines.add("tasksCompleted=" + tasksCompleted);
        lines.add("focusSessions=" + focusSessions);
        lines.add("quizzesTaken=" + quizzesTaken);
        lines.add("perfectQuizzes=" + perfectQuizzes);
        lines.add("streak=" + streak);
        lines.add("lastActive=" + (lastActive == null ? "none" : lastActive.toString()));
        lines.add("badges=" + String.join(",", unlocked));
        for (Task t : tasks) {
            lines.add("task=" + clean(t.title) + "|" + clean(t.subject) + "|" + t.priority + "|" + t.due + "|" + t.done);
        }
        for (Question q : customQuestions) {
            lines.add("question=" + clean(q.text) + "|" + clean(q.options[0]) + "|" + clean(q.options[1]) + "|"
                    + clean(q.options[2]) + "|" + clean(q.options[3]) + "|" + q.answer);
        }
        try {
            Files.write(saveFile, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not save: " + e.getMessage());
        }
    }

    boolean load() {
        if (!Files.exists(saveFile)) return false;
        try {
            for (String line : Files.readAllLines(saveFile, StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String key = line.substring(0, eq), val = line.substring(eq + 1);
                try {
                    switch (key) {
                        case "petName": petName = val.isEmpty() ? "Buddy" : val; break;
                        case "xp": xp = Integer.parseInt(val); break;
                        case "happiness": happiness = Integer.parseInt(val); break;
                        case "tasksCompleted": tasksCompleted = Integer.parseInt(val); break;
                        case "focusSessions": focusSessions = Integer.parseInt(val); break;
                        case "quizzesTaken": quizzesTaken = Integer.parseInt(val); break;
                        case "perfectQuizzes": perfectQuizzes = Integer.parseInt(val); break;
                        case "streak": streak = Integer.parseInt(val); break;
                        case "lastActive": lastActive = val.equals("none") ? null : LocalDate.parse(val); break;
                        case "badges":
                            for (String id : val.split(",")) if (!id.isEmpty()) unlocked.add(id);
                            break;
                        case "task": {
                            String[] p = val.split("\\|", -1);
                            if (p.length == 5)
                                tasks.add(new Task(p[0], p[1], p[2], LocalDate.parse(p[3]), Boolean.parseBoolean(p[4])));
                            break;
                        }
                        case "question": {
                            String[] p = val.split("\\|", -1);
                            if (p.length == 6)
                                customQuestions.add(new Question(p[0], new String[]{p[1], p[2], p[3], p[4]}, Integer.parseInt(p[5])));
                            break;
                        }
                        default: break;
                    }
                } catch (RuntimeException bad) {
                    // skip any corrupted line
                }
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    // ============================================================ UI
    void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.add(buildHeader(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 15));
        tabs.addTab("Dashboard", buildDashboard());
        tabs.addTab("Tasks", buildTasksTab());
        tabs.addTab("Focus Mode", buildFocusTab());
        tabs.addTab("Quiz", new QuizPanel());
        tabs.addTab("Badges", buildBadgesTab());
        root.add(tabs, BorderLayout.CENTER);
        setContentPane(root);
    }

    JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, new Color(88, 101, 242), getWidth(), 0, new Color(150, 90, 230)));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        header.setBorder(new EmptyBorder(12, 18, 12, 18));
        JLabel title = new JLabel("StudyFlow");
        title.setFont(BIG);
        title.setForeground(Color.WHITE);
        JLabel sub = new JLabel("Plan it. Focus. Level up with your study buddy.");
        sub.setFont(BASE);
        sub.setForeground(new Color(235, 235, 255));
        JPanel left = new JPanel(new GridLayout(2, 1));
        left.setOpaque(false);
        left.add(title);
        left.add(sub);
        headerInfo.setFont(new Font("Segoe UI", Font.BOLD, 16));
        headerInfo.setForeground(Color.WHITE);
        header.add(left, BorderLayout.WEST);
        JPanel rightBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        rightBox.setOpaque(false);
        JButton logout = new JButton("Logout");
        logout.addActionListener(e -> logout());
        rightBox.add(headerInfo);
        rightBox.add(logout);
        header.add(rightBox, BorderLayout.EAST);
        return header;
    }

    JPanel card() {
        JPanel p = new JPanel();
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(new LineBorder(new Color(225, 228, 240), 1, true),
                new EmptyBorder(10, 12, 10, 12)));
        return p;
    }

    JPanel statCard(String title, JLabel value, Color color) {
        JPanel p = card();
        p.setLayout(new GridLayout(2, 1));
        JLabel t = new JLabel(title);
        t.setFont(BASE);
        t.setForeground(Color.GRAY);
        value.setFont(BIG);
        value.setForeground(color);
        p.add(t);
        p.add(value);
        return p;
    }

    // ---------------------------------------------------------- dashboard
    JPanel buildDashboard() {
        JPanel p = new JPanel(new BorderLayout(14, 14));
        p.setBorder(new EmptyBorder(14, 14, 14, 14));
        p.setBackground(BG);

        pet = new PetPanel();
        JPanel petCard = card();
        petCard.setLayout(new BorderLayout());
        petCard.add(pet, BorderLayout.CENTER);
        JButton rename = new JButton("Rename pet");
        rename.addActionListener(e -> {
            String n = JOptionPane.showInputDialog(this, "Name your study buddy:", petName);
            if (n != null && !n.trim().isEmpty()) {
                petName = clean(n);
                say("I love my new name!");
                save();
                refreshAll();
            }
        });
        petCard.add(rename, BorderLayout.SOUTH);
        petCard.setPreferredSize(new Dimension(330, 100));
        p.add(petCard, BorderLayout.WEST);

        JPanel right = new JPanel(new BorderLayout(0, 14));
        right.setOpaque(false);

        JPanel stats = new JPanel(new GridLayout(1, 4, 12, 0));
        stats.setOpaque(false);
        stats.add(statCard("Level", lvlVal, PRIMARY));
        stats.add(statCard("Tasks done", tasksVal, GOOD));
        stats.add(statCard("Focus sessions", focusVal, new Color(230, 140, 30)));
        stats.add(statCard("Day streak", streakVal, BAD));

        JPanel xpCard = card();
        xpCard.setLayout(new BorderLayout(0, 6));
        JLabel xpTitle = new JLabel("Progress to next level");
        xpTitle.setFont(BOLD);
        xpBar.setStringPainted(true);
        xpCard.add(xpTitle, BorderLayout.NORTH);
        xpCard.add(xpBar, BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout(0, 14));
        top.setOpaque(false);
        top.add(stats, BorderLayout.NORTH);
        top.add(xpCard, BorderLayout.SOUTH);
        right.add(top, BorderLayout.NORTH);

        JPanel upCard = card();
        upCard.setLayout(new BorderLayout(0, 6));
        JLabel upTitle = new JLabel("Up next");
        upTitle.setFont(BOLD);
        upcomingArea.setEditable(false);
        upcomingArea.setFont(BASE);
        upcomingArea.setOpaque(false);
        upCard.add(upTitle, BorderLayout.NORTH);
        upCard.add(upcomingArea, BorderLayout.CENTER);
        tipLabel.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        tipLabel.setForeground(new Color(90, 90, 120));
        upCard.add(tipLabel, BorderLayout.SOUTH);
        right.add(upCard, BorderLayout.CENTER);

        p.add(right, BorderLayout.CENTER);
        return p;
    }

    void refreshDashboard() {
        lvlVal.setText(String.valueOf(level()));
        tasksVal.setText(String.valueOf(tasksCompleted));
        focusVal.setText(String.valueOf(focusSessions));
        streakVal.setText(String.valueOf(effectiveStreak()));
        xpBar.setValue(xp % 100);
        xpBar.setString((xp % 100) + " / 100 XP   (total " + xp + ")");
        headerInfo.setText("Hi, " + username + "  |  Level " + level() + "  |  " + xp + " XP");

        List<Task> pending = new ArrayList<>();
        for (Task t : tasks) if (!t.done) pending.add(t);
        pending.sort(Comparator.comparing((Task t) -> t.due));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(5, pending.size()); i++) {
            Task t = pending.get(i);
            sb.append("  - ").append(t.title).append("  [").append(t.subject).append("]  due ").append(t.due);
            if (t.overdue()) sb.append("  OVERDUE");
            sb.append("\n");
        }
        if (pending.isEmpty()) sb.append("  No pending tasks. Add one in the Tasks tab!");
        upcomingArea.setText(sb.toString());
        tipLabel.setText(TIPS[new Random().nextInt(TIPS.length)]);
    }

    // ---------------------------------------------------------- tasks tab
    JPanel buildTasksTab() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBorder(new EmptyBorder(14, 14, 14, 14));
        p.setBackground(BG);

        JPanel form = card();
        form.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 4));
        titleField = new JTextField(18);
        subjectBox = new JComboBox<>(new String[]{"Math", "Science", "English", "Programming", "History", "Other"});
        subjectBox.setEditable(true);
        priorityBox = new JComboBox<>(new String[]{"High", "Medium", "Low"});
        priorityBox.setSelectedIndex(1);
        dateSpinner = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH));
        dateSpinner.setEditor(new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd"));
        JButton add = new JButton("Add Task");
        add.addActionListener(e -> addTask());
        titleField.addActionListener(e -> addTask());
        form.add(new JLabel("Task:"));
        form.add(titleField);
        form.add(new JLabel("Subject:"));
        form.add(subjectBox);
        form.add(new JLabel("Priority:"));
        form.add(priorityBox);
        form.add(new JLabel("Due:"));
        form.add(dateSpinner);
        form.add(add);
        p.add(form, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new String[]{"Status", "Task", "Subject", "Priority", "Due"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setRowHeight(28);
        table.setFont(BASE);
        table.getTableHeader().setFont(BOLD);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setMaxWidth(90);
        table.getColumnModel().getColumn(3).setMaxWidth(90);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tb, Object v, boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(tb, v, sel, foc, row, col);
                if (!sel && row < tasks.size()) {
                    Task t = tasks.get(row);
                    c.setForeground(t.done ? new Color(150, 150, 150) : t.overdue() ? BAD : Color.BLACK);
                }
                return c;
            }
        });
        p.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        buttons.setOpaque(false);
        JButton complete = new JButton("Mark Selected Complete");
        complete.addActionListener(e -> {
            int r = table.getSelectedRow();
            if (r < 0) {
                JOptionPane.showMessageDialog(this, "Select a task first.");
                return;
            }
            Task t = tasks.get(r);
            if (t.done) JOptionPane.showMessageDialog(this, "That task is already done!");
            else completeTask(t);
        });
        JButton delete = new JButton("Delete Selected");
        delete.addActionListener(e -> {
            int r = table.getSelectedRow();
            if (r < 0) {
                JOptionPane.showMessageDialog(this, "Select a task first.");
                return;
            }
            tasks.remove(r);
            save();
            refreshAll();
        });
        JButton clearDone = new JButton("Clear Completed");
        clearDone.addActionListener(e -> {
            tasks.removeIf(t -> t.done);
            save();
            refreshAll();
        });
        buttons.add(complete);
        buttons.add(delete);
        buttons.add(clearDone);
        p.add(buttons, BorderLayout.SOUTH);
        return p;
    }

    void addTask() {
        String title = clean(titleField.getText());
        if (title.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please type a task name.");
            return;
        }
        LocalDate due = ((Date) dateSpinner.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        String subject = clean(String.valueOf(subjectBox.getSelectedItem()));
        if (subject.isEmpty()) subject = "Other";
        tasks.add(new Task(title, subject, (String) priorityBox.getSelectedItem(), due, false));
        titleField.setText("");
        markActive();
        changeHappiness(2);
        say("New task! Let's get it done together!");
        save();
        refreshAll();
    }

    void refreshTable() {
        if (tableModel == null) return;
        tasks.sort(Comparator.comparing((Task t) -> t.done).thenComparing(t -> t.due));
        tableModel.setRowCount(0);
        LocalDate today = LocalDate.now();
        for (Task t : tasks) {
            String status = t.done ? "Done" : t.overdue() ? "Overdue" : t.due.equals(today) ? "Due today" : "Pending";
            tableModel.addRow(new Object[]{status, t.title, t.subject, t.priority, t.due.toString()});
        }
    }

    // ---------------------------------------------------------- focus tab
    int focusTotal = 25 * 60, focusRemaining = 25 * 60;
    boolean focusStarted = false;
    JLabel focusTime = new JLabel("25:00", SwingConstants.CENTER);
    JLabel focusMsg = new JLabel("Pick your minutes and press Start.", SwingConstants.CENTER);
    JProgressBar focusBar = new JProgressBar(0, 100);
    JSpinner minutesSpinner = new JSpinner(new SpinnerNumberModel(25, 1, 120, 1));
    JButton startBtn = new JButton("Start");
    javax.swing.Timer focusTimer;

    JPanel buildFocusTab() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(BG);
        JPanel box = card();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(new EmptyBorder(24, 40, 24, 40));

        JLabel head = new JLabel("Focus Mode");
        head.setFont(BIG);
        head.setAlignmentX(Component.CENTER_ALIGNMENT);
        focusTime.setFont(new Font("Segoe UI", Font.BOLD, 84));
        focusTime.setForeground(PRIMARY);
        focusTime.setAlignmentX(Component.CENTER_ALIGNMENT);
        focusMsg.setFont(BASE);
        focusMsg.setAlignmentX(Component.CENTER_ALIGNMENT);
        focusBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        focusBar.setMaximumSize(new Dimension(420, 18));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));
        row.setOpaque(false);
        row.add(new JLabel("Minutes:"));
        row.add(minutesSpinner);
        JButton reset = new JButton("Reset");
        row.add(startBtn);
        row.add(reset);

        minutesSpinner.addChangeListener(e -> {
            if (!focusStarted) {
                focusRemaining = focusTotal = (Integer) minutesSpinner.getValue() * 60;
                updateFocusLabel();
            }
        });
        startBtn.addActionListener(e -> toggleFocus());
        reset.addActionListener(e -> resetFocus());
        focusTimer = new javax.swing.Timer(1000, e -> focusTick());

        box.add(head);
        box.add(Box.createVerticalStrut(10));
        box.add(focusTime);
        box.add(focusBar);
        box.add(Box.createVerticalStrut(8));
        box.add(focusMsg);
        box.add(Box.createVerticalStrut(8));
        box.add(row);
        p.add(box);
        return p;
    }

    void toggleFocus() {
        if (!focusRunning) {
            if (!focusStarted) {
                focusTotal = focusRemaining = (Integer) minutesSpinner.getValue() * 60;
                focusStarted = true;
            }
            minutesSpinner.setEnabled(false);
            focusRunning = true;
            focusTimer.start();
            startBtn.setText("Pause");
            focusMsg.setText("Stay focused! " + petName + " is cheering for you.");
            say("You can do it! I'll be quiet.");
        } else {
            focusRunning = false;
            focusTimer.stop();
            startBtn.setText("Resume");
            focusMsg.setText("Paused.");
        }
    }

    void resetFocus() {
        boolean gaveUp = focusStarted && focusRemaining < focusTotal && focusRemaining > 0;
        focusTimer.stop();
        focusRunning = false;
        focusStarted = false;
        minutesSpinner.setEnabled(true);
        focusRemaining = focusTotal = (Integer) minutesSpinner.getValue() * 60;
        startBtn.setText("Start");
        focusMsg.setText("Pick your minutes and press Start.");
        updateFocusLabel();
        if (gaveUp) {
            changeHappiness(-5);
            say("You gave up... I'm a little sad.");
            refreshAll();
        }
    }

    void focusTick() {
        focusRemaining--;
        updateFocusLabel();
        if (focusRemaining <= 0) {
            focusTimer.stop();
            focusRunning = false;
            focusStarted = false;
            minutesSpinner.setEnabled(true);
            startBtn.setText("Start");
            Toolkit.getDefaultToolkit().beep();
            int minutes = focusTotal / 60;
            focusSessions++;
            int gained = Math.max(10, minutes);
            addXp(gained);
            changeHappiness(10);
            markActive();
            celebrate();
            say("Focus session complete! Amazing!");
            focusRemaining = focusTotal;
            updateFocusLabel();
            focusMsg.setText("Session complete! +" + gained + " XP. Take a short break.");
            afterChange();
        }
    }

    void updateFocusLabel() {
        focusTime.setText(String.format("%02d:%02d", focusRemaining / 60, focusRemaining % 60));
        int done = focusTotal == 0 ? 0 : (int) (100.0 * (focusTotal - focusRemaining) / focusTotal);
        focusBar.setValue(done);
    }

    // ---------------------------------------------------------- badges tab
    JPanel buildBadgesTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(14, 14, 14, 14));
        p.setBackground(BG);
        badgeGrid.setOpaque(false);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(badgeGrid, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(wrap);
        sp.setBorder(null);
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    void refreshBadges() {
        badgeGrid.removeAll();
        for (Badge b : badges) badgeGrid.add(new BadgeCard(b, unlocked.contains(b.id)));
        badgeGrid.revalidate();
        badgeGrid.repaint();
    }

    static class BadgeCard extends JPanel {
        final Badge badge;
        final boolean on;

        BadgeCard(Badge badge, boolean on) {
            this.badge = badge;
            this.on = on;
            setPreferredSize(new Dimension(200, 160));
            setBackground(Color.WHITE);
            setBorder(new LineBorder(new Color(225, 228, 240), 1, true));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int cx = getWidth() / 2;
            if (on) {
                g.setPaint(new GradientPaint(cx - 30, 15, new Color(255, 225, 110), cx + 30, 75, new Color(240, 160, 30)));
            } else {
                g.setColor(new Color(215, 218, 225));
            }
            g.fill(new Ellipse2D.Double(cx - 32, 16, 64, 64));
            g.setColor(on ? Color.WHITE : new Color(245, 245, 248));
            Path2D star = new Path2D.Double();
            for (int i = 0; i < 10; i++) {
                double r = i % 2 == 0 ? 22 : 9;
                double a = -Math.PI / 2 + i * Math.PI / 5;
                double x = cx + r * Math.cos(a), y = 48 + r * Math.sin(a);
                if (i == 0) star.moveTo(x, y);
                else star.lineTo(x, y);
            }
            star.closePath();
            g.fill(star);
            g.setColor(on ? Color.BLACK : Color.GRAY);
            g.setFont(BOLD);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(badge.name, cx - fm.stringWidth(badge.name) / 2, 105);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            g.setColor(Color.GRAY);
            fm = g.getFontMetrics();
            g.drawString(badge.description, Math.max(4, cx - fm.stringWidth(badge.description) / 2), 125);
            g.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g.setColor(on ? GOOD : new Color(170, 170, 180));
            String s = on ? "UNLOCKED" : "LOCKED";
            fm = g.getFontMetrics();
            g.drawString(s, cx - fm.stringWidth(s) / 2, 147);
            g.dispose();
        }
    }

    void logout() {
        save();
        idleTimer.stop();
        if (focusTimer != null) focusTimer.stop();
        focusRunning = false;
        if (pet != null) pet.stop();
        dispose();
        showLogin();
    }

    void refreshAll() {
        refreshDashboard();
        refreshTable();
        refreshBadges();
        if (pet != null) pet.repaint();
    }

    // ============================================================ virtual pet
    String mood() {
        if (System.currentTimeMillis() < celebrateUntil) return "CELEBRATE";
        if (happiness >= 65) return "HAPPY";
        if (happiness >= 35) return "OKAY";
        return "SAD";
    }

    class PetPanel extends JPanel {
        double t = 0;
        String bubbleText = "";
        long bubbleUntil = 0;

        final javax.swing.Timer anim;

        void stop() {
            anim.stop();
        }

        PetPanel() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(300, 360));
            anim = new javax.swing.Timer(40, e -> {
                t += 0.1;
                repaint();
            });
            anim.start();
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    String m = mood();
                    if (m.equals("SAD")) say("Thanks for the attention... study with me?");
                    else if (m.equals("OKAY")) say("Hehe! Let's finish a task!");
                    else say("That tickles! I'm happy!");
                }
            });
            setToolTipText("Click me!");
        }

        void say(String s) {
            bubbleText = s;
            bubbleUntil = System.currentTimeMillis() + 6000;
        }

        String bubble() {
            if (System.currentTimeMillis() < bubbleUntil) return bubbleText;
            switch (mood()) {
                case "CELEBRATE": return "Yay!! You did it!";
                case "HAPPY": return "I'm happy! Keep going!";
                case "OKAY": return "Shall we study a bit?";
                default: return "I feel ignored... :(";
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            String m = mood();
            boolean party = m.equals("CELEBRATE"), happy = party || m.equals("HAPPY"), sad = m.equals("SAD");
            double cx = w / 2.0;
            double lift = party ? Math.abs(Math.sin(t * 1.6)) * 45 : happy ? Math.abs(Math.sin(t * 0.8)) * 8 : 0;
            double cy = h / 2.0 + 5 - lift + (sad ? 10 : 0);

            // shadow
            g.setColor(new Color(0, 0, 0, 35));
            double sw = 120 - lift * 0.6;
            g.fill(new Ellipse2D.Double(cx - sw / 2, h / 2.0 + 90, sw, 16));

            Color body = happy ? new Color(255, 211, 92) : sad ? new Color(150, 165, 205) : new Color(130, 200, 236);
            Color dark = body.darker();
            Color light = new Color(Math.min(255, body.getRed() + 35), Math.min(255, body.getGreen() + 30), Math.min(255, body.getBlue() + 30));

            // ears
            g.setColor(dark);
            g.fill(new Ellipse2D.Double(cx - 62, cy - 86, 34, 34));
            g.fill(new Ellipse2D.Double(cx + 28, cy - 86, 34, 34));
            // arms
            double armY = party ? cy - 40 + Math.sin(t * 3) * 12 : sad ? cy + 28 : cy + 8;
            g.fill(new Ellipse2D.Double(cx - 98, armY, 28, 44));
            g.fill(new Ellipse2D.Double(cx + 70, armY, 28, 44));
            // feet
            g.fill(new Ellipse2D.Double(cx - 55, cy + 58, 44, 26));
            g.fill(new Ellipse2D.Double(cx + 11, cy + 58, 44, 26));
            // body
            g.setColor(body);
            g.fill(new Ellipse2D.Double(cx - 75, cy - 70, 150, 140));
            g.setColor(dark);
            g.setStroke(new BasicStroke(3));
            g.draw(new Ellipse2D.Double(cx - 75, cy - 70, 150, 140));
            g.setColor(light);
            g.fill(new Ellipse2D.Double(cx - 40, cy + 12, 80, 48));

            // cheeks
            if (happy) {
                g.setColor(new Color(255, 140, 140, 150));
                g.fill(new Ellipse2D.Double(cx - 60, cy + 2, 22, 14));
                g.fill(new Ellipse2D.Double(cx + 38, cy + 2, 22, 14));
            }

            // eyes
            double ey = cy - 18, lx = cx - 30, rx = cx + 30;
            g.setColor(new Color(40, 40, 60));
            g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (happy) {
                g.draw(new Arc2D.Double(lx - 13, ey - 8, 26, 20, 0, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Double(rx - 13, ey - 8, 26, 20, 0, 180, Arc2D.OPEN));
            } else {
                boolean blink = ((int) (t * 10)) % 70 < 2;
                double eh = blink ? 3 : 18;
                g.fill(new Ellipse2D.Double(lx - 8, ey - eh / 2, 16, eh));
                g.fill(new Ellipse2D.Double(rx - 8, ey - eh / 2, 16, eh));
                g.setColor(Color.WHITE);
                if (!blink) {
                    g.fill(new Ellipse2D.Double(lx - 3, ey - 6, 6, 6));
                    g.fill(new Ellipse2D.Double(rx - 3, ey - 6, 6, 6));
                }
                g.setColor(new Color(40, 40, 60));
                if (sad) {
                    g.draw(new Line2D.Double(lx - 14, ey - 18, lx + 10, ey - 28));
                    g.draw(new Line2D.Double(rx - 10, ey - 28, rx + 14, ey - 18));
                    // tears
                    double ty = (t * 25) % 36;
                    int alpha = (int) Math.max(0, 200 - ty * 5);
                    g.setColor(new Color(90, 170, 255, alpha));
                    g.fill(new Ellipse2D.Double(lx - 5, ey + 12 + ty, 9, 13));
                    g.fill(new Ellipse2D.Double(rx - 4, ey + 12 + ((ty + 14) % 36), 9, 13));
                }
            }

            // mouth
            double my = cy + 22;
            g.setColor(new Color(40, 40, 60));
            g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (party) {
                g.setColor(new Color(170, 50, 60));
                g.fill(new Arc2D.Double(cx - 24, my - 14, 48, 44, 180, 180, Arc2D.CHORD));
                g.setColor(new Color(255, 130, 140));
                g.fill(new Ellipse2D.Double(cx - 11, my + 12, 22, 12));
            } else if (happy) {
                g.draw(new Arc2D.Double(cx - 22, my - 14, 44, 30, 200, 140, Arc2D.OPEN));
            } else if (sad) {
                g.draw(new Arc2D.Double(cx - 18, my + 4, 36, 24, 20, 140, Arc2D.OPEN));
            } else {
                g.draw(new Line2D.Double(cx - 12, my + 6, cx + 12, my + 6));
            }

            // confetti
            if (party) {
                Color[] cols = {new Color(255, 90, 90), new Color(90, 160, 255), new Color(90, 210, 120),
                        new Color(255, 190, 40), new Color(200, 100, 255)};
                for (int i = 0; i < 14; i++) {
                    double a = i * 2 * Math.PI / 14 + t * 0.6;
                    double r = 105 + 12 * Math.sin(t * 2 + i);
                    double px = cx + Math.cos(a) * r, py = cy - 10 + Math.sin(a) * r * 0.8;
                    g.setColor(cols[i % cols.length]);
                    g.fill(new Ellipse2D.Double(px - 5, py - 5, 10, 10));
                }
            }

            // speech bubble
            String msg = bubble();
            g.setFont(BOLD);
            FontMetrics fm = g.getFontMetrics();
            int bw = Math.min(w - 16, fm.stringWidth(msg) + 28), bh = 34, bx = (int) (cx - bw / 2.0), by = 12;
            g.setColor(new Color(245, 246, 255));
            g.fill(new RoundRectangle2D.Double(bx, by, bw, bh, 18, 18));
            Path2D tail = new Path2D.Double();
            tail.moveTo(cx - 8, by + bh - 1);
            tail.lineTo(cx + 8, by + bh - 1);
            tail.lineTo(cx, by + bh + 10);
            tail.closePath();
            g.fill(tail);
            g.setColor(PRIMARY);
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new RoundRectangle2D.Double(bx, by, bw, bh, 18, 18));
            g.setColor(new Color(50, 50, 80));
            g.drawString(msg, (int) (cx - fm.stringWidth(msg) / 2.0), by + 22);

            // name + happiness bar
            String word = party ? "ecstatic" : happy ? "happy" : sad ? "sad" : "okay";
            String label = petName + " is feeling " + word;
            g.setFont(BOLD);
            fm = g.getFontMetrics();
            g.setColor(new Color(60, 60, 90));
            g.drawString(label, (int) (cx - fm.stringWidth(label) / 2.0), h - 40);
            int barX = 30, barW = w - 60, barY = h - 28;
            g.setColor(new Color(225, 228, 238));
            g.fill(new RoundRectangle2D.Double(barX, barY, barW, 14, 14, 14));
            g.setColor(happiness >= 65 ? GOOD : happiness >= 35 ? new Color(240, 170, 40) : BAD);
            g.fill(new RoundRectangle2D.Double(barX, barY, barW * happiness / 100.0, 14, 14, 14));
            g.dispose();
        }
    }


    // ============================================================ login
    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static String hashPassword(String salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest((salt + password).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /** username -> {salt, hash} */
    static Map<String, String[]> readUsers() {
        Map<String, String[]> users = new LinkedHashMap<>();
        if (!Files.exists(USERS_FILE)) return users;
        try {
            for (String line : Files.readAllLines(USERS_FILE, StandardCharsets.UTF_8)) {
                String[] p = line.split("\\|");
                if (p.length == 3) users.put(p[0], new String[]{p[1], p[2]});
            }
        } catch (IOException ignored) {
        }
        return users;
    }

    static void writeUsers(Map<String, String[]> users) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String[]> e : users.entrySet()) {
            lines.add(e.getKey() + "|" + e.getValue()[0] + "|" + e.getValue()[1]);
        }
        try {
            Files.write(USERS_FILE, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not save users: " + e.getMessage());
        }
    }

    static class LoginDialog extends JDialog {
        String result = null;
        final JTextField userField = new JTextField(16);
        final JPasswordField passField = new JPasswordField(16);
        final JLabel message = new JLabel(" ", SwingConstants.CENTER);

        static String ask() {
            LoginDialog d = new LoginDialog();
            d.setVisible(true); // modal: waits here until closed
            return d.result;
        }

        LoginDialog() {
            super((Frame) null, "StudyFlow - Login", true);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);

            JPanel banner = new JPanel(new GridLayout(2, 1)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setPaint(new GradientPaint(0, 0, new Color(88, 101, 242), getWidth(), 0, new Color(150, 90, 230)));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
            };
            banner.setBorder(new EmptyBorder(16, 20, 16, 20));
            JLabel title = new JLabel("StudyFlow", SwingConstants.CENTER);
            title.setFont(BIG);
            title.setForeground(Color.WHITE);
            JLabel sub = new JLabel("Log in or create an account to meet your study buddy", SwingConstants.CENTER);
            sub.setFont(BASE);
            sub.setForeground(new Color(235, 235, 255));
            banner.add(title);
            banner.add(sub);

            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(new EmptyBorder(20, 30, 10, 30));
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(6, 6, 6, 6);
            c.anchor = GridBagConstraints.WEST;
            c.gridx = 0; c.gridy = 0;
            form.add(new JLabel("Username:"), c);
            c.gridx = 1;
            form.add(userField, c);
            c.gridx = 0; c.gridy = 1;
            form.add(new JLabel("Password:"), c);
            c.gridx = 1;
            form.add(passField, c);

            JButton login = new JButton("Login");
            JButton register = new JButton("Register");
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
            buttons.add(login);
            buttons.add(register);
            c.gridx = 0; c.gridy = 2; c.gridwidth = 2; c.anchor = GridBagConstraints.CENTER;
            form.add(buttons, c);
            c.gridy = 3;
            message.setFont(BOLD);
            form.add(message, c);

            login.addActionListener(e -> doLogin());
            register.addActionListener(e -> doRegister());
            passField.addActionListener(e -> doLogin());
            getRootPane().setDefaultButton(login);

            JPanel root = new JPanel(new BorderLayout());
            root.add(banner, BorderLayout.NORTH);
            root.add(form, BorderLayout.CENTER);
            setContentPane(root);
            pack();
            setMinimumSize(new Dimension(440, 0));
            setResizable(false);
            setLocationRelativeTo(null);
        }

        void error(String msg) {
            message.setForeground(BAD);
            message.setText(msg);
        }

        void doLogin() {
            String u = userField.getText().trim().toLowerCase();
            String pw = new String(passField.getPassword());
            String[] rec = readUsers().get(u);
            if (rec == null || !rec[1].equals(hashPassword(rec[0], pw))) {
                error("Wrong username or password.");
                return;
            }
            result = u;
            dispose();
        }

        void doRegister() {
            String u = userField.getText().trim().toLowerCase();
            String pw = new String(passField.getPassword());
            if (!u.matches("[a-z0-9_]{3,20}")) {
                error("Username: 3-20 letters, numbers or _");
                return;
            }
            if (pw.length() < 4) {
                error("Password must be at least 4 characters.");
                return;
            }
            Map<String, String[]> users = readUsers();
            if (users.containsKey(u)) {
                error("That username is taken. Try logging in.");
                return;
            }
            byte[] saltBytes = new byte[16];
            new SecureRandom().nextBytes(saltBytes);
            String salt = hex(saltBytes);
            users.put(u, new String[]{salt, hashPassword(salt, pw)});
            writeUsers(users);
            result = u;
            dispose();
        }
    }

    // ============================================================ quiz
    static Question q(String text, String a, String b, String c, String d, int ans) {
        return new Question(text, new String[]{a, b, c, d}, ans);
    }

    static List<Question> builtInQuestions() {
        List<Question> l = new ArrayList<>();
        l.add(q("Which keyword is used to inherit a class in Java?", "extends", "implements", "inherits", "super", 0));
        l.add(q("How many bits does an int use in Java?", "8", "16", "32", "64", 2));
        l.add(q("Which collection does NOT allow duplicate elements?", "ArrayList", "HashSet", "LinkedList", "Vector", 1));
        l.add(q("A standard Pomodoro focus session lasts about...", "5 minutes", "25 minutes", "60 minutes", "90 minutes", 1));
        l.add(q("What is the entry point of a Java program?", "start()", "run()", "main()", "init()", 2));
        l.add(q("What does JVM stand for?", "Java Virtual Machine", "Java Variable Method", "Joint Visual Module", "Java Verified Memory", 0));
        l.add(q("Which of these is NOT a primitive type in Java?", "int", "boolean", "String", "double", 2));
        l.add(q("Time complexity of binary search?", "O(n)", "O(log n)", "O(n^2)", "O(1)", 1));
        l.add(q("Which data structure follows LIFO?", "Queue", "Stack", "Array", "Tree", 1));
        l.add(q("What does SQL's SELECT do?", "Deletes rows", "Retrieves data", "Creates a table", "Drops a database", 1));
        l.add(q("Active recall means...", "Re-reading notes", "Highlighting text", "Testing yourself from memory", "Listening to music", 2));
        l.add(q("Which loop always runs its body at least once?", "for", "while", "do-while", "for-each", 2));
        l.add(q("Which keyword stops a method from being overridden?", "static", "final", "private", "abstract", 1));
        l.add(q("What does HTML stand for?", "HyperText Markup Language", "High Text Machine Language", "Hyperlink Tool Mode Language", "Home Text Markup Logic", 0));
        l.add(q("Spaced repetition works best by...", "Cramming the night before", "Reviewing at increasing intervals", "Reading once", "Skipping sleep", 1));
        l.add(q("Which operator compares values for equality in Java?", "=", "==", "===", ":=", 1));
        l.add(q("Which class is commonly used to read console input?", "Scanner", "Writer", "Printer", "Display", 0));
        l.add(q("Which is a Java access modifier?", "public", "open", "global", "shared", 0));
        return l;
    }

    class QuizPanel extends JPanel {
        final CardLayout cl = new CardLayout();
        final JLabel qLabel = new JLabel(), progressLabel = new JLabel(), feedback = new JLabel(" "), resultLabel = new JLabel();
        final JRadioButton[] opts = new JRadioButton[4];
        final ButtonGroup group = new ButtonGroup();
        final JButton actionBtn = new JButton("Submit");
        List<Question> round = new ArrayList<>();
        int idx, score;
        boolean answered;

        QuizPanel() {
            setLayout(cl);
            setBackground(BG);
            add(startCard(), "start");
            add(questionCard(), "question");
            add(resultCard(), "result");
            cl.show(this, "start");
        }

        JPanel centered(JComponent inner) {
            JPanel p = new JPanel(new GridBagLayout());
            p.setBackground(BG);
            p.add(inner);
            return p;
        }

        JPanel startCard() {
            JPanel box = card();
            box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
            box.setBorder(new EmptyBorder(28, 50, 28, 50));
            JLabel h = new JLabel("Quick Quiz");
            h.setFont(BIG);
            h.setAlignmentX(CENTER_ALIGNMENT);
            JLabel d = new JLabel("5 random questions. Each correct answer = +5 XP.");
            d.setFont(BASE);
            d.setAlignmentX(CENTER_ALIGNMENT);
            JButton start = new JButton("Start Quiz");
            start.setAlignmentX(CENTER_ALIGNMENT);
            start.addActionListener(e -> startRound());
            JButton add = new JButton("Add My Own Question");
            add.setAlignmentX(CENTER_ALIGNMENT);
            add.addActionListener(e -> addCustomQuestion());
            box.add(h);
            box.add(Box.createVerticalStrut(10));
            box.add(d);
            box.add(Box.createVerticalStrut(18));
            box.add(start);
            box.add(Box.createVerticalStrut(8));
            box.add(add);
            return centered(box);
        }

        JPanel questionCard() {
            JPanel box = card();
            box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
            box.setBorder(new EmptyBorder(24, 36, 24, 36));
            progressLabel.setFont(BOLD);
            progressLabel.setForeground(PRIMARY);
            qLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
            box.add(progressLabel);
            box.add(Box.createVerticalStrut(12));
            box.add(qLabel);
            box.add(Box.createVerticalStrut(12));
            for (int i = 0; i < 4; i++) {
                opts[i] = new JRadioButton();
                opts[i].setFont(new Font("Segoe UI", Font.PLAIN, 16));
                opts[i].setOpaque(false);
                group.add(opts[i]);
                box.add(opts[i]);
            }
            box.add(Box.createVerticalStrut(10));
            feedback.setFont(BOLD);
            box.add(feedback);
            box.add(Box.createVerticalStrut(10));
            actionBtn.addActionListener(e -> onAction());
            box.add(actionBtn);
            return centered(box);
        }

        JPanel resultCard() {
            JPanel box = card();
            box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
            box.setBorder(new EmptyBorder(28, 50, 28, 50));
            resultLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
            resultLabel.setAlignmentX(CENTER_ALIGNMENT);
            JButton again = new JButton("Play Again");
            again.setAlignmentX(CENTER_ALIGNMENT);
            again.addActionListener(e -> startRound());
            JButton home = new JButton("Back");
            home.setAlignmentX(CENTER_ALIGNMENT);
            home.addActionListener(e -> cl.show(QuizPanel.this, "start"));
            box.add(resultLabel);
            box.add(Box.createVerticalStrut(18));
            box.add(again);
            box.add(Box.createVerticalStrut(8));
            box.add(home);
            return centered(box);
        }

        Question shuffled(Question src) {
            List<Integer> order = new ArrayList<>(Arrays.asList(0, 1, 2, 3));
            Collections.shuffle(order);
            String[] o = new String[4];
            int ans = 0;
            for (int i = 0; i < 4; i++) {
                o[i] = src.options[order.get(i)];
                if (order.get(i) == src.answer) ans = i;
            }
            return new Question(src.text, o, ans);
        }

        void startRound() {
            List<Question> pool = builtInQuestions();
            pool.addAll(customQuestions);
            Collections.shuffle(pool);
            round = new ArrayList<>();
            for (int i = 0; i < Math.min(5, pool.size()); i++) round.add(shuffled(pool.get(i)));
            idx = 0;
            score = 0;
            showQuestion();
            cl.show(this, "question");
        }

        void showQuestion() {
            Question qu = round.get(idx);
            progressLabel.setText("Question " + (idx + 1) + " of " + round.size() + "     Score: " + score);
            qLabel.setText("<html><body style='width:480px'>" + qu.text + "</body></html>");
            group.clearSelection();
            for (int i = 0; i < 4; i++) {
                opts[i].setText(qu.options[i]);
                opts[i].setEnabled(true);
            }
            feedback.setText(" ");
            actionBtn.setText("Submit");
            answered = false;
        }

        void onAction() {
            Question qu = round.get(idx);
            if (!answered) {
                int sel = -1;
                for (int i = 0; i < 4; i++) if (opts[i].isSelected()) sel = i;
                if (sel < 0) {
                    JOptionPane.showMessageDialog(StudyFlow.this, "Pick an answer first.");
                    return;
                }
                answered = true;
                for (JRadioButton r : opts) r.setEnabled(false);
                if (sel == qu.answer) {
                    score++;
                    feedback.setForeground(GOOD);
                    feedback.setText("Correct! +5 XP");
                } else {
                    feedback.setForeground(BAD);
                    feedback.setText("Not quite. Answer: " + qu.options[qu.answer]);
                }
                progressLabel.setText("Question " + (idx + 1) + " of " + round.size() + "     Score: " + score);
                actionBtn.setText(idx == round.size() - 1 ? "Finish" : "Next");
            } else {
                idx++;
                if (idx >= round.size()) finishRound();
                else showQuestion();
            }
        }

        void finishRound() {
            int total = round.size();
            addXp(score * 5);
            quizzesTaken++;
            if (score == total) perfectQuizzes++;
            markActive();
            if (score * 2 >= total) {
                changeHappiness(5 + score * 2);
                celebrate();
                say(score == total ? "PERFECT score! You're a genius!" : "Nice quiz! I'm proud!");
            } else {
                changeHappiness(2);
                say("Keep practicing, you'll get it!");
            }
            resultLabel.setText("You scored " + score + " / " + total + "  (+" + (score * 5) + " XP)");
            cl.show(this, "result");
            afterChange();
        }

        void addCustomQuestion() {
            JTextField qf = new JTextField(28);
            JTextField[] of = new JTextField[4];
            JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
            form.add(new JLabel("Question:"));
            form.add(qf);
            for (int i = 0; i < 4; i++) {
                of[i] = new JTextField(28);
                form.add(new JLabel("Option " + (char) ('A' + i) + ":"));
                form.add(of[i]);
            }
            JComboBox<String> correct = new JComboBox<>(new String[]{"A", "B", "C", "D"});
            form.add(new JLabel("Correct option:"));
            form.add(correct);
            int res = JOptionPane.showConfirmDialog(StudyFlow.this, form, "Add a quiz question",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (res != JOptionPane.OK_OPTION) return;
            String[] o = new String[4];
            for (int i = 0; i < 4; i++) {
                o[i] = clean(of[i].getText());
                if (o[i].isEmpty()) {
                    JOptionPane.showMessageDialog(StudyFlow.this, "Please fill all 4 options.");
                    return;
                }
            }
            if (clean(qf.getText()).isEmpty()) {
                JOptionPane.showMessageDialog(StudyFlow.this, "Please type the question.");
                return;
            }
            customQuestions.add(new Question(clean(qf.getText()), o, correct.getSelectedIndex()));
            save();
            JOptionPane.showMessageDialog(StudyFlow.this, "Question added! It can now appear in your quizzes.");
        }
    }
}
