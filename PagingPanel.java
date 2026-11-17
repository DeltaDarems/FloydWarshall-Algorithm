import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * GUI for Module 3: Paging.
 * All calculations are done by {@link Paging}; this class only collects
 * input, validates it and displays the results.
 */
public class PagingPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int MAX_PAGE_SIZE = 1000000;
    private static final int MAX_FRAMES = 256;
    private static final int MAX_PAGE_NUMBER = 100000;

    // Applied settings (changed only by "Apply Settings")
    private int pageSize = 1024;
    private int frameCount = 8;
    private final List<Paging.PageEntry> entries = new ArrayList<Paging.PageEntry>();

    // Setup widgets
    private final JTextField pageSizeField = UIStyles.createTextField(8);
    private final JTextField frameCountField = UIStyles.createTextField(8);
    private final JTextField pageField = UIStyles.createTextField(6);
    private final JTextField frameField = UIStyles.createTextField(6);
    private final JTextField addressField = UIStyles.createTextField(20);

    // Tables and result widgets
    private final DefaultTableModel pageTableModel;
    private final JTable pageTable;
    private final DefaultTableModel resultModel;
    private final FrameMapPanel frameMap = new FrameMapPanel();
    private final JLabel summaryLabel = UIStyles.createLabel("Enter logical addresses and click Translate.");

    public PagingPanel() {
        setLayout(new BorderLayout(16, 0));
        setBackground(UIStyles.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        pageTableModel = new DefaultTableModel(
                new String[]{"Page", "Frame", "Start Address"}, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        pageTable = new JTable(pageTableModel);
        UIStyles.styleTable(pageTable);

        resultModel = new DefaultTableModel(
                new String[]{"Logical", "Page", "Offset", "Frame", "Physical", "Status"}, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        pageSizeField.setText(String.valueOf(pageSize));
        frameCountField.setText(String.valueOf(frameCount));

        add(buildLeftColumn(), BorderLayout.WEST);
        add(buildRightColumn(), BorderLayout.CENTER);
        refreshAll();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private JPanel buildLeftColumn() {
        JPanel left = new JPanel(new BorderLayout(0, 12));
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(410, 100));

        // --- Memory settings ---
        JPanel settings = UIStyles.createTitledPanel("Memory Settings");
        settings.setLayout(new GridBagLayout());
        addRow(settings, 0, "Page size (bytes):", pageSizeField);
        addRow(settings, 1, "Number of frames:", frameCountField);

        JPanel settingButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        settingButtons.setOpaque(false);
        JButton apply = UIStyles.createButton("Apply Settings");
        JButton sample = UIStyles.createSecondaryButton("Load Sample");
        apply.addActionListener(e -> applySettings());
        sample.addActionListener(e -> loadSample());
        settingButtons.add(apply);
        settingButtons.add(sample);
        GridBagConstraints sc = new GridBagConstraints();
        sc.gridx = 0;
        sc.gridy = 2;
        sc.gridwidth = 2;
        sc.fill = GridBagConstraints.HORIZONTAL;
        sc.insets = new Insets(8, 4, 4, 4);
        settings.add(settingButtons, sc);
        left.add(settings, BorderLayout.NORTH);

        // --- Page table ---
        JPanel tableCard = UIStyles.createTitledPanel("Page Table");
        tableCard.setLayout(new BorderLayout(0, 8));
        tableCard.add(UIStyles.createScrollPane(pageTable), BorderLayout.CENTER);

        JPanel addPanel = new JPanel(new GridBagLayout());
        addPanel.setOpaque(false);
        addRow(addPanel, 0, "Page number:", pageField);
        addRow(addPanel, 1, "Frame number:", frameField);

        JPanel buttons = new JPanel(new GridLayout(1, 3, 8, 0));
        buttons.setOpaque(false);
        JButton add = UIStyles.createButton("Add");
        JButton remove = UIStyles.createSecondaryButton("Remove");
        JButton clear = UIStyles.createDangerButton("Clear");
        add.addActionListener(e -> addMapping());
        remove.addActionListener(e -> removeSelected());
        clear.addActionListener(e -> clearTable());
        buttons.add(add);
        buttons.add(remove);
        buttons.add(clear);
        GridBagConstraints bc = new GridBagConstraints();
        bc.gridx = 0;
        bc.gridy = 2;
        bc.gridwidth = 2;
        bc.fill = GridBagConstraints.HORIZONTAL;
        bc.insets = new Insets(8, 4, 4, 4);
        addPanel.add(buttons, bc);
        tableCard.add(addPanel, BorderLayout.SOUTH);

        left.add(tableCard, BorderLayout.CENTER);
        return left;
    }

    private JPanel buildRightColumn() {
        JPanel right = new JPanel(new GridLayout(2, 1, 0, 12));
        right.setOpaque(false);

        // --- Address translation ---
        JPanel translateCard = UIStyles.createTitledPanel("Address Translation");
        translateCard.setLayout(new BorderLayout(0, 8));

        JPanel input = new JPanel(new BorderLayout(8, 0));
        input.setOpaque(false);
        input.add(UIStyles.createLabel("Logical addresses (comma or space separated):"), BorderLayout.WEST);
        input.add(addressField, BorderLayout.CENTER);
        JButton translate = UIStyles.createButton("Translate");
        translate.addActionListener(e -> translateAddresses());
        input.add(translate, BorderLayout.EAST);

        JTable resultTable = new JTable(resultModel);
        UIStyles.styleTable(resultTable);
        int[] widths = {65, 50, 60, 50, 70, 340};
        for (int c = 0; c < widths.length; c++) {
            resultTable.getColumnModel().getColumn(c).setPreferredWidth(widths[c]);
        }

        JPanel summary = new JPanel();
        summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
        summary.setOpaque(false);
        JLabel formula = UIStyles.createMutedLabel(
                "Page = address / page size     Offset = address mod page size     "
                        + "Physical = frame x page size + offset");
        summaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        formula.setAlignmentX(Component.LEFT_ALIGNMENT);
        summary.add(summaryLabel);
        summary.add(Box.createRigidArea(new Dimension(0, 4)));
        summary.add(formula);

        translateCard.add(input, BorderLayout.NORTH);
        translateCard.add(UIStyles.createScrollPane(resultTable), BorderLayout.CENTER);
        translateCard.add(summary, BorderLayout.SOUTH);
        right.add(translateCard);

        // --- Frame map ---
        JPanel mapCard = UIStyles.createTitledPanel("Physical Memory (Frames)");
        mapCard.setLayout(new BorderLayout());
        mapCard.add(UIStyles.createScrollPane(frameMap), BorderLayout.CENTER);
        right.add(mapCard);

        return right;
    }

    private void addRow(JPanel panel, int row, String labelText, Component field) {
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0;
        lc.gridy = row;
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(4, 4, 4, 8);
        panel.add(UIStyles.createLabel(labelText), lc);

        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 1;
        fc.gridy = row;
        fc.weightx = 1.0;
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.insets = new Insets(4, 0, 4, 4);
        panel.add(field, fc);
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void applySettings() {
        try {
            int newPageSize = UIStyles.parseInt(pageSizeField.getText(), "Page size", 1);
            int newFrames = UIStyles.parseInt(frameCountField.getText(), "Number of frames", 1);
            if (newPageSize > MAX_PAGE_SIZE) {
                throw new IllegalArgumentException("Page size must be at most " + MAX_PAGE_SIZE + ".");
            }
            if (newFrames > MAX_FRAMES) {
                throw new IllegalArgumentException("Number of frames must be at most " + MAX_FRAMES + ".");
            }
            Paging.validateConfig(newPageSize, newFrames);
            Paging.validateTable(newFrames, entries);   // existing mappings must still fit

            pageSize = newPageSize;
            frameCount = newFrames;
            clearResults();
            refreshAll();
        } catch (IllegalArgumentException ex) {
            UIStyles.showError(this, ex.getMessage()
                    + (ex.getMessage().contains("does not exist")
                    ? "\nRemove or change that mapping before reducing the number of frames." : ""));
        }
    }

    private void addMapping() {
        try {
            int page = UIStyles.parseInt(pageField.getText(), "Page number", 0);
            int frame = UIStyles.parseInt(frameField.getText(), "Frame number", 0);
            if (page > MAX_PAGE_NUMBER) {
                throw new IllegalArgumentException("Page number must be at most " + MAX_PAGE_NUMBER + ".");
            }

            List<Paging.PageEntry> candidate = new ArrayList<Paging.PageEntry>(entries);
            candidate.add(new Paging.PageEntry(page, frame));
            Paging.validateTable(frameCount, candidate);    // frame range, duplicates

            entries.add(new Paging.PageEntry(page, frame));
            sortEntries();
            clearResults();
            refreshAll();
            pageField.setText("");
            frameField.setText("");
            pageField.requestFocusInWindow();
        } catch (IllegalArgumentException ex) {
            UIStyles.showError(this, ex.getMessage());
        }
    }

    private void removeSelected() {
        int row = pageTable.getSelectedRow();
        if (row < 0) {
            UIStyles.showWarning(this, "Select a row in the page table first.");
            return;
        }
        entries.remove(row);
        clearResults();
        refreshAll();
    }

    private void clearTable() {
        entries.clear();
        clearResults();
        refreshAll();
    }

    private void loadSample() {
        pageSize = 1024;
        frameCount = 8;
        pageSizeField.setText("1024");
        frameCountField.setText("8");
        entries.clear();
        entries.add(new Paging.PageEntry(0, 5));
        entries.add(new Paging.PageEntry(1, 2));
        entries.add(new Paging.PageEntry(2, 7));
        entries.add(new Paging.PageEntry(3, 0));
        addressField.setText("2500, 0, 1024, 3100, 5000");
        clearResults();
        refreshAll();
    }

    private void translateAddresses() {
        try {
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("Add at least one page table mapping first.");
            }
            List<Integer> addresses = UIStyles.parseIntList(addressField.getText(), "Logical address", 0);
            List<Paging.Translation> results = Paging.translateAll(pageSize, frameCount, entries, addresses);
            showResults(results);
        } catch (IllegalArgumentException ex) {
            UIStyles.showError(this, ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Display
    // ------------------------------------------------------------------

    private void showResults(List<Paging.Translation> results) {
        resultModel.setRowCount(0);
        Set<Integer> usedFrames = new HashSet<Integer>();
        int valid = 0;
        int invalid = 0;
        for (Paging.Translation t : results) {
            if (t.isValid()) {
                valid++;
                usedFrames.add(t.getFrame());
                resultModel.addRow(new Object[]{t.getLogicalAddress(), t.getPage(), t.getOffset(),
                        t.getFrame(), t.getPhysicalAddress(), t.getMessage()});
            } else {
                invalid++;
                resultModel.addRow(new Object[]{t.getLogicalAddress(),
                        t.getPage() < 0 ? "-" : String.valueOf(t.getPage()),
                        t.getOffset() < 0 ? "-" : String.valueOf(t.getOffset()),
                        "-", "-", t.getMessage()});
            }
        }
        summaryLabel.setText("Translated " + results.size() + " address(es):  " + valid
                + " valid,  " + invalid + " invalid.");
        frameMap.setData(Paging.frameMap(frameCount, entries), pageSize, usedFrames);
    }

    private void clearResults() {
        resultModel.setRowCount(0);
        summaryLabel.setText("Enter logical addresses and click Translate.");
    }

    private void sortEntries() {
        Collections.sort(entries, new Comparator<Paging.PageEntry>() {
            @Override
            public int compare(Paging.PageEntry a, Paging.PageEntry b) {
                return Integer.compare(a.getPage(), b.getPage());
            }
        });
    }

    /** Redraws the page table and the frame map from the current settings and entries. */
    private void refreshAll() {
        pageTableModel.setRowCount(0);
        for (Paging.PageEntry e : entries) {
            pageTableModel.addRow(new Object[]{e.getPage(), e.getFrame(), (long) e.getFrame() * pageSize});
        }
        frameMap.setData(Paging.frameMap(frameCount, entries), pageSize, new HashSet<Integer>());
    }

    // ------------------------------------------------------------------
    // Frame map component
    // ------------------------------------------------------------------

    /** Draws one bar per physical frame; frames used by a translation are highlighted. */
    private static class FrameMapPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private static final int ROW_HEIGHT = 34;
        private static final int GAP = 4;
        private static final int MARGIN = 10;

        private List<Integer> owners = new ArrayList<Integer>();
        private int pageSize = 1;
        private Set<Integer> highlighted = new HashSet<Integer>();

        FrameMapPanel() {
            setBackground(Color.WHITE);
        }

        void setData(List<Integer> newOwners, int newPageSize, Set<Integer> newHighlighted) {
            owners = new ArrayList<Integer>(newOwners);
            pageSize = newPageSize;
            highlighted = new HashSet<Integer>(newHighlighted);
            setPreferredSize(new Dimension(300, MARGIN * 2 + owners.size() * (ROW_HEIGHT + GAP)));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(UIStyles.BODY_FONT);
            FontMetrics fm = g2.getFontMetrics();

            int width = getWidth() - MARGIN * 2;
            for (int i = 0; i < owners.size(); i++) {
                int y = MARGIN + i * (ROW_HEIGHT + GAP);
                int page = owners.get(i);
                boolean free = page == Paging.NONE;
                boolean hit = highlighted.contains(i);

                Color fill = free ? new Color(0xE6EAF0) : (hit ? UIStyles.SUCCESS : UIStyles.PRIMARY);
                g2.setColor(fill);
                g2.fillRect(MARGIN, y, width, ROW_HEIGHT);
                g2.setColor(UIStyles.TEXT_DARK);
                g2.drawRect(MARGIN, y, width, ROW_HEIGHT);

                long start = (long) i * pageSize;
                long end = start + pageSize - 1;
                String left = "Frame " + i + "   [" + start + " - " + end + "]";
                String right = free ? "Free" : "Page " + page;
                int textY = y + (ROW_HEIGHT + fm.getAscent()) / 2 - 2;
                g2.setColor(free ? UIStyles.TEXT_DARK : Color.WHITE);
                g2.drawString(left, MARGIN + 10, textY);
                g2.drawString(right, MARGIN + width - 10 - fm.stringWidth(right), textY);
            }
            g2.dispose();
        }
    }
}