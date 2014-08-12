package net.serverpeon.twitcharchiver.ui;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import net.serverpeon.twitcharchiver.downloader.VideoStoreTableView;

import javax.swing.*;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;

class DataPanel extends JPanel {
    private final JTable table = new JTable();
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
    private Predicate<Boolean> selectedProcessor = Predicates.alwaysTrue();

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
                .setCellRenderer(new ComponentDataRenderer());

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
