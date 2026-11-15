import { Route, Routes } from "react-router-dom";
import { SupportDetail } from "./SupportDetail";
import { SupportList } from "./SupportList";

export function Support() {
  return (
    <Routes>
      <Route index element={<SupportList />} />
      <Route path=":id" element={<SupportDetail />} />
    </Routes>
  );
}
