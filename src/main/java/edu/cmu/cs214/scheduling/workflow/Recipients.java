package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.Member;

/** Who the workflow's notifications go to. */
final class Recipients {

    static final String FACILITIES_CONTACT = "facilities@rooms.example.edu";

    private Recipients() {
    }

    static String recipientFor(Member member) {
        return member == null ? FACILITIES_CONTACT : member.getEmail();
    }
}
