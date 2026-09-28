package com.jonathansoriano.enterprisedevgroupproject.profile.dto;

/** Missing preferences fail closed. Contact details are never a visibility option. */
public record ProfileVisibility(boolean showMajor, boolean showGraduationYear,
                                boolean showBio, boolean showPhoto) {
    public static ProfileVisibility privateByDefault() {
        return new ProfileVisibility(false, false, false, false);
    }
}
