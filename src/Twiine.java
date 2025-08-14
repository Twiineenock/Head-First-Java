public class Twiine {
    private Integer i;
    private int j;

    public static void main(String[] args) {
        Twiine twiine = new Twiine();
        twiine.go();
    }

    public void go() {
        j = i;
        System.out.println(j);
        System.out.println(i);
    }
}