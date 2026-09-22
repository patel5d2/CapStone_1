package com.jonathansoriano.enterprisedevgroupproject.profile;

import com.jonathansoriano.enterprisedevgroupproject.profile.dto.ProfileVisibility;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "profile_privacy")
@Getter
@Setter
@NoArgsConstructor
public class ProfilePrivacy {
    @Id private Long studentId;
    private boolean showMajor;
    private boolean showGraduationYear;
    private boolean showBio;
    private boolean showPhoto;

    public ProfileVisibility visibility() {
        return new ProfileVisibility(showMajor, showGraduationYear, showBio, showPhoto);
    }

    public void apply(ProfileVisibility value) {
        showMajor = value.showMajor();
        showGraduationYear = value.showGraduationYear();
        showBio = value.showBio();
        showPhoto = value.showPhoto();
    }
}
