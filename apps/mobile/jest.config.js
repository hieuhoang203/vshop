module.exports = {
  preset: "jest-expo",
  testTimeout: 30000,
  testMatch: ["<rootDir>/tests/**/*.test.ts", "<rootDir>/tests/**/*.test.tsx"],
  setupFilesAfterEnv: ["<rootDir>/tests/setup.ts"],
  moduleNameMapper: {
    "^lucide-react-native/icons/(.*)$":
      "<rootDir>/../../node_modules/lucide-react-native/dist/cjs/icons/$1.js",
  },
};
