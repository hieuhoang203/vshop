import { useEffect } from "react";
import { router } from "expo-router";
import { Loading } from "../../components/ui";
export default function Callback() {
  useEffect(() => {
    router.replace("/account");
  }, []);
  return <Loading />;
}
