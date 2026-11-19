"use client";

import { useEffect, useId, useRef, useState } from "react";
import { Area, AreaChart, CartesianGrid, XAxis, YAxis } from "recharts";
import { Badge, Button, SegmentedControl, Table, Spinner } from "@radix-ui/themes";
import { Activity, FilePlus2, CheckCheck, RotateCcw, ChevronDown } from "lucide-react";
import type { ShopActivityReport } from "@vshop/contracts";
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart";
import { formatDay } from "@/lib/format";

const config = {
  submitted: { label: "Hồ sơ tạo mới", color: "#793df5" },
  approved: { label: "Lượt chấp thuận", color: "#88b849" },
} satisfies ChartConfig;

export function ShopActivityChart({ initialReport }: { initialReport: ShopActivityReport | null }) {
  const [report, setReport] = useState(initialReport);
  const [days, setDays] = useState("30");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(initialReport ? "" : "Chưa thể tải dữ liệu hoạt động.");
  const [series, setSeries] = useState("all");
  const id = useId().replace(/[^a-zA-Z0-9_-]/g, "");
  const requestRef = useRef<AbortController | null>(null);
  useEffect(() => () => requestRef.current?.abort(), []);
  async function load(value: string) {
    requestRef.current?.abort();
    const controller = new AbortController();
    requestRef.current = controller;
    setDays(value);
    setPending(true);
    setError("");
    try {
      const response = await fetch(`/api/bff/admin/analytics?days=${value}`, {
        signal: controller.signal,
        cache: "no-store",
      });
      if (!response.ok)
        throw new Error(
          response.status === 401
            ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
            : "Chưa thể tải dữ liệu hoạt động.",
        );
      const next: ShopActivityReport = await response.json();
      if (!controller.signal.aborted) setReport(next);
    } catch (cause) {
      if (!controller.signal.aborted) {
        setReport(null);
        setError(cause instanceof Error ? cause.message : "Chưa thể tải dữ liệu.");
      }
    } finally {
      if (!controller.signal.aborted) setPending(false);
    }
  }
  const totals = report?.points.reduce(
    (sum, point) => ({
      submitted: sum.submitted + point.submitted,
      approved: sum.approved + point.approved,
      rejected: sum.rejected + point.rejected,
    }),
    { submitted: 0, approved: 0, rejected: 0 },
  );
  return (
    <section
      className="activity-panel"
      aria-label="Biểu đồ hoạt động gian hàng"
      aria-busy={pending}
    >
      <div className="activity-header">
        <div>
          <Badge variant="soft" color="iris">
            <Activity size={13} /> NHỊP HOẠT ĐỘNG
          </Badge>
          <h2>Gian hàng đang chuyển động.</h2>
          <p>Hồ sơ mới và kết quả xét duyệt từ dữ liệu hệ thống.</p>
        </div>
        <SegmentedControl.Root
          value={days}
          onValueChange={load}
          aria-label="Khoảng thời gian biểu đồ"
        >
          <SegmentedControl.Item value="7">7 ngày</SegmentedControl.Item>
          <SegmentedControl.Item value="30">30 ngày</SegmentedControl.Item>
          <SegmentedControl.Item value="90">90 ngày</SegmentedControl.Item>
        </SegmentedControl.Root>
      </div>
      <div className="activity-stats">
        <div>
          <span className="activity-stat-icon purple-stat">
            <FilePlus2 size={19} />
          </span>
          <span>
            Hồ sơ tạo mới<strong>{pending ? "—" : (totals?.submitted ?? "—")}</strong>
          </span>
        </div>
        <div>
          <span className="activity-stat-icon green-stat">
            <CheckCheck size={19} />
          </span>
          <span>
            Lượt chấp thuận<strong>{pending ? "—" : (totals?.approved ?? "—")}</strong>
          </span>
        </div>
        <div className="activity-period">
          <span>
            Khoảng dữ liệu
            <strong>
              {report ? `${formatDay(report.from)} – ${formatDay(report.to)}` : "Chưa có dữ liệu"}
            </strong>
          </span>
          <span className="data-source-tag">Dữ liệu thực · giờ Việt Nam</span>
        </div>
      </div>
      <div className="chart-controls">
        <div className="chart-legend">
          <span>
            <i style={{ background: config.submitted.color }} />
            Hồ sơ tạo mới
          </span>
          <span>
            <i style={{ background: config.approved.color }} />
            Lượt chấp thuận
          </span>
        </div>
        <SegmentedControl.Root
          value={series}
          onValueChange={setSeries}
          size="1"
          aria-label="Chuỗi dữ liệu hiển thị"
        >
          <SegmentedControl.Item value="all">Cả hai</SegmentedControl.Item>
          <SegmentedControl.Item value="submitted">Hồ sơ</SegmentedControl.Item>
          <SegmentedControl.Item value="approved">Duyệt</SegmentedControl.Item>
        </SegmentedControl.Root>
      </div>
      <div className={`activity-chart-wrap${pending ? " is-loading" : ""}`}>
        {pending && (
          <div className="chart-loading" role="status">
            <Spinner />
            Đang cập nhật biểu đồ…
          </div>
        )}
        {report ? (
          <ChartContainer config={config} className="activity-chart">
            <AreaChart
              accessibilityLayer
              data={report.points}
              margin={{ left: -18, right: 12, top: 12, bottom: 5 }}
            >
              <defs>
                <linearGradient id={`submitted-${id}`} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="var(--color-submitted)" stopOpacity={0.32} />
                  <stop offset="100%" stopColor="var(--color-submitted)" stopOpacity={0.02} />
                </linearGradient>
                <linearGradient id={`approved-${id}`} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="var(--color-approved)" stopOpacity={0.22} />
                  <stop offset="100%" stopColor="var(--color-approved)" stopOpacity={0.01} />
                </linearGradient>
              </defs>
              <CartesianGrid vertical={false} strokeDasharray="4 5" stroke="#eee8f5" />
              <XAxis
                dataKey="date"
                tickLine={false}
                axisLine={false}
                tickMargin={12}
                minTickGap={32}
                tickFormatter={formatDay}
              />
              <YAxis
                tickLine={false}
                axisLine={false}
                allowDecimals={false}
                tickMargin={10}
                domain={[0, "auto"]}
              />
              <ChartTooltip
                cursor={{ stroke: "#d5c6ee", strokeDasharray: "4 4" }}
                content={
                  <ChartTooltipContent
                    indicator="line"
                    labelFormatter={(label) => `Ngày ${formatDay(String(label))}`}
                  />
                }
              />
              {series !== "approved" && (
                <Area
                  type="monotone"
                  dataKey="submitted"
                  stroke="var(--color-submitted)"
                  fill={`url(#submitted-${id})`}
                  strokeWidth={2.5}
                  isAnimationActive={false}
                />
              )}{" "}
              {series !== "submitted" && (
                <Area
                  type="monotone"
                  dataKey="approved"
                  stroke="var(--color-approved)"
                  fill={`url(#approved-${id})`}
                  strokeWidth={2.5}
                  isAnimationActive={false}
                />
              )}
            </AreaChart>
          </ChartContainer>
        ) : (
          <div className="chart-error" role="status">
            <Activity size={28} />
            <p>{error}</p>
            <Button variant="soft" onClick={() => load(days)}>
              <RotateCcw size={15} />
              Thử lại
            </Button>
          </div>
        )}
      </div>
      <div className="chart-footer">
        <p>
          Hồ sơ tạo mới không bao gồm lượt gửi lại. Kết quả duyệt tính từ lịch sử quyết định đã lưu.
        </p>
        {!pending && report && totals?.submitted === 0 && totals.approved === 0 && (
          <span>Chưa có hoạt động trong khoảng này.</span>
        )}
      </div>
      {report && (
        <details className="chart-data-table">
          <summary>
            Xem bảng dữ liệu <ChevronDown size={14} />
          </summary>
          <div tabIndex={0} className="chart-table-scroll" aria-label="Bảng dữ liệu hoạt động">
            <Table.Root size="1">
              <Table.Header>
                <Table.Row>
                  <Table.ColumnHeaderCell>Ngày</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell>Hồ sơ mới</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell>Chấp thuận</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell>Cần bổ sung</Table.ColumnHeaderCell>
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {report.points.map((point) => (
                  <Table.Row key={point.date}>
                    <Table.RowHeaderCell>{formatDay(point.date)}</Table.RowHeaderCell>
                    <Table.Cell>{point.submitted}</Table.Cell>
                    <Table.Cell>{point.approved}</Table.Cell>
                    <Table.Cell>{point.rejected}</Table.Cell>
                  </Table.Row>
                ))}
              </Table.Body>
            </Table.Root>
          </div>
        </details>
      )}
    </section>
  );
}
