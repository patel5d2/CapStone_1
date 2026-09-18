package com.jonathansoriano.enterprisedevgroupproject.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
//This is the class that will be used to display student information to the UI
public class Student {

    /**
     * The directory row's stable identifier.
     *
     * <p>Added 2026-09-18. Until now this response carried no id at all, so the SPA had
     * nothing to key a list on but the student's email address, and anything that wanted
     * to refer to a student — starting a conversation, replacing `mailto:` with an
     * in-platform action — had to use the address too. That made the contact details in
     * this payload load-bearing, which is the opposite of what invariant 4 wants.
     *
     * <p>It is deliberately decision-neutral: {@code id} is needed whichever way
     * D-DIRECTORY (#52) decides the email question, and adding it removes nothing. The
     * removal of the address itself waits for that vote. See
     * {@code docs/phase-1/privacy-audit.md} §3.
     *
     * <p>The value is the existing surrogate key, already selected by every directory
     * query. Not the Clerk subject: that identifies the account to Clerk and does not
     * belong in a payload other students receive.
     */
    private Long id;

    //Got rid of studentID
    private String firstName;
    private String lastName;
    private String residentCity;
    private String residentState;
    private String universityName;
    private String grade;
    private String major;
    private String email;
    private String socialMediaLink;
}
