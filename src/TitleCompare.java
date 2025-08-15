import java.util.Comparator;

public class TitleCompare implements Comparator<SongV3> {
    public int compare(SongV3 o1, SongV3 o2) {
        return o1.getTitle().compareTo(o2.getTitle());
    }
}
