export function formatCurrencyVND(amount: number): string {
  return new Intl.NumberFormat('vi-VN').format(amount) + 'đ';
}

export function formatPercent(rate: number): string {
  // rate 0..1 or 0..100
  const pct = rate <= 1 ? rate * 100 : rate;
  return Math.round(pct * 10) / 10 + '%';
}

export interface ToolFormattedResponse {
  [key: string]: unknown;
  content: Array<{
    type: 'text';
    text: string;
  }>;
  structuredContent?: Record<string, unknown>;
  isError?: boolean;
}

export function createToolSuccess(summary: string, data: Record<string, unknown>): ToolFormattedResponse {
  const jsonStr = JSON.stringify(data, null, 2);
  const text = `${summary}\n\n\`\`\`json\n${jsonStr}\n\`\`\``;

  return {
    content: [
      {
        type: 'text',
        text,
      },
    ],
    structuredContent: data,
  };
}

export function createToolError(message: string): ToolFormattedResponse {
  return {
    content: [
      {
        type: 'text',
        text: `Lỗi: ${message}`,
      },
    ],
    isError: true,
  };
}
