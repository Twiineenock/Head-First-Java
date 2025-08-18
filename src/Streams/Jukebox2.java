package Streams;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Jukebox2 {
    public static void main(String[] args) {
        System.out.println(new Jukebox2().play());
    }
    public List<String> play() {
        List<Song> list = new Songs().getSongs();
        return list.stream()
                .map(Song::getGenre)
                .distinct()
                .collect(Collectors.toList());
    }
}
