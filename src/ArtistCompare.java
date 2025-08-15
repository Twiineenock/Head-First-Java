import java.util.Comparator;

public class ArtistCompare implements Comparator<SongV3> {
    public int compare (SongV3 s1, SongV3 s2) {
        return s1.getArtist().compareTo(s2.getArtist());
    }
}
