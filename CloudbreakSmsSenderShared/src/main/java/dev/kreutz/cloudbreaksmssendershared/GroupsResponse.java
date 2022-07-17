package dev.kreutz.cloudbreaksmssendershared;

import java.io.Serializable;
import java.util.Set;

public class GroupsResponse  implements Serializable {
    private final Set<String> groups;

    public GroupsResponse(Set<String> groups) {
        this.groups = groups;
    }

    public Set<String> getGroups() {
        return groups;
    }
}
