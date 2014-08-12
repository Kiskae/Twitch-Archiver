package net.serverpeon.twitcharchiver.ui;

import com.google.common.base.Predicate;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

class OAuthPanel extends JPanel {
    private final JTextField input = new JTextField(32);
    private final JButton submitButton;

    public OAuthPanel(final Predicate<String> callback) {
        this.setBorder(BorderFactory.createTitledBorder("OAuth Token"));

        this.add(input, BorderLayout.CENTER);

        submitButton = new JButton();
        submitButton.setAction(new AbstractAction("Submit") {
            @Override
            public void actionPerformed(ActionEvent e) {
                callback.apply(input.getText());
            }
        });

        this.add(submitButton, BorderLayout.SOUTH);
    }

    public void setEnabled(final boolean enabled) {
        this.input.setEnabled(enabled);
        this.submitButton.setEnabled(enabled);
    }
}
