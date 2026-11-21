import { environment } from "../config/environment";

export class ApiFailure extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
  }
}
export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const controller = new AbortController();
  const abort = () => controller.abort();
  if (options.signal?.aborted) controller.abort();
  options.signal?.addEventListener("abort", abort, { once: true });
  const timeout = setTimeout(abort, 10000);
  try {
    const response = await fetch(`${environment.apiUrl}/api/v1${path}`, {
      ...options,
      signal: controller.signal,
      headers: {
        Accept: "application/json",
        ...(options.body ? { "Content-Type": "application/json" } : {}),
        ...options.headers,
      },
    });
    if (!response.ok) {
      const body = await response.json().catch(() => null);
      throw new ApiFailure(
        response.status,
        body?.message ??
          (response.status === 401
            ? "Phiên đăng nhập đã hết hạn."
            : "Chưa thể tải dữ liệu. Vui lòng thử lại."),
      );
    }
    // Some answers (for example 201 on sign-up) have no body.
    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
  } catch (error) {
    if (error instanceof ApiFailure) throw error;
    throw new Error("Không thể kết nối VShop. Kiểm tra mạng và thử lại.");
  } finally {
    clearTimeout(timeout);
    options.signal?.removeEventListener("abort", abort);
  }
}
