package GUI;

import java.util.*;

public class LibraryManagementSystem {

    private String libraryName;
    private String location;
    private List<Book> books;
    private List<Member> members;
    private List<Librarian> librarians;

    public LibraryManagementSystem(String libraryName, String location) {
        this.libraryName = libraryName;
        this.location = location;
        this.books = new ArrayList<>();
        this.members = new ArrayList<>();
        this.librarians = new ArrayList<>();
    }

    // Add entities
    public void addBook(Book book) {
        books.add(book);
    }

    public void addMember(Member member) {
        members.add(member);
    }

    public void addLibrarian(Librarian librarian) {
        librarians.add(librarian);
    }

    // Search for a book by title
    public Book findBookByTitle(String title) {
        for (Book book : books) {
            if (book.getTitle().equalsIgnoreCase(title)) {
                return book;
            }
        }
        return null;
    }

    // Borrow a book
    public boolean borrowBook(String title, Member member) {
        Book book = findBookByTitle(title);
        if (book != null && book.isAvailable()) {
            book.setAvailable(false);
            member.borrowedBooks.add(book);
            return true;
        }
        return false;
    }

    // Return a book
    public boolean returnBook(String title, Member member) {
        for (Book book : member.borrowedBooks) {
            if (book.getTitle().equalsIgnoreCase(title)) {
                book.setAvailable(true);
                member.borrowedBooks.remove(book);
                return true;
            }
        }
        return false;
    }

    // Inner class: Book
    public static class Book {
        private String title;
        private String author;
        private String isbn;
        private boolean available;

        public Book(String title, String author, String isbn) {
            this.title = title;
            this.author = author;
            this.isbn = isbn;
            this.available = true;
        }

        public String getTitle() {
            return title;
        }

        public boolean isAvailable() {
            return available;
        }

        public void setAvailable(boolean available) {
            this.available = available;
        }

        @Override
        public String toString() {
            return title + " by " + author + " (ISBN: " + isbn + ")";
        }
    }

    // Inner class: Member
    public static class Member {
        private String name;
        private String memberId;
        private List<Book> borrowedBooks;

        public Member(String name, String memberId) {
            this.name = name;
            this.memberId = memberId;
            this.borrowedBooks = new ArrayList<>();
        }

        @Override
        public String toString() {
            return name + " (ID: " + memberId + ")";
        }
    }

    // Inner class: Librarian
    public static class Librarian {
        private String name;
        private String employeeId;

        public Librarian(String name, String employeeId) {
            this.name = name;
            this.employeeId = employeeId;
        }

        public void issueBook(LibraryManagementSystem library, String title, Member member) {
            if (library.borrowBook(title, member)) {
                System.out.println("Book issued successfully.");
            } else {
                System.out.println("Book not available.");
            }
        }

        public void receiveBook(LibraryManagementSystem library, String title, Member member) {
            if (library.returnBook(title, member)) {
                System.out.println("Book returned successfully.");
            } else {
                System.out.println("Error: Book not found in member's borrowed list.");
            }
        }

        @Override
        public String toString() {
            return name + " (Employee ID: " + employeeId + ")";
        }
    }

    public static void main(String[] args) {
        LibraryManagementSystem library = new LibraryManagementSystem("City Library", "Main Street");

        Book b1 = new Book("1984", "George Orwell", "ISBN001");
        Book b2 = new Book("Brave New World", "Aldous Huxley", "ISBN002");

        Member m1 = new Member("Alice", "M001");
        Librarian l1 = new Librarian("Mr. Smith", "E001");

        library.addBook(b1);
        library.addBook(b2);
        library.addMember(m1);
        library.addLibrarian(l1);

        l1.issueBook(library, "1984", m1);
        l1.receiveBook(library, "1984", m1);
    }
}

