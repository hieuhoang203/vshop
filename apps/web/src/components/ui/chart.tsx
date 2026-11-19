"use client";

// Adapted from shadcn/ui Chart (MIT). See THIRD_PARTY_NOTICES.md.
// The ChartContainer/config/tooltip API follows shadcn; styling uses VShop CSS alongside Radix Themes.
import {
  createContext,
  useContext,
  useId,
  type CSSProperties,
  type ReactNode,
  type ComponentProps,
} from "react";
import { ResponsiveContainer, Tooltip } from "recharts";
import clsx from "clsx";

export type ChartConfig = Record<string, { label: ReactNode; color: string }>;
const ChartContext = createContext<ChartConfig | null>(null);

export function ChartContainer({
  config,
  children,
  className,
  ...props
}: ComponentProps<"div"> & {
  config: ChartConfig;
  children: ComponentProps<typeof ResponsiveContainer>["children"];
}) {
  const id = useId().replace(/[^a-zA-Z0-9_-]/g, "");
  const colors = Object.fromEntries(
    Object.entries(config).map(([key, value]) => [`--color-${key}`, value.color]),
  );
  return (
    <ChartContext.Provider value={config}>
      <div
        data-slot="chart"
        data-chart={`chart-${id}`}
        className={clsx("chart-container", className)}
        style={colors as CSSProperties}
        {...props}
      >
        <ResponsiveContainer initialDimension={{ width: 320, height: 260 }}>
          {children}
        </ResponsiveContainer>
      </div>
    </ChartContext.Provider>
  );
}

export const ChartTooltip = Tooltip;
type TooltipItem = {
  dataKey?: string | number;
  name?: string | number;
  value?: number | string | readonly (number | string)[];
  color?: string;
  type?: string;
};
export function ChartTooltipContent({
  active,
  payload,
  label,
  labelFormatter,
  indicator = "dot",
}: {
  active?: boolean;
  payload?: readonly TooltipItem[];
  label?: ReactNode;
  labelFormatter?: (label: ReactNode) => ReactNode;
  indicator?: "dot" | "line";
}) {
  const config = useContext(ChartContext);
  if (!config) throw new Error("ChartTooltipContent requires ChartContainer");
  if (!active || !payload?.length) return null;
  return (
    <div className="chart-tooltip">
      <div className="chart-tooltip-title">{labelFormatter ? labelFormatter(label) : label}</div>
      {payload
        .filter((item) => item.type !== "none")
        .map((item, index) => {
          const key = String(item.dataKey ?? item.name ?? "");
          const entry = config[key];
          return (
            <div className="chart-tooltip-row" key={`${key}-${index}`}>
              <span
                className={`chart-tooltip-indicator indicator-${indicator}`}
                style={{ backgroundColor: entry?.color ?? item.color }}
              />
              <span>{entry?.label ?? item.name}</span>
              <strong>
                {typeof item.value === "number"
                  ? item.value.toLocaleString("vi-VN")
                  : String(item.value ?? "")}
              </strong>
            </div>
          );
        })}
    </div>
  );
}
