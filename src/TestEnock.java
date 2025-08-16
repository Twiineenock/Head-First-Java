public class TestEnock {
    public static void main(String[] args) {
        Enock enock = new Enock("Twiine", "Enock", 23);
        String firstName = enock.getFirstName();
        String lastName = enock.getLastName();
        Integer age = enock.getAge();
        System.out.println("First Name: " + firstName);
        System.out.println("Last Name: " + lastName);
        System.out.println("Age: " + age);
    }
}
