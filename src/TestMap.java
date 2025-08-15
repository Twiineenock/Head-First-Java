import java.util.HashMap;
import java.util.Map;

public class TestMap {
    public static void main(String[] args) {
        Map<String, Integer> scores = new HashMap<>();

        scores.put("Age", 22);
        scores.put("BPM", 33);
        scores.put("Name", 117);
        scores.put("Hobby", 1);

        System.out.println(scores);
        System.out.println(scores.get("Name"));
    }
}
