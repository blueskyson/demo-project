// 簡易 MCP client：用 stdio 啟動 server.js，列出工具並實際呼叫，驗證 server 能正常運作。
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";

const transport = new StdioClientTransport({
  command: "node",
  args: [new URL("./server.js", import.meta.url).pathname],
});
const client = new Client({ name: "weather-test-client", version: "1.0.0" });
await client.connect(transport);

const { tools } = await client.listTools();
console.log("== 工具清單 ==");
for (const t of tools) console.log(`- ${t.name}: ${Object.keys(t.inputSchema.properties).join(", ")}`);

const cases = [
  ["get_weather", { city: "Taipei", days: 2 }],
  ["get_weather", { city: "Kaohsiung", country_code: "TW", days: 1 }],
  ["get_weather", { latitude: 35.68, longitude: 139.69, days: 1 }],
  ["search_locations", { name: "Springfield", country_code: "US" }],
  ["get_weather", { city: "Xyzqwvbn" }], // 預期 isError
  ["get_weather", {}], // 預期 isError
];

let failed = 0;
for (const [name, args] of cases) {
  const res = await client.callTool({ name, arguments: args });
  const expectError = name === "get_weather" && (args.city === "Xyzqwvbn" || Object.keys(args).length === 0);
  const ok = Boolean(res.isError) === expectError;
  if (!ok) failed++;
  console.log(`\n== ${ok ? "PASS" : "FAIL"} ${name} ${JSON.stringify(args)}${res.isError ? " [isError]" : ""} ==`);
  console.log(res.content.map((c) => c.text).join("\n"));
}

await client.close();
console.log(`\n${failed === 0 ? "全部通過" : `${failed} 個案例失敗`}`);
process.exit(failed === 0 ? 0 : 1);
