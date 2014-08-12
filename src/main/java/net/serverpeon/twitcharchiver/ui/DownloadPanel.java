package net.serverpeon.twitcharchiver.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class DownloadPanel extends JPanel {
    private final static int MAX_PROCESSORS = Runtime.getRuntime().availableProcessors();
    private final JSpinner parallelSpinner = new JSpinner(new SpinnerNumberModel(
            Math.max(MAX_PROCESSORS - 1, 1),
            1,
            Math.max(MAX_PROCESSORS - 1, 1),
            1
    ));
    private final JProgressBar processing = new JProgressBar();
    private final JButton downloadButton = new JButton();

    public DownloadPanel(final Runnable buttonCallback) {
        this.setBorder(BorderFactory.createTitledBorder("Download"));
        this.setLayout(new GridBagLayout());

        final GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;

        this.add(new JLabel("Parallel Downloads"), c);
        c.gridx = 1;
        this.add(this.parallelSpinner);

        c.gridy = 1;
        c.gridx = 0;
        c.gridwidth = 2;
        this.downloadButton.setAction(new AbstractAction("Download Selected Broadcasts") {
            @Override
            public void actionPerformed(ActionEvent e) {
                buttonCallback.run();
            }
        });
        this.add(this.downloadButton, c);

        c.gridy = 2;
        this.add(this.processing, c);
    }

    public int getNumberOfProcesses() {
        return ((Number) this.parallelSpinner.getValue()).intValue();
    }

    public void setProcessing(final boolean processing) {
        this.processing.setIndeterminate(processing);
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.parallelSpinner.setEnabled(enabled);
        this.downloadButton.setEnabled(enabled);
    }
}
