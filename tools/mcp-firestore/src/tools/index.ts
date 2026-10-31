import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { DataSource } from '../data/dataSource.js';
import { Config } from '../config.js';

import { registerQueryOrdersTool } from './queryOrders.js';
import { registerSalesTool } from './sales.js';
import { registerTopItemsTool } from './topItems.js';
import { registerOrdersBySlotTool } from './ordersBySlot.js';
import { registerCancellationsTool } from './cancellations.js';
import { registerPrepTool } from './prep.js';
import { registerUnsoldTool } from './unsold.js';
import { registerForecastTool } from './forecast.js';
import { registerMenuTool } from './menu.js';

export function registerAllTools(server: McpServer, dataSource: DataSource, config: Config) {
  // 6.0 query_orders (công cụ tổng quát)
  registerQueryOrdersTool(server, dataSource, config);

  // 6.1 get_sales_summary
  registerSalesTool(server, dataSource, config);

  // 6.2 get_top_items
  registerTopItemsTool(server, dataSource, config);

  // 6.3 get_orders_by_slot
  registerOrdersBySlotTool(server, dataSource, config);

  // 6.4 get_cancellations
  registerCancellationsTool(server, dataSource, config);

  // 6.5 get_prep_time_stats
  registerPrepTool(server, dataSource, config);

  // 6.6 get_unsold_items
  registerUnsoldTool(server, dataSource, config);

  // 6.7 forecast_tomorrow
  registerForecastTool(server, dataSource, config);

  // 6.8 list_menu
  registerMenuTool(server, dataSource, config);
}
