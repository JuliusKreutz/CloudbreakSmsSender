package dev.kreutz.cloudbreaksmssendershared;

import java.io.Serializable;

public class SendSmsRequest  implements Serializable {
    private final String number;
    private final String text;

    public SendSmsRequest(String number, String text) {
        this.number = number;
        this.text = text;
    }

    public String getNumber() {
        return number;
    }

    public String getText() {
        return text;
    }
}
