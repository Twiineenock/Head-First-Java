import java.util.List;

public class Jukebox5 {
    public static void main(String[] args) {
        Jukebox5 jukebox5 = new Jukebox5();
        jukebox5.go();
    }

    public void go() {
        List<SongV3> songV3List = MockSongs.getSongsV3();
        System.out.println(songV3List);

        TitleCompare titleCompare = new TitleCompare();
        songV3List.sort(titleCompare);
        System.out.println("Sorted by title");
        System.out.println(songV3List);

        ArtistCompare artistCompare = new ArtistCompare();
        songV3List.sort(artistCompare);
        System.out.println("Sorted by artist");
        System.out.println(songV3List);
    }
}
