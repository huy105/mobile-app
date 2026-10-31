import { MenuItem } from '../data/types.js';

export interface FormattedMenuItem {
  name: string;
  category: string;
  price: number;
  tags?: string[];
  remaining: number | null;
  available: boolean;
}

export interface ListMenuResult {
  date: string;
  items: FormattedMenuItem[];
}

export function computeListMenu(
  menuItems: MenuItem[],
  date: string,
  options?: {
    tags?: string[];
    maxPrice?: number;
    onlyAvailable?: boolean;
  }
): ListMenuResult {
  const onlyAvailable = options?.onlyAvailable !== false; // mặc định true
  const maxPrice = options?.maxPrice;
  const tagsFilter = options?.tags;

  const itemsOfDate = menuItems.filter((m) => m.date === date);

  const formattedList: FormattedMenuItem[] = [];

  for (const m of itemsOfDate) {
    const dailyQuota = m.dailyQuota || 0;
    const soldQty = m.soldQty || 0;
    const remaining = dailyQuota > 0 ? dailyQuota - soldQty : null;
    const available = m.inStock && (remaining === null || remaining > 0);

    if (onlyAvailable && !available) {
      continue;
    }

    if (maxPrice !== undefined && m.price > maxPrice) {
      continue;
    }

    if (tagsFilter && tagsFilter.length > 0) {
      const itemTags = m.tags || [];
      const hasAllTags = tagsFilter.every((t) => itemTags.includes(t));
      if (!hasAllTags) {
        continue;
      }
    }

    formattedList.push({
      name: m.name,
      category: m.category,
      price: m.price,
      tags: m.tags,
      remaining,
      available,
    });
  }

  // Sắp theo category rồi price
  formattedList.sort((a, b) => {
    const catComp = a.category.localeCompare(b.category);
    if (catComp !== 0) return catComp;
    return a.price - b.price;
  });

  return {
    date,
    items: formattedList,
  };
}

