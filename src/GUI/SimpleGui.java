package GUI;

import javax.swing.*;
import java.awt.*;

public class SimpleGui {
    public static void main(String[] args) {
        JFrame frame = new JFrame();
        JButton button = new JButton("Play");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(button, BorderLayout.NORTH);
        frame.setSize(400, 400);
        frame.setVisible(true);
    }
}
