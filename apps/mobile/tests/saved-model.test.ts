import { parseSaved, toggleProduct } from "../src/features/saved/saved-model";
const item = { id: "1", slug: "den-ban", name: "Đèn bàn", price: 100000, imageKey: "lamp" };
test("invalid persisted snapshots are discarded and duplicates normalized", () => {
  expect(parseSaved("broken")).toEqual([]);
  expect(
    parseSaved(
      JSON.stringify([
        item,
        item,
        { ...item, id: "2", price: -1 },
        { ...item, slug: "../../invalid" },
      ]),
    ),
  ).toEqual([item]);
});
test("toggle removes a saved item and caps new snapshots at 100", () => {
  expect(toggleProduct([item], item)).toEqual([]);
  const list = Array.from({ length: 100 }, (_, i) => ({ ...item, id: String(i) }));
  const next = toggleProduct(list, { ...item, id: "new" });
  expect(next).toHaveLength(100);
  expect(next[0]?.id).toBe("1");
  expect(next[99]?.id).toBe("new");
});
