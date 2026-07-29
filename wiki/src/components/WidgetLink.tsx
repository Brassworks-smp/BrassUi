import { Link } from "react-router-dom";
import { WIDGETS } from "../data/widgets";

/**
 * Link to a widget's page when it exists in the catalog, otherwise render the name as plain accent
 * text - so a guide can reference any type without breaking if it isn't a documented widget.
 */
export function WidgetLink({ name, fallback }: { name: string; fallback?: string }) {
  const exists = WIDGETS.some((w) => w.slug === name);
  const text = fallback ?? name;
  if (!exists) return <span className="font-mc text-brass-300">{text}</span>;
  return (
    <Link to={`/widgets/${name}`} className="font-mc text-brass-300 underline-offset-2 hover:underline">
      {text}
    </Link>
  );
}
