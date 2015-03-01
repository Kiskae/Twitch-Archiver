package net.serverpeon.twitcharchiver.downloader;

import java.util.concurrent.ForkJoinTask;

public abstract class ForkJoinDownloadTask extends ForkJoinTask<Void> {
    @Override
    public Void getRawResult() {
        return null;
    }

    @Override
    protected void setRawResult(Void value) {

    }

    @Override
    protected boolean exec() {
        run();
        return true;
    }

    protected abstract void run();
}
