package com.jonathansoriano.enterprisedevgroupproject.seed;

import com.jonathansoriano.enterprisedevgroupproject.school.School;
import com.jonathansoriano.enterprisedevgroupproject.school.SchoolRepository;
import com.jonathansoriano.enterprisedevgroupproject.support.SupportCategory;
import com.jonathansoriano.enterprisedevgroupproject.support.SupportResource;
import com.jonathansoriano.enterprisedevgroupproject.support.SupportResourceRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Seeds one food pantry, emergency aid contact, and counseling center per
 * Cincinnati-area school on first startup, keyed off the school names the
 * student directory already carries in the {@code university} table. Runs
 * every startup but is a no-op once {@code support_resource} has rows, so it
 * never overwrites anything a real admin has since edited.
 */
@Component
public class SupportResourceSeeder implements CommandLineRunner {

    private static final Map<String, String[]> RESOURCES_BY_SCHOOL = Map.of(
            "University of Cincinnati", new String[]{"UC Bearcats Pantry", "UC Student Emergency Fund", "UC Counseling and Psychological Services"},
            "Northern Kentucky University", new String[]{"NKU Norse Pantry", "NKU Student Emergency Fund", "NKU Health, Counseling and Prevention Services"},
            "Xavier University", new String[]{"Xavier Food Pantry", "Xavier Student Emergency Aid", "Xavier Psychological Services Center"},
            "Miami University", new String[]{"Miami Swipe It Forward Pantry", "Miami Student Emergency Fund", "Miami Student Counseling Service"},
            "Cincinnati State Technical and Community College", new String[]{"Cincinnati State Campus Cupboard", "Cincinnati State Student Emergency Fund", "Cincinnati State Counseling Services"},
            "Mount St. Joseph University", new String[]{"Mount St. Joseph Lion's Pantry", "Mount St. Joseph Student Emergency Fund", "Mount St. Joseph Counseling Center"}
    );

    private final SchoolRepository schoolRepository;
    private final SupportResourceRepository supportResourceRepository;

    public SupportResourceSeeder(SchoolRepository schoolRepository, SupportResourceRepository supportResourceRepository) {
        this.schoolRepository = schoolRepository;
        this.supportResourceRepository = supportResourceRepository;
    }

    @Override
    public void run(String... args) {
        if (supportResourceRepository.count() > 0) {
            return;
        }

        List<School> schools = schoolRepository.findAll();
        for (School school : schools) {
            String[] names = RESOURCES_BY_SCHOOL.get(school.getName());
            if (names == null) {
                continue;
            }

            supportResourceRepository.save(SupportResource.builder()
                    .schoolId(school.getId())
                    .category(SupportCategory.FOOD_PANTRY)
                    .name(names[0])
                    .description("Free groceries and toiletries for students, no questions asked.")
                    .contactInfo("Visit the student affairs office for hours")
                    .build());

            supportResourceRepository.save(SupportResource.builder()
                    .schoolId(school.getId())
                    .category(SupportCategory.EMERGENCY_AID)
                    .name(names[1])
                    .description("One-time emergency grants for housing, medical, and other urgent costs.")
                    .contactInfo("Apply through the dean of students office")
                    .build());

            supportResourceRepository.save(SupportResource.builder()
                    .schoolId(school.getId())
                    .category(SupportCategory.COUNSELING)
                    .name(names[2])
                    .description("Free, confidential counseling for enrolled students.")
                    .contactInfo("Call the campus counseling center to schedule")
                    .build());
        }
    }
}
