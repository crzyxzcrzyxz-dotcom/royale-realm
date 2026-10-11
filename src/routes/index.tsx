import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Battle Royale — Plugin para Paper" },
      { name: "description", content: "Projeto do plugin Battle Royale para Minecraft Paper, com safe zone customizada e itens especiais." },
      { property: "og:title", content: "Battle Royale — Plugin para Paper" },
      { property: "og:description", content: "Plugin Minecraft com partidas Battle Royale e barreira customizada sem partículas." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: Index,
});

// IMPORTANT: Replace this placeholder. See ./README.md for routing conventions.
function Index() {
  return (
    <div
      className="flex min-h-screen items-center justify-center"
      style={{ backgroundColor: "#fcfbf8" }}
    >
      <img
        data-lovable-blank-page-placeholder="REMOVE_THIS"
        src="https://cdn.gpteng.co/blank-app-v1.svg"
        alt="Your app will live here!"
      />
    </div>
  );
}
