package net.serverpeon.twitcharchiver.downloader;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

import java.io.File;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class UriFileMapping<E> implements Iterable<UriFileMapping<E>.UriFileEntry> {
    private final List<UriFileEntry> uriFileEntries;
    private final Function<E, URI> propertyMapping;

    public UriFileMapping(Iterable<E> videoSource, Function<E, URI> propertyMapping, File targetFolder) {
        this.uriFileEntries = ImmutableList.copyOf(generateUriFileMappings(videoSource, propertyMapping, targetFolder));
        this.propertyMapping = propertyMapping;
    }

    private static boolean successfullyDownloaded(UriFileMapping<?>.UriFileEntry entry, ProgressTracker tracker) {
        return tracker.getStatus(entry.getURI().toString()) == ProgressTracker.Status.DOWNLOADED &&
                entry.target.exists();
    }

    private Iterable<UriFileEntry> generateUriFileMappings(
            final Iterable<E> videoSource,
            final Function<E, URI> propertyMapping,
            final File targetFolder
    ) {
        final AtomicInteger counter = new AtomicInteger(0);
        return FluentIterable.from(videoSource).transform(new Function<E, UriFileEntry>() {
            @Override
            public UriFileEntry apply(E e) {
                final URI target = propertyMapping.apply(e);
                final String extension = Files.getFileExtension(target.getPath());
                return new UriFileEntry(e, new File(targetFolder, String.format(
                        "part%d.%s",
                        counter.incrementAndGet(),
                        extension
                )));
            }
        });
    }

    @Override
    public Iterator<UriFileEntry> iterator() {
        return uriFileEntries.iterator();
    }

    public void generateConcatFile(final PrintWriter out, final Path parentPath, final ProgressTracker tracker) {
        for (UriFileEntry entry : this) {
            final Path relativePath = parentPath.relativize(entry.target.toPath());

            if (successfullyDownloaded(entry, tracker)) {
                out.printf("file '%s'%n", relativePath);
            } else {
                out.printf("# Missing file '%s'%n", relativePath);
            }
        }
    }

    public <T> List<T> generateTasks(final ProgressTracker tracker, final Function<UriFileEntry, T> taskCreator) {
        return FluentIterable.from(this).filter(new Predicate<UriFileEntry>() {
            @Override
            public boolean apply(UriFileEntry entry) {
                //Filter out files that are already downloaded
                return !successfullyDownloaded(entry, tracker);
            }
        }).transform(taskCreator).toList();
    }

    public class UriFileEntry {
        public final E source;
        public final File target;

        private UriFileEntry(E source, File target) {
            this.source = source;
            this.target = target;
        }

        public URI getURI() {
            return propertyMapping.apply(source);
        }
    }
}
