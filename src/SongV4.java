public class SongV4 implements Comparable<SongV4> {
    private final String title;
    private final String artist;
    private final int bpm;

    public boolean equals (Object song) {
        if (song == null || getClass() != song.getClass()) return false;
        SongV4 otherSong = (SongV4) song;
        return title.equals(otherSong.getTitle());
    }

    public int hashCode () {
        return title.hashCode();
    }

    public int compareTo (SongV4 song) {
        return title.compareTo(song.getTitle());
    }

    SongV4 (String title, String artist, int bpm) {
        this.title = title;
        this.artist = artist;
        this.bpm = bpm;
    }

    public String getTitle() {
        return title;
    }
    public String getArtist() {
        return artist;
    }
    public int getBpm() {
        return bpm;
    }
    public String toString() {
        return title;
    }
}
