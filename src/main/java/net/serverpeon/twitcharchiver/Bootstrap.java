package net.serverpeon.twitcharchiver;

import net.serverpeon.twitcharchiver.ui.GUI;

public class Bootstrap {
    public static void main(final String[] args) throws Exception {
        System.setProperty("java.net.preferIPv4Stack", "true");

        new GUI().init();
    }
}
