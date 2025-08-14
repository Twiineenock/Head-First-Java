import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Jukebox2 {
    public static void main(String[] args) {

    }
    public void go() {
        List<SongV2> songList = MockSongs.getSongsV2();
        System.out.println(songList);
        Collections.sort(songList);
        System.out.println(songList);
    }
}
