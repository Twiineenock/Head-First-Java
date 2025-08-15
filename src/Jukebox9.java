import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Jukebox9 {
    public static void main(String[] args) {
        new Jukebox9().go();
    }

    public void go() {
        List<SongV4> songs = MockSongs.getSongsV4();
        System.out.println(songs);
        songs.sort((one, two)->one.getTitle().compareTo(two.getTitle()));
        System.out.println(songs);
        Set<SongV4> songsSet = new HashSet<>(songs);
        System.out.println(songsSet);
    }
}
