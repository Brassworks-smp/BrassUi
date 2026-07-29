import React from "react";
import { createRoot } from "react-dom/client";
import { HashRouter } from "react-router-dom";
import { TooltipLayer, applyAccent } from "brassui-react";
import "./styles.css";
import { App } from "./App";
import { savedAccent } from "./components/AccentPicker";

// Apply the remembered accent before first paint, so there's no flash of the default brass.
applyAccent(savedAccent());

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <HashRouter>
      <App />
      <TooltipLayer />
    </HashRouter>
  </React.StrictMode>,
);
