import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class Jukebox10 {
    public static void main(String[] args) {
        new Jukebox10().go();
    }

    public void go () {
        List <SongV4> songsList = MockSongs.getSongsV4();
        System.out.println(songsList);
        songsList.sort((s1, s2) -> s1.getTitle().compareTo(s2.getTitle()));
        System.out.println(songsList);
        //store the songs in a set to maintain the oder
        Set<SongV4> setSongs = new TreeSet<>(songsList);
        System.out.println(setSongs);
    }
}
