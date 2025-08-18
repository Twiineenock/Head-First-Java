package Streams;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Limit {
    public static void main(String[] args) {
        new Limit().go();
    }

    public void go() {
        List<String> list  = List.of("I", "am", "a", "list", "of", "strings");
        Stream<String> stream = list.stream();
        Stream<String> limit = stream.limit(4);
/*
        long result  = limit.count();
        System.out.println("Result = " + result);
*/
        List<String> resultList  = limit.toList();
        System.out.println(resultList);
    }
}
