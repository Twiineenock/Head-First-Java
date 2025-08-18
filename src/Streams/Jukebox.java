package Streams;

import java.util.List;

public class Jukebox {
    public static void main(String[] args) {
        List<Song> rockSonngs = new Jukebox().play();
        System.out.println(rockSonngs);
    }
    public List<Song> play() {
        Songs songs = new Songs();
        List<Song> songList = songs.getSongs();
        System.out.println("Songs: " + songList.size());

        return songList.stream()
                .filter((s)-> s.getGenre().contains("Rock"))
                .toList();
    }
}
