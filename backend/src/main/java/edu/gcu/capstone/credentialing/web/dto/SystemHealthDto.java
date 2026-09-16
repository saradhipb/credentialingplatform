package edu.gcu.capstone.credentialing.web.dto;

import java.util.List;

/**
 * SystemHealthDto.java
 *
 * <p>Integration and model health, shown on the Administrator Control Panel. It
 * exists so that an operator can tell at a glance whether a verification result
 * reflects a live registry lookup or a fallback, which changes how much weight
 * that result should carry.</p>
 *
 * @param nppesEnabled          whether live registry lookups are switched on
 * @param nppesReachable        whether the most recent live lookup succeeded
 * @param nppesLastError        description of the most recent failure, or null
 * @param offlineSnapshotSize   records held in the bundled registry snapshot
 * @param exclusionListLoaded   whether the OIG list loaded at start-up
 * @param exclusionRecordCount  records held in the exclusion list
 * @param ocrEngines            availability of each extraction engine
 * @param modelVersion          identifier of the trained anomaly model
 * @param anomalyThreshold      the configured decision threshold
 * @param precision             measured precision on the evaluation set
 * @param recall                measured recall on the evaluation set
 * @param f1Score               measured F1 on the evaluation set
 * @param accuracy              measured accuracy on the evaluation set
 * @param evaluationSampleCount size of the evaluation set
 * @param uploadDirectory       where documents are stored
 * @param retentionDays         how long stored binaries are kept
 */
public record SystemHealthDto(boolean nppesEnabled, boolean nppesReachable, String nppesLastError,
                              int offlineSnapshotSize, boolean exclusionListLoaded,
                              int exclusionRecordCount, List<String> ocrEngines,
                              String modelVersion, double anomalyThreshold,
                              double precision, double recall, double f1Score, double accuracy,
                              int evaluationSampleCount, String uploadDirectory, int retentionDays) {
}
