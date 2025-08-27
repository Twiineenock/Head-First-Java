package GUI;

import javax.swing.*;
import java.awt.*;

public class Button1 {
    public static void main(String[] args) {
        new Button1().go();
    }
    public void go() {
        JFrame frame = new JFrame();
        JButton button = new JButton("Click Me that shit boy, ....");
        frame.getContentPane().add(BorderLayout.EAST, button);
        frame.setSize(400,400);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }
}
