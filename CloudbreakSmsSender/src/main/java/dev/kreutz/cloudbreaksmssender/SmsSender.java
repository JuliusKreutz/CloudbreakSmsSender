package dev.kreutz.cloudbreaksmssender;

import com.formdev.flatlaf.FlatLightLaf;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import dev.kreutz.cloudbreaksmssendershared.Const;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * Main class with all the logic and ui
 */
public class SmsSender extends JFrame {

    private final Semaphore semaphore = new Semaphore(1);

    private JPanel panel;
    private JButton sendButton;
    private JTextArea textArea;
    private JComboBox<Phone> phoneDropdown;
    private JButton refreshButton;
    private JPanel groupPanel;
    private JButton clearButton;
    private JLabel icon;

    /**
     * A list of the current connected phones
     */
    private final List<Phone> phones = new ArrayList<>(4);

    /**
     * Initializes ui and starts listening to connections
     *
     * @see #listenToConnections()
     */
    private SmsSender() {
        try {
            BufferedImage image = ImageIO.read(Objects.requireNonNull(getClass().getClassLoader().getResource("icon.png")));
            setIconImage(image);

            int factor = 10;
            int width = image.getWidth() / factor;
            int height = image.getHeight() / factor;

            icon.setIcon(new ImageIcon(image.getScaledInstance(width, height, Image.SCALE_SMOOTH)));
        } catch (IOException ignored) {
        }


        setContentPane(panel);
        setResizable(false);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        actionListeners();
        setLocationByPlatform(true);

        pack();
        setVisible(true);

        groupPanel.setLayout(new BoxLayout(groupPanel, BoxLayout.Y_AXIS));

        new Thread(this::listenToConnections).start();

        textArea.requestFocus();
    }

    /**
     * Sets action listeners for button
     */
    private void actionListeners() {
        phoneDropdown.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                selectPhone();
            }
        });
        refreshButton.addActionListener(e -> refreshPhones());
        sendButton.addActionListener(e -> {
            sendButton.setEnabled(false);
            SwingUtilities.invokeLater(() -> new Thread(this::sendSms).start());
        });
        clearButton.addActionListener(e -> clearGroups());
    }

    /**
     * Listen to tcp connections<br>
     * When connected update phone list and display new phone in dropdown
     */
    private void listenToConnections() {
        try (ServerSocket serverSocket = new ServerSocket(Const.TCP_PORT)) {
            refreshPhones();

            while (!Thread.interrupted()) {
                try {
                    Socket socket = serverSocket.accept();
                    Phone phone = new Phone(socket);

                    semaphore.acquire();
                    phones.add(phone);
                    phoneDropdown.addItem(phone);
                    semaphore.release();
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * Select the next working phone and add the groups
     */
    private void selectPhone() {
        Phone phone = (Phone) phoneDropdown.getSelectedItem();

        if (phone == null)
            return;

        sendButton.setEnabled(phone.isReady());

        Set<String> groups = phone.getGroups();

        groupPanel.removeAll();
        for (String group : groups) {
            groupPanel.add(new Checkbox(group));
        }
        pack();
    }

    /**
     * Removes all dead phone from phone list and dropdown<br>
     * Resends the multicast package
     */
    private void refreshPhones() {
        groupPanel.removeAll();

        try {
            semaphore.acquire();
        } catch (InterruptedException ignored) {
        }

        phoneDropdown.removeAllItems();
        phones.stream().filter(Phone::refresh).forEach(phoneDropdown::addItem);
        semaphore.release();

        pack();

        for (; ; ) {
            try (DatagramSocket socket = new DatagramSocket()) {
                InetAddress address = InetAddress.getByName(Const.MULTICAST_ADDRESS);
                DatagramPacket packet = new DatagramPacket(new byte[0], 0, address, Const.MULTICAST_PORT);
                socket.send(packet);
                break;
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Make the selected phone send a sms to all the selected groups
     */
    private void sendSms() {
        Phone phone = (Phone) phoneDropdown.getSelectedItem();

        if (phone == null) {
            refreshPhones();
            sendButton.setEnabled(true);
            return;
        }

        phone.setReady(false);

        Set<String> groups = Arrays.stream(groupPanel.getComponents()).filter(c -> c instanceof Checkbox).map(c -> (Checkbox) c).filter(Checkbox::getState).map(Checkbox::getLabel).collect(Collectors.toSet());
        Set<String> numbers = phone.getNumbers(groups).stream().map(n -> n.replaceAll("\\s+", ""))
                .map(n -> {
                    if (n.startsWith("04")) {
                        return n.replaceFirst("0", "+61");
                    }

                    return n;
                }).collect(Collectors.toSet());

        if (numbers.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No numbers found. Maybe refresh?", "Error", JOptionPane.ERROR_MESSAGE);
            phone.setReady(true);
            sendButton.setEnabled(true);
            return;
        }

        SmsSenderLog log = new SmsSenderLog(this);
        int i = 1;
        for (String number : numbers) {
            log.setLabelText("Sending sms " + i++ + "/" + numbers.size() + " to phone " + phone.getName());

            long start = System.currentTimeMillis();
            boolean success = phone.sendSms(number, textArea.getText());
            long time = System.currentTimeMillis() - start;

            String message = "Sent sms to " + number + " in " + time + "ms";

            if (success) {
                log.success(message);
            } else {
                log.error(message);
            }
        }

        log.setLabelText("Finished sending " + numbers.size() + " sms to phone " + phone.getName());

        log.waitForFinish();
        phone.setReady(true);

        phone = (Phone) phoneDropdown.getSelectedItem();

        if (phone != null)
            sendButton.setEnabled(phone.isReady());
    }

    /**
     * Reset the group selection
     */
    private void clearGroups() {
        Arrays.stream(groupPanel.getComponents()).filter(c -> c instanceof Checkbox).map(c -> (Checkbox) c).forEach(c -> c.setState(false));
    }

    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(SmsSender::new);
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        panel = new JPanel();
        panel.setLayout(new GridLayoutManager(4, 3, new Insets(10, 10, 10, 10), -1, -1));
        sendButton = new JButton();
        sendButton.setText("Send");
        panel.add(sendButton, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        scrollPane1.setHorizontalScrollBarPolicy(31);
        scrollPane1.setVerticalScrollBarPolicy(22);
        panel.add(scrollPane1, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(-1, 500), null, 0, false));
        groupPanel = new JPanel();
        groupPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        scrollPane1.setViewportView(groupPanel);
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel.add(panel1, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        phoneDropdown = new JComboBox();
        panel1.add(phoneDropdown, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        refreshButton = new JButton();
        refreshButton.setText("⟳");
        panel1.add(refreshButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Groups");
        panel.add(label1, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        clearButton = new JButton();
        clearButton.setText("Clear");
        panel.add(clearButton, new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane2 = new JScrollPane();
        panel.add(scrollPane2, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(300, 500), null, 0, false));
        textArea = new JTextArea();
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(false);
        scrollPane2.setViewportView(textArea);
        final JLabel label2 = new JLabel();
        Font label2Font = this.$$$getFont$$$(null, Font.BOLD, -1, label2.getFont());
        if (label2Font != null) label2.setFont(label2Font);
        label2.setText("CloudBreak SMS Sender");
        panel.add(label2, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        icon = new JLabel();
        icon.setText("");
        panel.add(icon, new GridConstraints(0, 0, 2, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                resultName = fontName;
            } else {
                resultName = currentFont.getName();
            }
        }
        Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
        boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
        Font fontWithFallback = isMac ? new Font(font.getFamily(), font.getStyle(), font.getSize()) : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
        return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return panel;
    }

}
