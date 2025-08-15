import java.util.Collections;
import java.util.List;

public class Jukebox3 {
    public static void main(String[] args) {
        Jukebox3 jukebox3 = new Jukebox3();
        jukebox3.go();
    }
    public void go() {
        List<SongV3> songList = MockSongs.getSongsV3();
        System.out.println(songList);
        Collections.sort(songList);
        System.out.println(songList);
    }
}
