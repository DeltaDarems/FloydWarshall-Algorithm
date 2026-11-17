import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main window of the simulator. Shows one card per OS module and switches
 * between the home screen and the module panels using a CardLayout.
 *
 * Module panels are created lazily the first time they are opened and are
 * wrapped with a top bar that contains a "Back to Menu" button, so the
 * individual panels do not need to know anything about navigation.
 */
public class MainMenu extends JFrame {

    private static final String HOME_CARD = "HOME";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardContainer = new JPanel(cardLayout);
    private final Map<String, JPanel> loadedModules = new HashMap<String, JPanel>();
    private final List<ModuleInfo> modules = new ArrayList<ModuleInfo>();

    public MainMenu() {
        super("Operating System Simulator");
        registerModules();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 640));
        setSize(1180, 720);
        setLocationRelativeTo(null);

        cardContainer.setBackground(UIStyles.BACKGROUND);
        cardContainer.add(buildHomePanel(), HOME_CARD);
        setContentPane(cardContainer);
        showHome();
    }

    // ------------------------------------------------------------------
    // Module registry
    // ------------------------------------------------------------------

    private void registerModules() {
        modules.add(new ModuleInfo("CPU_SCHEDULING", 1, "CPU Scheduling",
                "FCFS, SJF, Priority and Round Robin with Gantt chart and average times.", 2));
        modules.add(new ModuleInfo("BANKERS", 2, "Banker's Algorithm",
                "Need matrix, safety algorithm and safe sequence detection.", 3));
        modules.add(new ModuleInfo("PAGING", 3, "Paging",
                "Page table, page-to-frame mapping and address translation.", 4));
        modules.add(new ModuleInfo("PAGE_REPLACEMENT", 4, "Page Replacement",
                "FIFO, LRU and Optimal with hits, faults and ratios.", 5));
        modules.add(new ModuleInfo("SEGMENTATION", 5, "Segmentation",
                "Segment table, base/limit and bounds checking.", 6));
        modules.add(new ModuleInfo("FRAGMENTATION", 6, "Fragmentation",
                "First, Best and Worst Fit with internal/external fragmentation.", 7));
        modules.add(new ModuleInfo("DISK_SCHEDULING", 7, "Disk Scheduling",
                "FCFS, SSTF, SCAN, C-SCAN, LOOK and C-LOOK head movement.", 8));
        modules.add(new ModuleInfo("MULTIPROGRAMMING", 8, "Multiprogramming",
                "Program loading, memory usage, utilization and queues.", 9));
    }

    /**
     * Creates the panel for a module. Parts 2-9 each add one case here that
     * returns the real panel; modules that are not built yet show an info page.
     */
    private JPanel createModulePanel(ModuleInfo info) {
        switch (info.key) {
            case "CPU_SCHEDULING":
                return new CPUSchedulingPanel();
            case "BANKERS":
                return new BankersAlgorithmPanel();
            case "PAGING":
                return new PagingPanel();
            case "PAGE_REPLACEMENT":
                return new PageReplacementPanel();
            case "SEGMENTATION":
                return new SegmentationPanel();
              
            case "FRAGMENTATION":
                return new FragmentationPanel();
             case "DISK_SCHEDULING":
                return new DiskSchedulingPanel();
            case "MULTIPROGRAMMING":
                return new MultiprogrammingPanel();
            default:
                return createNotBuiltYetPanel(info);
        }
    }

    // ------------------------------------------------------------------
    // Home screen
    // ------------------------------------------------------------------

    private JPanel buildHomePanel() {
        JPanel home = new JPanel(new BorderLayout());
        home.setBackground(UIStyles.BACKGROUND);

        // Header
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(UIStyles.PRIMARY_DARK);
        header.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));

        JLabel title = UIStyles.createTitleLabel("Operating System Simulator");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("Choose a module to run a real simulation");
        subtitle.setFont(UIStyles.BODY_FONT);
        subtitle.setForeground(new Color(0xCFDAF0));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box_vgap(6));
        header.add(subtitle);
        home.add(header, BorderLayout.NORTH);

        // Module cards (2 rows x 4 columns)
        JPanel grid = new JPanel(new GridLayout(2, 4, 20, 20));
        grid.setBackground(UIStyles.BACKGROUND);
        grid.setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));
        for (ModuleInfo info : modules) {
            grid.add(buildModuleCard(info));
        }
        home.add(grid, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 32, 12));
        footer.setBackground(UIStyles.BACKGROUND);
        JButton exitButton = UIStyles.createDangerButton("Exit");
        exitButton.addActionListener(e -> dispose());
        footer.add(exitButton);
        home.add(footer, BorderLayout.SOUTH);

        return home;
    }

    /** Small invisible spacer for BoxLayout. */
    private Component Box_vgap(int height) {
        return javax.swing.Box.createRigidArea(new Dimension(0, height));
    }

    private JPanel buildModuleCard(final ModuleInfo info) {
        final JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(UIStyles.PANEL_BG);
        setCardBorder(card, false);
        card.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

        JLabel number = new JLabel(String.valueOf(info.number), SwingConstants.CENTER);
        number.setFont(UIStyles.HEADING_FONT);
        number.setForeground(Color.WHITE);
        number.setOpaque(true);
        number.setBackground(UIStyles.PRIMARY);
        number.setPreferredSize(new Dimension(34, 34));

        JPanel numberWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        numberWrap.setOpaque(false);
        numberWrap.add(number);

        JLabel title = UIStyles.createHeadingLabel(info.title);

        JLabel description = new JLabel("<html><div style='width:170px'>" + info.description + "</div></html>");
        description.setFont(UIStyles.SMALL_FONT);
        description.setForeground(UIStyles.TEXT_MUTED);
        description.setVerticalAlignment(SwingConstants.TOP);

        JPanel text = new JPanel(new BorderLayout(0, 6));
        text.setOpaque(false);
        text.add(title, BorderLayout.NORTH);
        text.add(description, BorderLayout.CENTER);

        JLabel open = new JLabel("Open \u2192");
        open.setFont(UIStyles.BUTTON_FONT);
        open.setForeground(UIStyles.PRIMARY);

        card.add(numberWrap, BorderLayout.NORTH);
        card.add(text, BorderLayout.CENTER);
        card.add(open, BorderLayout.SOUTH);

        MouseAdapter listener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openModule(info);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                setCardBorder(card, true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // Ignore exits caused by moving onto a child label inside the same card
                java.awt.Point p = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), card);
                if (!card.contains(p)) {
                    setCardBorder(card, false);
                }
            }
        };
        attachListener(card, listener);
        return card;
    }

    private void attachListener(Component component, MouseAdapter listener) {
        component.addMouseListener(listener);
        if (component instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) component).getComponents()) {
                attachListener(child, listener);
            }
        }
    }

    private void setCardBorder(JPanel card, boolean highlighted) {
        Color line = highlighted ? UIStyles.PRIMARY : UIStyles.BORDER;
        int thickness = highlighted ? 2 : 1;
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(line, thickness),
                BorderFactory.createEmptyBorder(16 - thickness + 1, 16 - thickness + 1,
                        16 - thickness + 1, 16 - thickness + 1)));
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    private void openModule(ModuleInfo info) {
        JPanel wrapper = loadedModules.get(info.key);
        if (wrapper == null) {
            wrapper = wrapModule(info, createModulePanel(info));
            loadedModules.put(info.key, wrapper);
            cardContainer.add(wrapper, info.key);
        }
        cardLayout.show(cardContainer, info.key);
        setTitle("Operating System Simulator - " + info.title);
    }

    private void showHome() {
        cardLayout.show(cardContainer, HOME_CARD);
        setTitle("Operating System Simulator");
    }

    /** Adds a top bar with a Back button and the module title above a module panel. */
    private JPanel wrapModule(ModuleInfo info, JPanel content) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(UIStyles.BACKGROUND);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(UIStyles.PRIMARY_DARK);
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

        JButton back = UIStyles.createSecondaryButton("\u2190 Back to Menu");
        back.addActionListener(e -> showHome());

        JLabel title = new JLabel(info.number + ". " + info.title);
        title.setFont(UIStyles.HEADING_FONT);
        title.setForeground(Color.WHITE);
        title.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));

        topBar.add(back, BorderLayout.WEST);
        topBar.add(title, BorderLayout.CENTER);

        wrapper.add(topBar, BorderLayout.NORTH);
        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
    }

    /** Page shown for modules whose panel has not been built yet. */
    private JPanel createNotBuiltYetPanel(ModuleInfo info) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UIStyles.BACKGROUND);

        JPanel box = UIStyles.createTitledPanel(info.title);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(BorderFactory.createCompoundBorder(
                box.getBorder(), BorderFactory.createEmptyBorder(16, 32, 16, 32)));

        JLabel line1 = UIStyles.createLabel("This module has not been built yet.");
        JLabel line2 = UIStyles.createMutedLabel("It will be added in PART " + info.part + " of the project.");
        line1.setAlignmentX(Component.CENTER_ALIGNMENT);
        line2.setAlignmentX(Component.CENTER_ALIGNMENT);
        box.add(line1);
        box.add(javax.swing.Box.createRigidArea(new Dimension(0, 8)));
        box.add(line2);

        panel.add(box);
        return panel;
    }

    // ------------------------------------------------------------------
    // Module description holder
    // ------------------------------------------------------------------

    private static class ModuleInfo {
        final String key;
        final int number;
        final String title;
        final String description;
        final int part;

        ModuleInfo(String key, int number, String title, String description, int part) {
            this.key = key;
            this.number = number;
            this.title = title;
            this.description = description;
            this.part = part;
        }
    }
}