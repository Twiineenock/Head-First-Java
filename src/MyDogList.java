public class MyDogList {
    private final Animal[] animals = new Animal[5];
    private int nextIndex = 0;

    public Animal[] getAnimals() {
        return animals;
    }

    public void add(Animal d) {
        if(nextIndex <  animals.length) {
            animals[nextIndex] = d;
            System.out.println("Dog added at " + nextIndex);
            nextIndex++;
        }
    }
}
