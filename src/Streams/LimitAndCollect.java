package Streams;

import java.util.List;
import java.util.stream.Collectors;

public class LimitAndCollect {
    public List<String> limitAndCollect(){
        List<String> list = List.of("I", "am", "a", "list", "of", "strings");
        return list.stream()
                .sorted(String::compareToIgnoreCase)
                .limit(4)
                .toList();
    }
}
