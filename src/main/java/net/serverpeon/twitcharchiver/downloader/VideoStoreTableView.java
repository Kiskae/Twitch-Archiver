package net.serverpeon.twitcharchiver.downloader;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;

public class VideoStoreTableView extends AbstractTableModel {
    private final VideoStore vs;
    private boolean editable = true;

    VideoStoreTableView(final VideoStore vs) {
        this.vs = Preconditions.checkNotNull(vs);
    }

    public void setEditable(final boolean b) {
        this.editable = b;
    }

    @Override
    public int getRowCount() {
        return this.vs.getNumberOfBroadcasts();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.values().length;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        final StoredBroadcast entry = this.vs.getBroadcastByIdx(rowIndex);
        if (entry != null) {
            COLUMNS.getByIdx(columnIndex).set.consume(entry, aValue);
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return this.editable && COLUMNS.getByIdx(columnIndex).editable;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return COLUMNS.getByIdx(columnIndex).clazz;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS.getByIdx(column).name;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        final StoredBroadcast entry = this.vs.getBroadcastByIdx(rowIndex);
        if (entry != null) {
            return COLUMNS.getByIdx(columnIndex).get.apply(entry);
        } else {
            return null;
        }
    }

    public enum COLUMNS {
        SELECTED(0, "D", Boolean.class, true, new Function<StoredBroadcast, Object>() {
            @Override
            public Object apply(StoredBroadcast storedBroadcast) {
                return storedBroadcast.isSelected();
            }
        }, new BiConsumer<StoredBroadcast, Object>() {
            @Override
            public void consume(StoredBroadcast first, Object second) {
                first.setSelected((Boolean) second);
            }
        }),
        TITLE(1, "VoD title", String.class, false, new Function<StoredBroadcast, Object>() {
            @Override
            public Object apply(StoredBroadcast storedBroadcast) {
                return storedBroadcast.getBroadcastInformation().title;
            }
        }),
        LENGTH(2, "Video Length", String.class, false, new Function<StoredBroadcast, Object>() {
            @Override
            public Object apply(StoredBroadcast storedBroadcast) {
                return storedBroadcast.getLengthRepr();
            }
        }),
        VIEWS(3, "Video Views", Number.class, false, new Function<StoredBroadcast, Object>() {
            @Override
            public Object apply(StoredBroadcast storedBroadcast) {
                return storedBroadcast.getBroadcastInformation().getViews();
            }
        }),
        SIZE(4, "Approximate File Size (@ 2.5 Mbps)", String.class, false, new Function<StoredBroadcast, Object>() {
            @Override
            public Object apply(StoredBroadcast storedBroadcast) {
                return storedBroadcast.getApproximateSizeRepr();
            }
        }),
        RECORDING_DATE(5, "Recording Date", String.class, false, new Function<StoredBroadcast, Object>() {
            @Override
            public Object apply(StoredBroadcast storedBroadcast) {
                return storedBroadcast.getBroadcastAtRepr();
            }
        }),
        FILE_PARTS_TOTAL(6, "TP", Number.class, false, new Function<StoredBroadcast, Object>() {
            @Override
            public Object apply(StoredBroadcast storedBroadcast) {
                return storedBroadcast.getNumberOfParts();
            }
        }),
        DOWNLOADED_PARTS(7, "DP", Number.class, false, new Function<StoredBroadcast, Object>() {
            @Override
            public Object apply(StoredBroadcast storedBroadcast) {
                return storedBroadcast.getDownloadedParts();
            }
        }),
        FAILED_PARTS(8, "FP", Number.class, false, new Function<StoredBroadcast, Object>() {
            @Override
            public Object apply(StoredBroadcast storedBroadcast) {
                return storedBroadcast.getFailedParts();
            }
        }),
        DOWNLOAD_PROGRESS(9, "Download Progress", JProgressBar.class, false, new Function<StoredBroadcast, Object>() {
            @Override
            public Object apply(StoredBroadcast storedBroadcast) {
                return storedBroadcast.getDownloadProgress();
            }
        }),
        VIDEO_MUTED(10, "MP", Number.class, false, new Function<StoredBroadcast, Object>() {
            @Override
            public Object apply(StoredBroadcast storedBroadcast) {
                return storedBroadcast.getNumberOfMutedParts();
            }
        });

        private final static COLUMNS[] columnsByIdx;

        static {
            final COLUMNS[] tmp = COLUMNS.values();
            columnsByIdx = new COLUMNS[tmp.length];

            for (COLUMNS c : tmp) {
                columnsByIdx[c.idx] = c;
            }
        }

        private final int idx;
        private final String name;
        private final Class<?> clazz;
        private final boolean editable;
        private final Function<StoredBroadcast, Object> get;
        private final BiConsumer<StoredBroadcast, Object> set;

        private COLUMNS(int idx, String name, Class<?> clazz, boolean editable,
                        Function<StoredBroadcast, Object> get
        ) {
            this(idx, name, clazz, editable, get, new BiConsumer<StoredBroadcast, Object>() {
                @Override
                public void consume(StoredBroadcast first, Object second) {

                }
            });
        }

        private COLUMNS(int idx, String name, Class<?> clazz, boolean editable,
                        Function<StoredBroadcast, Object> get,
                        BiConsumer<StoredBroadcast, Object> set
        ) {
            this.idx = idx;
            this.name = name;
            this.clazz = clazz;
            this.editable = editable;
            this.get = get;
            this.set = set;
        }

        private static COLUMNS getByIdx(int idx) {
            return columnsByIdx[idx];
        }

        public int getIdx() {
            return this.idx;
        }
    }
}
