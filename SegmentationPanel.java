import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
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
 * GUI for Module 5: Segmentation.
 * All calculations are done by {@link Segmentation}; this class only collects
 * input, validates it and displays the results.
 */
public class SegmentationPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int MAX_SEGMENT_NUMBER = 999;
    private static final int MAX_VALUE = 1000000;

    private final List<Segment> segments = new ArrayList<Segment>();

    // Input widgets
    private final JTextField segmentField = UIStyles.createTextField(6);
    private final JTextField baseField = UIStyles.createTextField(6);
    private final JTextField limitField = UIStyles.createTextField(6);
    private final JTextField addressField = UIStyles.createTextField(24);

    // Tables and result widgets
    private final DefaultTableModel segmentModel;
    private final JTable segmentTable;
    private final DefaultTableModel resultModel;
    private final MemoryMapPanel memoryMap = new MemoryMapPanel();
    private final JLabel warningLabel = UIStyles.createMutedLabel(" ");
    private final JLabel summaryLabel = UIStyles.createLabel("Enter logical addresses and click Translate.");

    public SegmentationPanel() {
        setLayout(new BorderLayout(16, 0));
        setBackground(UIStyles.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        segmentModel = new DefaultTableModel(
                new String[]{"Segment", "Base", "Limit", "End Address"}, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        segmentTable = new JTable(segmentModel);
        UIStyles.styleTable(segmentTable);

        resultModel = new DefaultTableModel(
                new String[]{"Logical", "Base", "Limit", "Physical", "Status"}, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        add(buildLeftColumn(), BorderLayout.WEST);
        add(buildRightColumn(), BorderLayout.CENTER);
        refreshAll();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private JPanel buildLeftColumn() {
        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(410, 100));

        JPanel card = UIStyles.createTitledPanel("Segment Table");
        card.setLayout(new BorderLayout(0, 8));
        card.add(UIStyles.createScrollPane(segmentTable), BorderLayout.CENTER);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        addRow(form, 0, "Segment number:", segmentField);
        addRow(form, 1, "Base address:", baseField);
        addRow(form, 2, "Limit (length):", limitField);

        JPanel buttons = new JPanel(new GridLayout(2, 2, 8, 8));
        buttons.setOpaque(false);
        JButton add = UIStyles.createButton("Add");
        JButton remove = UIStyles.createSecondaryButton("Remove");
        JButton clear = UIStyles.createDangerButton("Clear");
        JButton sample = UIStyles.createSecondaryButton("Load Sample");
        add.addActionListener(e -> addSegment());
        remove.addActionListener(e -> removeSelected());
        clear.addActionListener(e -> clearTable());
        sample.addActionListener(e -> loadSample());
        buttons.add(add);
        buttons.add(remove);
        buttons.add(clear);
        buttons.add(sample);
        GridBagConstraints bc = new GridBagConstraints();
        bc.gridx = 0;
        bc.gridy = 3;
        bc.gridwidth = 2;
        bc.fill = GridBagConstraints.HORIZONTAL;
        bc.insets = new Insets(8, 4, 4, 4);
        form.add(buttons, bc);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setOpaque(false);
        warningLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.setAlignmentX(Component.LEFT_ALIGNMENT);
        south.add(warningLabel);
        south.add(Box.createRigidArea(new Dimension(0, 4)));
        south.add(form);
        card.add(south, BorderLayout.SOUTH);

        left.add(card, BorderLayout.CENTER);
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
        input.add(UIStyles.createLabel("Addresses (segment:offset, e.g. 2:53, 0:1222):"), BorderLayout.WEST);
        input.add(addressField, BorderLayout.CENTER);
        JButton translate = UIStyles.createButton("Translate");
        translate.addActionListener(e -> translateAddresses());
        input.add(translate, BorderLayout.EAST);

        JTable resultTable = new JTable(resultModel);
        UIStyles.styleTable(resultTable);
        int[] widths = {80, 60, 60, 80, 340};
        for (int c = 0; c < widths.length; c++) {
            resultTable.getColumnModel().getColumn(c).setPreferredWidth(widths[c]);
        }

        JPanel summary = new JPanel();
        summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
        summary.setOpaque(false);
        JLabel formula = UIStyles.createMutedLabel(
                "Valid when 0 <= offset < limit     Physical address = base + offset");
        summaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        formula.setAlignmentX(Component.LEFT_ALIGNMENT);
        summary.add(summaryLabel);
        summary.add(Box.createRigidArea(new Dimension(0, 4)));
        summary.add(formula);

        translateCard.add(input, BorderLayout.NORTH);
        translateCard.add(UIStyles.createScrollPane(resultTable), BorderLayout.CENTER);
        translateCard.add(summary, BorderLayout.SOUTH);
        right.add(translateCard);

        // --- Memory map ---
        JPanel mapCard = UIStyles.createTitledPanel("Physical Memory Map");
        mapCard.setLayout(new BorderLayout());
        mapCard.add(UIStyles.createScrollPane(memoryMap), BorderLayout.CENTER);
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

    private void addSegment() {
        try {
            int number = UIStyles.parseInt(segmentField.getText(), "Segment number", 0);
            int base = UIStyles.parseInt(baseField.getText(), "Base address", 0);
            int limit = UIStyles.parseInt(limitField.getText(), "Limit", 1);
            if (number > MAX_SEGMENT_NUMBER) {
                throw new IllegalArgumentException("Segment number must be at most " + MAX_SEGMENT_NUMBER + ".");
            }
            if (base > MAX_VALUE) {
                throw new IllegalArgumentException("Base address must be at most " + MAX_VALUE + ".");
            }
            if (limit > MAX_VALUE) {
                throw new IllegalArgumentException("Limit must be at most " + MAX_VALUE + ".");
            }

            Segment segment = new Segment(number, base, limit);
            List<Segment> candidate = new ArrayList<Segment>(segments);
            candidate.add(segment);
            Segmentation.validateTable(candidate);      // duplicate segment numbers

            segments.add(segment);
            sortSegments();
            clearResults();
            refreshAll();
            segmentField.setText("");
            baseField.setText("");
            limitField.setText("");
            segmentField.requestFocusInWindow();
        } catch (IllegalArgumentException ex) {
            UIStyles.showError(this, ex.getMessage());
        }
    }

    private void removeSelected() {
        int row = segmentTable.getSelectedRow();
        if (row < 0) {
            UIStyles.showWarning(this, "Select a row in the segment table first.");
            return;
        }
        segments.remove(row);
        clearResults();
        refreshAll();
    }

    private void clearTable() {
        segments.clear();
        clearResults();
        refreshAll();
    }

    private void loadSample() {
        segments.clear();
        segments.add(new Segment(0, 1400, 1000));
        segments.add(new Segment(1, 6300, 400));
        segments.add(new Segment(2, 4300, 400));
        segments.add(new Segment(3, 3200, 1100));
        segments.add(new Segment(4, 4700, 1000));
        addressField.setText("2:53, 3:852, 0:1222, 1:100, 7:5, 4:999");
        clearResults();
        refreshAll();
    }

    private void translateAddresses() {
        try {
            if (segments.isEmpty()) {
                throw new IllegalArgumentException("Add at least one segment to the table first.");
            }
            List<Segmentation.Address> addresses = parseAddresses(addressField.getText());
            showResults(Segmentation.translateAll(segments, addresses));
        } catch (IllegalArgumentException ex) {
            UIStyles.showError(this, ex.getMessage());
        }
    }

    /** Parses text like "2:53, 0:1222 1:7" into segment/offset pairs. */
    private List<Segmentation.Address> parseAddresses(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Logical addresses cannot be empty.");
        }
        String normalized = text.trim().replaceAll("\\s*:\\s*", ":");
        List<Segmentation.Address> list = new ArrayList<Segmentation.Address>();
        for (String token : normalized.split("[,\\s]+")) {
            if (token.isEmpty()) {
                continue;
            }
            String[] parts = token.split(":", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Each address must look like segment:offset, "
                        + "for example 2:53 (got \"" + token + "\").");
            }
            int segment = UIStyles.parseInt(parts[0], "Segment number in \"" + token + "\"", 0);
            int offset = UIStyles.parseInt(parts[1], "Offset in \"" + token + "\"", 0);
            if (segment > MAX_SEGMENT_NUMBER) {
                throw new IllegalArgumentException("Segment number in \"" + token + "\" must be at most "
                        + MAX_SEGMENT_NUMBER + ".");
            }
            list.add(new Segmentation.Address(segment, offset));
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("Logical addresses cannot be empty.");
        }
        return list;
    }

    // ------------------------------------------------------------------
    // Display
    // ------------------------------------------------------------------

    private void showResults(List<Segmentation.Translation> results) {
        resultModel.setRowCount(0);
        Set<Long> markers = new HashSet<Long>();
        int valid = 0;
        int invalid = 0;
        for (Segmentation.Translation t : results) {
            String base = t.isSegmentFound() ? String.valueOf(t.getBase()) : "-";
            String limit = t.isSegmentFound() ? String.valueOf(t.getLimit()) : "-";
            if (t.isValid()) {
                valid++;
                markers.add(t.getPhysicalAddress());
                resultModel.addRow(new Object[]{t.getAddress().toString(), base, limit,
                        t.getPhysicalAddress(), t.getMessage()});
            } else {
                invalid++;
                resultModel.addRow(new Object[]{t.getAddress().toString(), base, limit, "-", t.getMessage()});
            }
        }
        summaryLabel.setText("Translated " + results.size() + " address(es):  " + valid
                + " valid,  " + invalid + " invalid (trap).");
        memoryMap.setData(segments, markers);
    }

    private void clearResults() {
        resultModel.setRowCount(0);
        summaryLabel.setText("Enter logical addresses and click Translate.");
    }

    private void sortSegments() {
        Collections.sort(segments, new Comparator<Segment>() {
            @Override
            public int compare(Segment a, Segment b) {
                return Integer.compare(a.getSegmentNumber(), b.getSegmentNumber());
            }
        });
    }

    /** Redraws the segment table, overlap warning and memory map. */
    private void refreshAll() {
        segmentModel.setRowCount(0);
        for (Segment s : segments) {
            segmentModel.addRow(new Object[]{s.getSegmentNumber(), s.getBase(), s.getLimit(), s.getEndAddress()});
        }

        List<String> overlaps = Segmentation.findOverlaps(segments);
        if (overlaps.isEmpty()) {
            warningLabel.setText(" ");
        } else {
            StringBuilder sb = new StringBuilder("<html><font color='#C0392B'>Warning: ");
            sb.append(overlaps.get(0));
            if (overlaps.size() > 1) {
                sb.append(" (+").append(overlaps.size() - 1).append(" more)");
            }
            warningLabel.setText(sb.append("</font></html>").toString());
        }
        memoryMap.setData(segments, new HashSet<Long>());
    }

    // ------------------------------------------------------------------
    // Memory map component
    // ------------------------------------------------------------------

    /** One bar per segment on a shared physical-address scale; valid addresses are marked. */
    private static class MemoryMapPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private static final Color[] PALETTE = {
                new Color(0x2F5DA8), new Color(0x8E44AD), new Color(0xD68910),
                new Color(0x138D90), new Color(0xC0392B), new Color(0x7D6608),
                new Color(0x5D6D7E), new Color(0x2E8B57)
        };
        private static final int ROW_HEIGHT = 26;
        private static final int GAP = 6;
        private static final int LEFT = 90;
        private static final int RIGHT = 20;
        private static final int TOP = 12;

        private List<Segment> data = new ArrayList<Segment>();
        private Set<Long> markers = new HashSet<Long>();

        MemoryMapPanel() {
            setBackground(Color.WHITE);
        }

        void setData(List<Segment> newData, Set<Long> newMarkers) {
            data = new ArrayList<Segment>(newData);
            markers = new HashSet<Long>(newMarkers);
            setPreferredSize(new Dimension(300, TOP + data.size() * (ROW_HEIGHT + GAP) + 40));
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
            g2.setFont(UIStyles.SMALL_FONT);
            FontMetrics fm = g2.getFontMetrics();

            if (data.isEmpty()) {
                g2.setColor(UIStyles.TEXT_MUTED);
                g2.drawString("Add segments to see them in physical memory.", LEFT, TOP + 20);
                g2.dispose();
                return;
            }

            long max = 1;
            for (Segment s : data) {
                max = Math.max(max, (long) s.getEndAddress() + 1);
            }
            int width = Math.max(50, getWidth() - LEFT - RIGHT);
            int bottom = TOP + data.size() * (ROW_HEIGHT + GAP);

            for (int i = 0; i < data.size(); i++) {
                Segment s = data.get(i);
                int y = TOP + i * (ROW_HEIGHT + GAP);
                int x1 = LEFT + (int) ((long) s.getBase() * width / max);
                int x2 = LEFT + (int) (((long) s.getEndAddress() + 1) * width / max);

                g2.setColor(UIStyles.TEXT_DARK);
                g2.drawString("Segment " + s.getSegmentNumber(), 12, y + (ROW_HEIGHT + fm.getAscent()) / 2 - 2);

                g2.setColor(PALETTE[i % PALETTE.length]);
                g2.fillRect(x1, y, Math.max(3, x2 - x1), ROW_HEIGHT);
                g2.setColor(UIStyles.TEXT_DARK);
                g2.drawRect(x1, y, Math.max(3, x2 - x1), ROW_HEIGHT);

                String label = s.getBase() + " - " + s.getEndAddress();
                if (fm.stringWidth(label) + 8 <= x2 - x1) {
                    g2.setColor(Color.WHITE);
                    g2.drawString(label, x1 + 4, y + (ROW_HEIGHT + fm.getAscent()) / 2 - 2);
                }
            }

            // Valid translated addresses as vertical green lines
            g2.setColor(UIStyles.SUCCESS);
            for (long address : markers) {
                int x = LEFT + (int) (address * width / max);
                g2.fillRect(x - 1, TOP - 6, 3, bottom - TOP + 6);
            }

            // Axis
            g2.setColor(UIStyles.TEXT_DARK);
            g2.drawLine(LEFT, bottom + 4, LEFT + width, bottom + 4);
            g2.drawString("0", LEFT, bottom + 18);
            String end = String.valueOf(max);
            g2.drawString(end, LEFT + width - fm.stringWidth(end), bottom + 18);
            if (!markers.isEmpty()) {
                g2.setColor(UIStyles.SUCCESS);
                g2.drawString("green line = translated physical address", LEFT + 50, bottom + 18);
            }
            g2.dispose();
        }
    }
}