package com.yego.contractortracker.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MilestoneProgressService {
    
    private final Map<String, ProgressInfo> progressMap = new ConcurrentHashMap<>();
    
    public static class ProgressInfo {
        private String status;
        private int totalDrivers;
        private int processedDrivers;
        private int milestone1Count;
        private int milestone5Count;
        private int milestone25Count;
        private String periodType;
        private String error;
        
        public ProgressInfo() {
            this.status = "pending";
        }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public int getTotalDrivers() { return totalDrivers; }
        public void setTotalDrivers(int totalDrivers) { this.totalDrivers = totalDrivers; }
        
        public int getProcessedDrivers() { return processedDrivers; }
        public void setProcessedDrivers(int processedDrivers) { this.processedDrivers = processedDrivers; }
        
        public int getMilestone1Count() { return milestone1Count; }
        public void setMilestone1Count(int milestone1Count) { this.milestone1Count = milestone1Count; }
        
        public int getMilestone5Count() { return milestone5Count; }
        public void setMilestone5Count(int milestone5Count) { this.milestone5Count = milestone5Count; }
        
        public int getMilestone25Count() { return milestone25Count; }
        public void setMilestone25Count(int milestone25Count) { this.milestone25Count = milestone25Count; }
        
        public String getPeriodType() { return periodType; }
        public void setPeriodType(String periodType) { this.periodType = periodType; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        
        public int getProgressPercentage() {
            if (totalDrivers == 0) return 0;
            return (int) ((processedDrivers * 100.0) / totalDrivers);
        }
    }
    
    public void startProgress(String jobId, String periodType, int totalDrivers) {
        ProgressInfo info = new ProgressInfo();
        info.setStatus("running");
        info.setPeriodType(periodType);
        info.setTotalDrivers(totalDrivers);
        info.setProcessedDrivers(0);
        progressMap.put(jobId, info);
    }
    
    public void updateProgress(String jobId, int processed, int milestone1, int milestone5, int milestone25) {
        ProgressInfo info = progressMap.get(jobId);
        if (info != null) {
            info.setProcessedDrivers(processed);
            info.setMilestone1Count(milestone1);
            info.setMilestone5Count(milestone5);
            info.setMilestone25Count(milestone25);
        }
    }
    
    public void completeProgress(String jobId) {
        ProgressInfo info = progressMap.get(jobId);
        if (info != null) {
            info.setStatus("completed");
        }
    }
    
    public void failProgress(String jobId, String error) {
        ProgressInfo info = progressMap.get(jobId);
        if (info != null) {
            info.setStatus("failed");
            info.setError(error);
        }
    }
    
    public ProgressInfo getProgress(String jobId) {
        return progressMap.get(jobId);
    }
    
    public void clearProgress(String jobId) {
        progressMap.remove(jobId);
    }
}











