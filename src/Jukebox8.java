import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Jukebox8 {
    public static void main(String[] args) {
        new Jukebox8().go();
    }
    public void go() {
        List<SongV3> songs = MockSongs.getSongsV3();
        songs.sort((one, two)->one.getTitle().compareTo(two.getTitle()));
        System.out.println(songs);
        Set<SongV3> songsSet = new HashSet<SongV3>(songs);
        System.out.println("Streams.Songs: " + songsSet);
    }
}
