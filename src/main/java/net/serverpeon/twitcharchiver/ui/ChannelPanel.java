package net.serverpeon.twitcharchiver.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;

class ChannelPanel extends JPanel {
    private final JTextField channelName = new JTextField(20);
    private final JSpinner limitSpinner = new JSpinner(new SpinnerNumberModel(-1, -1, Integer.MAX_VALUE, 1));
    private final JButton dataButton = new JButton();
    private final JTextField dataLocation = new JTextField(20);
    private final JButton setLocationButton = new JButton();
    private final JProgressBar progressBar = new JProgressBar();

    public ChannelPanel(final Runnable callback) {
        this.setBorder(BorderFactory.createTitledBorder("Channel"));
        this.setLayout(new GridBagLayout());

        final GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;

        this.add(new JLabel("Channel Name"), c);
        c.gridx = 1;
        this.add(this.channelName, c);
        this.channelName.setEditable(false);
        this.channelName.setToolTipText("This will show the channel name associated with the OAuth token.");

        c.gridx = 0;
        c.gridy = 1;
        this.add(new JLabel("Video Limit"), c);
        c.gridx = 1;
        this.add(this.limitSpinner, c);
        this.limitSpinner.setToolTipText("This limits the amount of VoDs it will query, set to -1 for unlimited.");

        c.gridx = 1;
        c.gridy = 2;
        this.add(this.dataLocation, c);
        this.dataLocation.setEditable(false);
        this.dataLocation.setText("");
        c.gridx = 0;
        this.setLocationButton.setAction(new AbstractAction("Change Directory") {
            @Override
            public void actionPerformed(ActionEvent e) {
                final JFileChooser fc = new JFileChooser();
                fc.setAcceptAllFileFilterUsed(false);
                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                fc.setMultiSelectionEnabled(false);

                fc.setCurrentDirectory(
                        isStorageDirectorySet()
                                ? getStorageDirectory()
                                : new File(System.getProperty("user.dir"))
                );

                final int ret = fc.showDialog(ChannelPanel.this, "Select");
                if (ret == JFileChooser.APPROVE_OPTION) {
                    final File sf = fc.getSelectedFile();
                    if (sf.isDirectory()) {
                        dataLocation.setText(sf.getAbsolutePath());
                    }
                }
            }
        });
        this.add(this.setLocationButton, c);

        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 2;
        this.dataButton.setAction(new AbstractAction("Query Twitch for Videos") {
            @Override
            public void actionPerformed(ActionEvent e) {
                callback.run();
            }
        });
        this.add(this.dataButton, c);

        c.gridy = 4;
        this.add(this.progressBar, c);
        progressBar.setString("Querying Twitch, this can take several minutes");

        this.setChannelName("");
    }

    public void setChannelName(final String channelName) {
        this.channelName.setText(channelName);
        setEnabled(channelName != null && !channelName.trim().isEmpty());
    }

    public boolean isStorageDirectorySet() {
        return !this.dataLocation.getText().isEmpty();
    }

    public void enableProgressBar(final boolean enabled) {
        progressBar.setIndeterminate(enabled);
        progressBar.setStringPainted(enabled);
    }

    public void setEnabled(final boolean enabled) {
        this.channelName.setEnabled(enabled);
        this.limitSpinner.setEnabled(enabled);
        this.dataButton.setEnabled(enabled);
        this.dataLocation.setEnabled(enabled);
        this.setLocationButton.setEnabled(enabled);
    }

    public int getLimit() {
        return ((Number) this.limitSpinner.getValue()).intValue();
    }

    public File getStorageDirectory() {
        return new File(this.dataLocation.getText());
    }
}
