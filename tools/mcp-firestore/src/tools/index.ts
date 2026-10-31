import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { DataSource } from '../data/dataSource.js';
import { Config } from '../config.js';

import { registerGetSchemaTool } from './getSchema.js';
import { registerRunSqlTool } from './runSql.js';
import { registerForecastTomorrowTool } from './forecastTomorrow.js';
import { registerFindSlowDaysTool } from './findSlowDays.js';

export function registerAllTools(
  server: McpServer,
  dataSource: DataSource,
  config: Config
) {
  // 6.1 get_schema
  registerGetSchemaTool(server, config);

  // 6.2 run_sql
  registerRunSqlTool(server, dataSource, config);

  // 6.6 forecast_tomorrow
  registerForecastTomorrowTool(server, dataSource, config);

  // 6.7 find_slow_days
  registerFindSlowDaysTool(server, dataSource, config);
}
