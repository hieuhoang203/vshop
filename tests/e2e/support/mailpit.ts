import { expect, type APIRequestContext } from "@playwright/test";

const MAILPIT = "http://localhost:8025";

/** Waits for a mail sent to the address (local Mailpit catches everything) and returns its text. */
export async function waitForMail(request: APIRequestContext, to: string, subject: RegExp) {
  for (let attempt = 0; attempt < 30; attempt++) {
    const found = await request.get(
      `${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${to}`)}`,
    );
    const list = (await found.json()) as { messages?: { ID: string; Subject: string }[] };
    const hit = list.messages?.find((m) => subject.test(m.Subject));
    if (hit) {
      const message = await request.get(`${MAILPIT}/api/v1/message/${hit.ID}`);
      return (await message.json()) as { Subject: string; Text: string };
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  expect(false, `no mail for ${to} matching ${subject}`).toBeTruthy();
  throw new Error("unreachable");
}

/** The first link of a mail body that points at the identity provider. */
export function actionLink(text: string) {
  const link = text.match(/https?:\/\/localhost:8180\/[^\s]+/)?.[0];
  expect(link).toBeTruthy();
  return link!;
}
