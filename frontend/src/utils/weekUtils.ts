function getWeekNumber(date: Date): number {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  d.setUTCDate(d.getUTCDate() + 4 - (d.getUTCDay() || 7));
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  return Math.ceil((((d.getTime() - yearStart.getTime()) / 86400000) + 1) / 7);
}

function getWeekYear(date: Date): number {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  d.setUTCDate(d.getUTCDate() + 4 - (d.getUTCDay() || 7));
  return d.getUTCFullYear();
}

export function getCurrentWeekISO(): string {
  const now = new Date();
  const year = getWeekYear(now);
  const week = getWeekNumber(now);
  return `${year}-W${String(week).padStart(2, '0')}`;
}

export function getWeekRange(weekISO: string): { start: Date; end: Date } {
  const [yearStr, weekStr] = weekISO.split('-W');
  const year = parseInt(yearStr, 10);
  const week = parseInt(weekStr, 10);
  
  const jan4 = new Date(Date.UTC(year, 0, 4));
  const jan4Day = jan4.getUTCDay() || 7;
  const week1Start = new Date(Date.UTC(year, 0, 4 - jan4Day + 1));
  
  let weekStart = new Date(week1Start);
  weekStart.setUTCDate(week1Start.getUTCDate() + (week - 1) * 7);
  
  const calculatedYear = getWeekYear(weekStart);
  const calculatedWeek = getWeekNumber(weekStart);
  
  if (calculatedYear !== year || calculatedWeek !== week) {
    const midYear = new Date(Date.UTC(year, 5, 15));
    let dateInWeek = new Date(midYear);
    
    let currentWeekYear = getWeekYear(dateInWeek);
    let currentWeek = getWeekNumber(dateInWeek);
    
    const targetWeek = week;
    const targetYear = year;
    
    while (currentWeekYear !== targetYear || currentWeek !== targetWeek) {
      if (currentWeekYear < targetYear || (currentWeekYear === targetYear && currentWeek < targetWeek)) {
        dateInWeek.setUTCDate(dateInWeek.getUTCDate() + 7);
      } else {
        dateInWeek.setUTCDate(dateInWeek.getUTCDate() - 7);
      }
      currentWeekYear = getWeekYear(dateInWeek);
      currentWeek = getWeekNumber(dateInWeek);
    }
    
    const dayOfWeek = dateInWeek.getUTCDay() || 7;
    weekStart = new Date(dateInWeek);
    weekStart.setUTCDate(dateInWeek.getUTCDate() - (dayOfWeek - 1));
  }
  
  const weekEnd = new Date(weekStart);
  weekEnd.setUTCDate(weekStart.getUTCDate() + 6);
  weekEnd.setUTCHours(23, 59, 59, 999);
  
  return { start: weekStart, end: weekEnd };
}

export function getPreviousWeek(weekISO: string): string {
  const [yearStr, weekStr] = weekISO.split('-W');
  let year = parseInt(yearStr, 10);
  let week = parseInt(weekStr, 10);
  
  // Validar que la semana sea válida
  if (isNaN(year) || isNaN(week) || week < 1 || week > 53) {
    return getCurrentWeekISO();
  }
  
  week -= 1;
  if (week < 1) {
    year -= 1;
    // Calcular la última semana del año anterior
    // Usamos el 28 de diciembre como punto de referencia para obtener la última semana
    const dec28 = new Date(Date.UTC(year, 11, 28));
    const lastWeekYear = getWeekYear(dec28);
    const lastWeek = getWeekNumber(dec28);
    
    // Si el año calculado es diferente, usar el último día del año
    if (lastWeekYear !== year) {
      const lastDayOfYear = new Date(Date.UTC(year, 11, 31));
      const finalWeekYear = getWeekYear(lastDayOfYear);
      const finalWeek = getWeekNumber(lastDayOfYear);
      year = finalWeekYear;
      week = finalWeek;
    } else {
      year = lastWeekYear;
      week = lastWeek;
    }
  }
  
  return `${year}-W${String(week).padStart(2, '0')}`;
}

export function getNextWeek(weekISO: string): string {
  const [yearStr, weekStr] = weekISO.split('-W');
  let year = parseInt(yearStr, 10);
  let week = parseInt(weekStr, 10);
  
  // Validar que la semana sea válida
  if (isNaN(year) || isNaN(week) || week < 1 || week > 53) {
    return getCurrentWeekISO();
  }
  
  week += 1;
  const lastDayOfYear = new Date(Date.UTC(year, 11, 31));
  const maxWeek = getWeekNumber(lastDayOfYear);
  
  if (week > maxWeek) {
    year += 1;
    week = 1;
  }
  
  return `${year}-W${String(week).padStart(2, '0')}`;
}

export function formatWeekISO(weekISO: string): string {
  const [yearStr, weekStr] = weekISO.split('-W');
  const week = parseInt(weekStr, 10);
  return `${yearStr} - Semana ${week}`;
}

export function formatWeekRange(weekISO: string): string {
  const { start, end } = getWeekRange(weekISO);
  const startStr = start.toLocaleDateString('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric' });
  const endStr = end.toLocaleDateString('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric' });
  return `${startStr} - ${endStr}`;
}

export function getLastNWeeks(n: number): string[] {
  const weeks: string[] = [];
  let currentWeek = getCurrentWeekISO();
  
  for (let i = 0; i < n; i++) {
    weeks.push(currentWeek);
    currentWeek = getPreviousWeek(currentWeek);
  }
  
  return weeks.reverse();
}

