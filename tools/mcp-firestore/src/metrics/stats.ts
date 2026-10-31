export function calculateAvg(values: number[]): number {
  if (values.length === 0) return 0;
  const sum = values.reduce((acc, v) => acc + v, 0);
  return Math.round((sum / values.length) * 10) / 10;
}

export function calculateP90(sortedValues: number[]): number {
  if (sortedValues.length === 0) return 0;
  const index = Math.ceil(0.9 * sortedValues.length) - 1;
  const val = sortedValues[Math.max(0, Math.min(index, sortedValues.length - 1))];
  return Math.round(val * 10) / 10;
}

export function calculateStdDev(values: number[]): { mean: number; stdDev: number } {
  if (values.length === 0) return { mean: 0, stdDev: 0 };
  const mean = values.reduce((acc, v) => acc + v, 0) / values.length;
  const variance =
    values.reduce((acc, v) => acc + Math.pow(v - mean, 2), 0) / values.length;
  return {
    mean: Math.round(mean * 10) / 10,
    stdDev: Math.round(Math.sqrt(variance) * 10) / 10,
  };
}
