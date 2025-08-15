import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Jukebox4 {
    public static void main(String[] args) {
        Jukebox4 jukebox4 = new Jukebox4();
        jukebox4.go();
    }
    public void go() {
        List<SongV3> songV3List = MockSongs.getSongsV3();
        System.out.println(songV3List);
        Collections.sort(songV3List);
        System.out.println("Sorted by title");
        System.out.println(songV3List);

        ArtistCompare artistCompare = new ArtistCompare();
        songV3List.sort(artistCompare);
        System.out.println("Sorted by Artist");
        System.out.println(songV3List);
    }
}
