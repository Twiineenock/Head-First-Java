package GUI;

import java.util.ArrayList;
import java.util.List;

public class University {

    private String name;
    private String location;
    private List<Department> departments = new ArrayList<>();

    public University(String name, String location) {
        this.name = name;
        this.location = location;
    }

    public void addDepartment(Department dept) {
        departments.add(dept);
    }

    public List<Department> getDepartments() {
        return departments;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }

    // -------- Inner Class: Department --------
    public class Department {
        private String deptName;
        private Professor head;
        private List<Professor> professors = new ArrayList<>();
        private List<Student> students = new ArrayList<>();

        public Department(String deptName, Professor head) {
            this.deptName = deptName;
            this.head = head;
        }

        public void addProfessor(Professor prof) {
            professors.add(prof);
        }

        public void addStudent(Student student) {
            students.add(student);
        }

        public String getDeptName() {
            return deptName;
        }

        public Professor getHead() {
            return head;
        }

        public List<Professor> getProfessors() {
            return professors;
        }

        public List<Student> getStudents() {
            return students;
        }
    }

    // -------- Inner Class: Professor --------
    public class Professor {
        private String name;
        private String specialization;

        public Professor(String name, String specialization) {
            this.name = name;
            this.specialization = specialization;
        }

        public String getName() {
            return name;
        }

        public String getSpecialization() {
            return specialization;
        }

        public void teach() {
            System.out.println(name + " is teaching " + specialization);
        }
    }

    // -------- Inner Class: Student --------
    public class Student {
        private String name;
        private int year;
        private List<Grade> grades = new ArrayList<>();

        public Student(String name, int year) {
            this.name = name;
            this.year = year;
        }

        public void addGrade(String course, double score) {
            grades.add(new Grade(course, score));
        }

        public String getName() {
            return name;
        }

        public int getYear() {
            return year;
        }

        public List<Grade> getGrades() {
            return grades;
        }

        // Nested Inner Class: Grade
        public class Grade {
            private String course;
            private double score;

            public Grade(String course, double score) {
                this.course = course;
                this.score = score;
            }

            public String getCourse() {
                return course;
            }

            public double getScore() {
                return score;
            }

            @Override
            public String toString() {
                return course + ": " + score;
            }
        }
    }

    // -------- Runner --------
    public static void main(String[] args) {
        University uni = new University("Global Tech University", "Kampala");

        // Create professors
        Professor prof1 = uni.new Professor("Dr. Smith", "Quantum Computing");
        Professor prof2 = uni.new Professor("Dr. Lee", "AI & Machine Learning");

        // Create a department
        Department csDept = uni.new Department("Computer Science", prof1);
        csDept.addProfessor(prof1);
        csDept.addProfessor(prof2);

        // Create students
        Student student1 = uni.new Student("Alice", 2);
        Student student2 = uni.new Student("Bob", 3);

        // Add grades
        student1.addGrade("Quantum Mechanics", 88.5);
        student1.addGrade("Algorithms", 91.0);

        student2.addGrade("Machine Learning", 85.0);
        student2.addGrade("Databases", 79.5);

        // Add students to dept
        csDept.addStudent(student1);
        csDept.addStudent(student2);

        // Add dept to university
        uni.addDepartment(csDept);

        // Display data
        System.out.println("University: " + uni.getName() + " (" + uni.getLocation() + ")");
        for (Department d : uni.getDepartments()) {
            System.out.println("\nDepartment: " + d.getDeptName());
            System.out.println("Head: " + d.getHead().getName());

            System.out.println("\nProfessors:");
            for (Professor p : d.getProfessors()) {
                System.out.println("- " + p.getName() + " (" + p.getSpecialization() + ")");
            }

            System.out.println("\nStudents:");
            for (Student s : d.getStudents()) {
                System.out.println("- " + s.getName() + " (Year " + s.getYear() + ")");
                for (Student.Grade g : s.getGrades()) {
                    System.out.println("   * " + g);
                }
            }
        }
    }
}
