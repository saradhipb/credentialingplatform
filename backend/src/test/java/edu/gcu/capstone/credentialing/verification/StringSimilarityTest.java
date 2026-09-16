package edu.gcu.capstone.credentialing.verification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StringSimilarityTest.java
 *
 * <p>Module tests for the name-matching measure used in exclusion screening. The
 * behaviour that matters is the ordering: a near-miss spelling of the same name
 * must score above the configured 0.92 threshold, while two different names must
 * score below it.</p>
 */
class StringSimilarityTest {

    @Test
    @DisplayName("Identical strings score 1.0")
    void identicalStringsScoreOne() {
        assertThat(StringSimilarity.jaroWinkler("whitfield reginald", "whitfield reginald"))
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("A one-character misspelling still scores above the screening threshold")
    void nearMissScoresAboveThreshold() {
        double score = StringSimilarity.jaroWinkler("reginald whitfield", "reginald whitfeild");
        assertThat(score).isGreaterThan(0.92);
    }

    @Test
    @DisplayName("Unrelated names score below the screening threshold")
    void unrelatedNamesScoreBelowThreshold() {
        double score = StringSimilarity.jaroWinkler("reginald whitfield", "chidi okafor");
        assertThat(score).isLessThan(0.92);
    }

    @Test
    @DisplayName("Null and empty inputs score zero instead of failing")
    void handlesNullAndEmpty() {
        assertThat(StringSimilarity.jaroWinkler(null, "harper")).isZero();
        assertThat(StringSimilarity.jaroWinkler("harper", null)).isZero();
        assertThat(StringSimilarity.jaroWinkler("", "harper")).isZero();
    }

    @Test
    @DisplayName("Scores always fall within the range 0.0 to 1.0")
    void scoresStayInRange() {
        String[] samples = {"harper dana", "okafor chidi", "", "x", "lindqvist marta r"};
        for (String left : samples) {
            for (String right : samples) {
                double score = StringSimilarity.jaroWinkler(left, right);
                assertThat(score).isBetween(0.0, 1.0);
            }
        }
    }
}
