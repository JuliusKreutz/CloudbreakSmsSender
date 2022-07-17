package dev.kreutz.cloudbreaksmssender;

import dev.kreutz.cloudbreaksmssendershared.*;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Set;
import java.util.TreeSet;

/**
 * Simple wrapper for a phone connected
 */
public class Phone {

    /**
     * Number of the phone
     */
    private String name;

    /**
     * All the groups of the phone
     */
    private Set<String> groups;

    /**
     * OutputStream of the phone
     */
    private final ObjectOutputStream writer;

    /**
     * InputStream of the phone
     */
    private final ObjectInputStream reader;

    /**
     * If the phone is currently sending
     */
    private boolean ready = true;

    public Phone(Socket socket) throws Exception {
        writer = new ObjectOutputStream(socket.getOutputStream());
        reader = new ObjectInputStream(socket.getInputStream());

        writer.writeObject(new NameRequest());
        NameResponse nameResponse = (NameResponse) reader.readObject();
        name = nameResponse.getName();

        writer.writeObject(new GroupsRequest());
        GroupsResponse groupsResponse = (GroupsResponse) reader.readObject();
        groups = groupsResponse.getGroups();
    }

    public String getName() {
        return name;
    }

    public Set<String> getGroups() {
        return groups;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    /**
     * Refreshes the phones name
     *
     * @return True if successful
     */
    public boolean refresh() {
        try {
            writer.writeObject(new NameRequest());
            NameResponse nameResponse = (NameResponse) reader.readObject();
            name = nameResponse.getName();

            writer.writeObject(new GroupsRequest());
            GroupsResponse groupsResponse = (GroupsResponse) reader.readObject();
            groups = groupsResponse.getGroups();

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Set<String> getNumbers(Set<String> groups) {
        try {
            writer.writeObject(new NumbersRequest(groups));
            NumbersResponse numbersResponse = (NumbersResponse) reader.readObject();
            return numbersResponse.getNumbers();
        } catch (Exception e) {
            return new TreeSet<>();
        }
    }

    /**
     * Make phone send sms to number
     *
     * @param number The number to send the sms to
     * @param text   The text of the sms
     * @return true if succeeded
     */
    public boolean sendSms(String number, String text) {
        try {
            writer.writeObject(new SendSmsRequest(number, text));
            SendSmsResponse sendSmsResponse = (SendSmsResponse) reader.readObject();
            return sendSmsResponse.isOk();
        } catch (Exception e) {
            return false;
        }
    }


    @Override
    public String toString() {
        return name;
    }
}
