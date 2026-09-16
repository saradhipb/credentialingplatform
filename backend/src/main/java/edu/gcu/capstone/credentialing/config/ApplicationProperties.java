package edu.gcu.capstone.credentialing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ApplicationProperties.java
 *
 * <p>Type-safe binding of every {@code app.*} key in application.yml. Collecting
 * configuration in one object keeps API endpoints, file paths, model
 * hyper-parameters and risk weights out of the business classes, so the same
 * build can be deployed to different environments by changing configuration
 * only.</p>
 */
@ConfigurationProperties(prefix = "app")
public class ApplicationProperties {

    private Storage storage = new Storage();
    private Ocr ocr = new Ocr();
    private Nppes nppes = new Nppes();
    private Oig oig = new Oig();
    private Ml ml = new Ml();
    private Risk risk = new Risk();
    private Security security = new Security();

    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }

    public Ocr getOcr() { return ocr; }
    public void setOcr(Ocr ocr) { this.ocr = ocr; }

    public Nppes getNppes() { return nppes; }
    public void setNppes(Nppes nppes) { this.nppes = nppes; }

    public Oig getOig() { return oig; }
    public void setOig(Oig oig) { this.oig = oig; }

    public Ml getMl() { return ml; }
    public void setMl(Ml ml) { this.ml = ml; }

    public Risk getRisk() { return risk; }
    public void setRisk(Risk risk) { this.risk = risk; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    /** Where uploaded documents are written and how long they are kept. */
    public static class Storage {
        private String uploadDir = "./uploads";
        private int retentionDays = 7;

        public String getUploadDir() { return uploadDir; }
        public void setUploadDir(String uploadDir) { this.uploadDir = uploadDir; }
        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    }

    /** Optical character recognition engine settings. */
    public static class Ocr {
        private String tesseractCommand = "tesseract";
        private String language = "eng";
        private int timeoutSeconds = 60;
        private boolean failOnMissingEngine = false;

        public String getTesseractCommand() { return tesseractCommand; }
        public void setTesseractCommand(String tesseractCommand) { this.tesseractCommand = tesseractCommand; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public boolean isFailOnMissingEngine() { return failOnMissingEngine; }
        public void setFailOnMissingEngine(boolean failOnMissingEngine) { this.failOnMissingEngine = failOnMissingEngine; }
    }

    /** CMS NPPES NPI Registry API settings. */
    public static class Nppes {
        private String baseUrl = "https://npiregistry.cms.hhs.gov/api/";
        private String version = "2.1";
        private boolean enabled = true;
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 8000;
        private int maxRetries = 2;
        private String offlineDataset = "classpath:data/nppes-offline-snapshot.json";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        public String getOfflineDataset() { return offlineDataset; }
        public void setOfflineDataset(String offlineDataset) { this.offlineDataset = offlineDataset; }
    }

    /** HHS OIG List of Excluded Individuals/Entities settings. */
    public static class Oig {
        private String leieFile = "classpath:data/oig-leie-sample.csv";
        private double nameMatchThreshold = 0.92;

        public String getLeieFile() { return leieFile; }
        public void setLeieFile(String leieFile) { this.leieFile = leieFile; }
        public double getNameMatchThreshold() { return nameMatchThreshold; }
        public void setNameMatchThreshold(double nameMatchThreshold) { this.nameMatchThreshold = nameMatchThreshold; }
    }

    /** Isolation Forest hyper-parameters. */
    public static class Ml {
        private int trees = 120;
        private int sampleSize = 128;
        private long randomSeed = 42L;
        private double anomalyThreshold = 0.60;

        public int getTrees() { return trees; }
        public void setTrees(int trees) { this.trees = trees; }
        public int getSampleSize() { return sampleSize; }
        public void setSampleSize(int sampleSize) { this.sampleSize = sampleSize; }
        public long getRandomSeed() { return randomSeed; }
        public void setRandomSeed(long randomSeed) { this.randomSeed = randomSeed; }
        public double getAnomalyThreshold() { return anomalyThreshold; }
        public void setAnomalyThreshold(double anomalyThreshold) { this.anomalyThreshold = anomalyThreshold; }
    }

    /** Weights and decision thresholds used by the risk scoring engine. */
    public static class Risk {
        private double weightExclusion = 0.40;
        private double weightNpi = 0.25;
        private double weightDiscrepancy = 0.20;
        private double weightAnomaly = 0.15;
        private int reviewThreshold = 40;
        private int rejectThreshold = 70;

        public double getWeightExclusion() { return weightExclusion; }
        public void setWeightExclusion(double weightExclusion) { this.weightExclusion = weightExclusion; }
        public double getWeightNpi() { return weightNpi; }
        public void setWeightNpi(double weightNpi) { this.weightNpi = weightNpi; }
        public double getWeightDiscrepancy() { return weightDiscrepancy; }
        public void setWeightDiscrepancy(double weightDiscrepancy) { this.weightDiscrepancy = weightDiscrepancy; }
        public double getWeightAnomaly() { return weightAnomaly; }
        public void setWeightAnomaly(double weightAnomaly) { this.weightAnomaly = weightAnomaly; }
        public int getReviewThreshold() { return reviewThreshold; }
        public void setReviewThreshold(int reviewThreshold) { this.reviewThreshold = reviewThreshold; }
        public int getRejectThreshold() { return rejectThreshold; }
        public void setRejectThreshold(int rejectThreshold) { this.rejectThreshold = rejectThreshold; }
    }

    /** Session and demonstration-account settings. */
    public static class Security {
        private int sessionTimeoutMinutes = 30;
        private boolean seedDemoUsers = true;

        public int getSessionTimeoutMinutes() { return sessionTimeoutMinutes; }
        public void setSessionTimeoutMinutes(int sessionTimeoutMinutes) { this.sessionTimeoutMinutes = sessionTimeoutMinutes; }
        public boolean isSeedDemoUsers() { return seedDemoUsers; }
        public void setSeedDemoUsers(boolean seedDemoUsers) { this.seedDemoUsers = seedDemoUsers; }
    }
}
