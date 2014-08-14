package net.serverpeon.twitcharchiver.ui;

import com.google.common.base.Predicate;
import net.serverpeon.twitcharchiver.downloader.VideoStore;
import net.serverpeon.twitcharchiver.downloader.VideoStoreDownloader;
import net.serverpeon.twitcharchiver.twitch.InvalidOAuthTokenException;
import net.serverpeon.twitcharchiver.twitch.SubscriberOnlyException;
import net.serverpeon.twitcharchiver.twitch.TwitchApi;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class GUI extends JFrame {
    private final static Logger logger = LogManager.getLogger(GUI.class);
    private final ExecutorService executors = Executors.newSingleThreadExecutor();
    private final OAuthPanel oauth;
    private final ChannelPanel channel;
    private final DataPanel vp = new DataPanel();
    private final DownloadPanel download;
    private final AtomicReference<VideoStore> vs = new AtomicReference<>();
    private String selectedChannel = null;
    private String userOAuthToken = null;

    public GUI() {
        super("Twitch Archiver - by @KiskaeEU");

        this.oauth = new OAuthPanel(new Predicate<String>() {
            @Override
            public boolean apply(String s) {
                final String oauthToken;
                if (s.startsWith("oauth:"))
                    oauthToken = s.substring(6);
                else
                    oauthToken = s;

                setSelectedChannel(null, null);

                if (s.isEmpty()) return false;

                executors.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final String userName = TwitchApi.getTwitchUsernameWithOAuth(oauthToken);
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    setSelectedChannel(userName, oauthToken);
                                }
                            });
                        } catch (InvalidOAuthTokenException e) {
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    JOptionPane.showMessageDialog(
                                            GUI.this,
                                            "The OAuth token you gave was invalid.",
                                            "Invalid token",
                                            JOptionPane.ERROR_MESSAGE
                                    );
                                }
                            });
                        }
                    }
                });

                return false;
            }
        });

        this.channel = new ChannelPanel(new Runnable() {
            @Override
            public void run() {
                oauth.setEnabled(false);
                channel.setEnabled(false);
                channel.enableProgressBar(true);
                download.setEnabled(false);

                final VideoStore vs = new VideoStore(channel.getStorageDirectory(), selectedChannel, userOAuthToken);
                GUI.this.vs.set(vs);

                vp.setTableModel(vs.getTableView(), new Predicate<Boolean>() {
                    @Override
                    public boolean apply(Boolean aBoolean) {
                        vs.setSelectedForAll(aBoolean);
                        return false;
                    }
                });

                final int limit = channel.getLimit();
                executors.execute(new Runnable() {
                    @Override
                    public void run() {
                        final Runnable r = new Runnable() {
                            @Override
                            public void run() {
                                SwingUtilities.invokeLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        oauth.setEnabled(true);
                                        channel.setEnabled(true);
                                        channel.enableProgressBar(false);
                                        download.setEnabled(true);
                                    }
                                });
                            }
                        };

                        try {
                            vs.loadData(limit, r);
                        } catch (SubscriberOnlyException ex) {
                            r.run();
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    JOptionPane.showMessageDialog(GUI.this,
                                            "These videos are limited to subscribers, this should only show up if you're messing with code.",
                                            "I cannae see them, captain",
                                            JOptionPane.ERROR_MESSAGE);
                                }
                            });
                        }
                    }
                });
            }
        });

        this.download = new DownloadPanel(new Runnable() {
            @Override
            public void run() {
                final VideoStore vs = getVideoStore();

                int ret = JOptionPane.showConfirmDialog(GUI.this,
                        "Please make sure you have enough disk space to store the videos. \n" +
                                "Do you have enough space?",
                        "Check your disk space!",
                        JOptionPane.YES_NO_OPTION);

                if (ret == JOptionPane.YES_OPTION && vs != null) {
                    oauth.setEnabled(false);
                    channel.setEnabled(false);
                    download.setEnabled(false);
                    vp.setEnabled(false);
                    download.setProcessing(true);

                    new VideoStoreDownloader(vs, new Runnable() {
                        @Override
                        public void run() {
                            oauth.setEnabled(true);
                            channel.setEnabled(true);
                            download.setEnabled(true);
                            vp.setEnabled(true);
                            download.setProcessing(false);
                        }
                    }, download.getNumberOfProcesses()).start();
                }
            }
        });
    }

    public void init() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (final UnsupportedLookAndFeelException ignored) {
            /* Will just default to the cross-platform L&F */
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            logger.warn("Exception setting Swing L&F", e);
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                initWindow();
            }
        });
    }

    private void setSelectedChannel(final String channelName, String oauthToken) {
        this.selectedChannel = channelName;
        this.userOAuthToken = oauthToken;
        this.channel.setChannelName(channelName);
        this.download.setEnabled(false);
    }

    private void initWindow() {
        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        this.add(vp, BorderLayout.CENTER);
        this.add(createControlPanel(), BorderLayout.LINE_END);

        this.download.setEnabled(false);

        SwingUtilities.updateComponentTreeUI(this);
        this.pack();
        this.setVisible(true);
    }

    public VideoStore getVideoStore() {
        return this.vs.get();
    }

    private JComponent createControlPanel() {
        final JPanel ret = new JPanel();
        ret.setLayout(new BoxLayout(ret, BoxLayout.PAGE_AXIS));

        ret.add(this.oauth);
        ret.add(Box.createVerticalGlue());
        ret.add(this.channel);
        ret.add(Box.createVerticalGlue());
        ret.add(this.download);

        return ret;
    }
}
