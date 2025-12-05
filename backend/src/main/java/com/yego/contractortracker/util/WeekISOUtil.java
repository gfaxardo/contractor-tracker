package com.yego.contractortracker.util;

import java.time.LocalDate;
import java.time.temporal.WeekFields;

public class WeekISOUtil {
    
    private static final WeekFields WEEK_FIELDS = WeekFields.ISO;
    
    public static LocalDate[] getWeekRange(String weekISO) {
        if (weekISO == null || weekISO.isEmpty()) {
            return null;
        }
        
        try {
            String[] parts = weekISO.split("-W");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Formato de semana ISO inválido: " + weekISO);
            }
            
            int weekBasedYear = Integer.parseInt(parts[0]);
            int week = Integer.parseInt(parts[1]);
            
            LocalDate jan4 = LocalDate.of(weekBasedYear, 1, 4);
            int dayOfWeek = jan4.getDayOfWeek().getValue();
            LocalDate week1Start = jan4.minusDays(dayOfWeek - 1);
            
            LocalDate weekStart = week1Start.plusWeeks(week - 1);
            LocalDate weekEnd = weekStart.plusDays(6);
            
            int calculatedYear = weekStart.get(WEEK_FIELDS.weekBasedYear());
            int calculatedWeek = weekStart.get(WEEK_FIELDS.weekOfWeekBasedYear());
            
            if (calculatedYear != weekBasedYear || calculatedWeek != week) {
                LocalDate testDate = LocalDate.of(weekBasedYear, 6, 15);
                LocalDate dateInTargetWeek = testDate
                    .with(WEEK_FIELDS.weekBasedYear(), weekBasedYear)
                    .with(WEEK_FIELDS.weekOfWeekBasedYear(), week);
                
                int finalYear = dateInTargetWeek.get(WEEK_FIELDS.weekBasedYear());
                int finalWeek = dateInTargetWeek.get(WEEK_FIELDS.weekOfWeekBasedYear());
                
                if (finalYear == weekBasedYear && finalWeek == week) {
                    weekStart = dateInTargetWeek.with(WEEK_FIELDS.dayOfWeek(), 1);
                    weekEnd = weekStart.plusDays(6);
                } else {
                    LocalDate lastDayOfYear = LocalDate.of(weekBasedYear, 12, 31);
                    int maxWeek = lastDayOfYear.get(WEEK_FIELDS.weekOfWeekBasedYear());
                    if (week > maxWeek) {
                        LocalDate nextYearJan4 = LocalDate.of(weekBasedYear + 1, 1, 4);
                        int nextJan4DayOfWeek = nextYearJan4.getDayOfWeek().getValue();
                        LocalDate nextYearWeek1Start = nextYearJan4.minusDays(nextJan4DayOfWeek - 1);
                        weekStart = nextYearWeek1Start.plusWeeks(week - 1);
                        weekEnd = weekStart.plusDays(6);
                    } else {
                        LocalDate firstDayOfYear = LocalDate.of(weekBasedYear, 1, 1);
                        for (int i = 0; i < 365; i++) {
                            LocalDate test = firstDayOfYear.plusDays(i);
                            if (test.get(WEEK_FIELDS.weekBasedYear()) == weekBasedYear && 
                                test.get(WEEK_FIELDS.weekOfWeekBasedYear()) == week) {
                                weekStart = test.with(WEEK_FIELDS.dayOfWeek(), 1);
                                weekEnd = weekStart.plusDays(6);
                                break;
                            }
                        }
                    }
                }
            }
            
            int verifyYear = weekStart.get(WEEK_FIELDS.weekBasedYear());
            int verifyWeek = weekStart.get(WEEK_FIELDS.weekOfWeekBasedYear());
            
            if (verifyYear != weekBasedYear || verifyWeek != week) {
                System.err.println("ADVERTENCIA: Semana ISO " + weekISO + " calculada incorrectamente. Esperado: " + weekBasedYear + "-W" + week + ", Obtenido: " + verifyYear + "-W" + verifyWeek);
            }
            
            System.out.println("Semana ISO " + weekISO + " -> Fechas: " + weekStart + " a " + weekEnd + " (Verificación: " + verifyYear + "-W" + verifyWeek + ")");
            
            return new LocalDate[]{weekStart, weekEnd};
        } catch (Exception e) {
            System.err.println("Error al calcular semana ISO " + weekISO + ": " + e.getMessage());
            e.printStackTrace();
            throw new IllegalArgumentException("Error al parsear semana ISO: " + weekISO, e);
        }
    }
    
    public static String getCurrentWeekISO() {
        LocalDate today = LocalDate.now();
        int year = today.get(WEEK_FIELDS.weekBasedYear());
        int week = today.get(WEEK_FIELDS.weekOfWeekBasedYear());
        return String.format("%04d-W%02d", year, week);
    }
}

