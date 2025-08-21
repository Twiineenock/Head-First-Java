package GUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SimpleGui2 implements ActionListener {
    private JButton  button;
    public static void main(String[] args) {
        SimpleGui2 simpleGui2 = new SimpleGui2();
        simpleGui2.go();
    }

    public void go() {
        JFrame frame = new JFrame();
        button = new JButton("Click Me");
        button.addActionListener(this);
        frame.getContentPane().add(button, BorderLayout.NORTH);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(400, 400);
        frame.setVisible(true);
    }

    public void actionPerformed(ActionEvent e) {
        button.setText("I've been clicked");
    }
}
