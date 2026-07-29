import { Routes, Route } from "react-router-dom";
import { Shell } from "./components/Shell";
import { Home } from "./pages/Home";
import { GettingStarted } from "./pages/GettingStarted";
import { Architecture } from "./pages/Architecture";
import { DesignGuide } from "./pages/DesignGuide";
import { LayoutGuide } from "./pages/LayoutGuide";
import { Widgets } from "./pages/Widgets";
import { WidgetDetail } from "./pages/WidgetDetail";
import { Gallery } from "./pages/Gallery";
import { Elementa } from "./pages/Elementa";
import { DevTools } from "./pages/DevTools";

export function App() {
  return (
    <Routes>
      <Route element={<Shell />}>
        <Route path="/" element={<Home />} />
        <Route path="/getting-started" element={<GettingStarted />} />
        <Route path="/architecture" element={<Architecture />} />
        <Route path="/elementa" element={<Elementa />} />
        <Route path="/dev-tools" element={<DevTools />} />
        <Route path="/design" element={<DesignGuide />} />
        <Route path="/layout" element={<LayoutGuide />} />
        <Route path="/widgets" element={<Widgets />} />
        <Route path="/widgets/:slug" element={<WidgetDetail />} />
        <Route path="/gallery" element={<Gallery />} />
        <Route path="*" element={<Home />} />
      </Route>
    </Routes>
  );
}
