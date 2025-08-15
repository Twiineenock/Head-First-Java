import java.util.List;

public class Jukebox6 {
    public static void main(String[] args) {
        new Jukebox6().go();
    }
    public void go() {
        List<SongV3> songV3List = MockSongs.getSongsV3();
        songV3List.sort((one, t ) -> one.getTitle().compareTo(t.getTitle()));
        System.out.println("Sorted by title mans");
        System.out.println(songV3List);

        songV3List.sort((one, tow)-> one.getArtist().compareTo(tow.getArtist()));
        System.out.println("Sorted by artist mans");
        System.out.println(songV3List);

        songV3List.sort((one, two)-> one.getBpm().compareTo(two.getBpm()));
        System.out.println("Sorted by bpm mans");
        System.out.println(songV3List);

        songV3List.sort((s1, s2) -> s2.getTitle().compareTo(s1.getTitle()));
        System.out.println("Sorted by title in descending order mans");
        System.out.println(songV3List);
    }
}
