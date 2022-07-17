package dev.kreutz.cloudbreaksmssendershared;

import java.io.Serializable;
import java.util.Set;

public class NumbersResponse implements Serializable {
    private final Set<String> numbers;

    public NumbersResponse(Set<String> numbers) {
        this.numbers = numbers;
    }

    public Set<String> getNumbers() {
        return numbers;
    }
}
