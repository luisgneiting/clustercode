package net.chrigel.clustercode.scan;

import net.chrigel.clustercode.task.MediaCandidate;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface ScanService {

    /**
     * Gets a map of files under {@link ScanSettings#getBaseInputDir()}. Each key contains the sourcePath to a directory
     * which contains a number as name. The number represents the priority of the queue. The sourcePath is relative to the
     * base input directory. The value of the map is a list of files that were found in the sourcePath, with each sourcePath
     * being relative to the base input directory. The non-null list is empty if no candidates for queueing were
     * found. Completed jobs are excluded from the list, as well as files which do not match the whitelisted
     * extensions.
     * <p>
     * Example:<br>
     * {@code /input/1/file_That_Is_Being_Included.mp4}
     * {@code /input/2/file_completed_will_be_ignored.mp4} because of
     * {@code /input/2/file_completed_will_be_ignored.mp4.done}
     * {@code /input/3/subdir/this_textfile_will_be_ignored.txt}
     * {@code /input/this_directory_is_not_a_number_and_will_be_ignored}<br>
     * The resulting map would contain 3 keys, but only key {@code /input/1} will have an entry in the list.
     * </p>
     * <p>
     * This method is blocking until the file system has been recursively scanned.
     * </p>
     *
     * @return the map as described. Empty map if no priority directories found.
     * @throws RuntimeException if {@link ScanSettings#getBaseInputDir()} is not readable.
     */
    Map<Path, List<MediaCandidate>> retrieveFiles();

}
