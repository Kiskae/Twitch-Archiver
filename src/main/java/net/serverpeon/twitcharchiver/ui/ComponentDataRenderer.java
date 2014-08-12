package net.serverpeon.twitcharchiver.ui;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class ComponentDataRenderer implements TableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (value instanceof JComponent) {
            return (JComponent) value;
        } else {
            return null;
        }
    }
}
