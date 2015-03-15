package net.serverpeon.twitcharchiver.ui;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import net.serverpeon.twitcharchiver.downloader.VideoStoreTableView;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;

class DataPanel extends JPanel {
    private final JTable table = new JTable();
    private Predicate<Boolean> selectedProcessor = Predicates.alwaysTrue();
    private final JButton selectAll = new JButton(new AbstractAction("Select All") {
        @Override
        public void actionPerformed(ActionEvent e) {
            selectedProcessor.apply(true);
        }
    });
    private final JButton deselectAll = new JButton(new AbstractAction("Deselect All") {
        @Override
        public void actionPerformed(ActionEvent e) {
            selectedProcessor.apply(false);
        }
    });

    public DataPanel() {
        this.setLayout(new BorderLayout());

        table.getTableHeader().setReorderingAllowed(false);

        this.add(new JScrollPane(table), BorderLayout.CENTER);
        this.add(createButtons(), BorderLayout.PAGE_END);
    }

    public void setEnabled(boolean enabled) {
        final TableModel tm = table.getModel();
        if (tm instanceof VideoStoreTableView) {
            ((VideoStoreTableView) tm).setEditable(enabled);
        }

        this.selectAll.setEnabled(enabled);
        this.deselectAll.setEnabled(enabled);
    }

    private JComponent createButtons() {
        final JPanel ret = new JPanel();
        ret.setLayout(new FlowLayout());
        ret.add(selectAll);
        ret.add(deselectAll);
        return ret;
    }

    public void setTableModel(final TableModel tm, Predicate<Boolean> predicate) {
        this.table.setModel(tm);
        this.selectedProcessor = predicate;

        final TableColumnModel cm = this.table.getColumnModel();
        cm.getColumn(VideoStoreTableView.COLUMNS.DOWNLOAD_PROGRESS.getIdx())
                .setCellRenderer(new TableCellRenderer() {
                    @Override
                    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                        if (value instanceof JComponent) {
                            return (JComponent) value;
                        } else {
                            return null;
                        }
                    }
                });
        cm.getColumn(VideoStoreTableView.COLUMNS.VIDEO_MUTED.getIdx())
                .setCellRenderer(new DefaultTableCellRenderer() {
                    @Override
                    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                        final Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                        c.setForeground(Color.WHITE);
                        int mutedVideos = value instanceof Number ? ((Number) value).intValue() : 0;
                        if (mutedVideos > 0) {
                            c.setBackground(Color.RED.darker());
                        } else if (mutedVideos < 0) {
                            c.setBackground(Color.ORANGE.darker());
                        } else {
                            c.setBackground(Color.GREEN.darker());
                        }
                        return c;
                    }
                });

        cm.getColumn(VideoStoreTableView.COLUMNS.VIDEO_MUTED.getIdx())
                .setMaxWidth(30);

        cm.getColumn(VideoStoreTableView.COLUMNS.FILE_PARTS_TOTAL.getIdx())
                .setMaxWidth(30);
        cm.getColumn(VideoStoreTableView.COLUMNS.DOWNLOADED_PARTS.getIdx())
                .setMaxWidth(30);
        cm.getColumn(VideoStoreTableView.COLUMNS.FAILED_PARTS.getIdx())
                .setMaxWidth(30);

        cm.getColumn(VideoStoreTableView.COLUMNS.SELECTED.getIdx())
                .setMaxWidth(30);
    }
}
