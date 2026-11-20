export type ApiResult<T> = { ok: true; data: T } | { ok: false; status: number; message: string };

/** Calls the same-origin BFF. Never throws; network failures become a 0-status result. */
export async function bff<T = unknown>(
  method: "GET" | "POST" | "PUT",
  path: string,
  body?: unknown,
  headers: Record<string, string> = {},
): Promise<ApiResult<T>> {
  try {
    const response = await fetch(`/api/bff/${path}`, {
      method,
      headers: body === undefined ? headers : { "Content-Type": "application/json", ...headers },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok)
      return {
        ok: false,
        status: response.status,
        message: (data as { message?: string }).message ?? "Không thể thực hiện thao tác.",
      };
    return { ok: true, data: data as T };
  } catch {
    return {
      ok: false,
      status: 0,
      message: "Kết nối tạm gián đoạn. Vui lòng kiểm tra lại trạng thái trước khi thử lại.",
    };
  }
}

export const newIdempotencyKey = () => `web-${crypto.randomUUID()}`;
