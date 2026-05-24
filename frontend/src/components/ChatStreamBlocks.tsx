import { ChevronRight } from "lucide-react";
import { MarkdownMessage } from "./MarkdownMessage";
import { AudioMessage } from "./AudioMessage";
import type { StreamBlock } from "../lib/stream";

type Props = {
  blocks: StreamBlock[];
};

export function ChatStreamBlocks({ blocks }: Props) {
  return (
    <div className="space-y-4">
      {blocks.map((block) =>
        block.type === "final_answer" ? (
          <article key={block.id} data-testid="final-answer" className="rounded-none px-1 py-2">
            <MarkdownMessage content={block.content} />
          </article>
        ) : (
          <details
            key={block.id}
            data-testid={`block-${block.type}`}
            className="group border-l border-border pl-3 text-sm text-muted-foreground"
            open={!block.collapsed}
          >
            <summary className="flex cursor-pointer list-none items-center gap-1 py-1 text-xs font-medium">
              <ChevronRight size={14} className="transition-transform group-open:rotate-90" />
              {block.title}
            </summary>
            <div className="pb-2 pl-5">
              {block.audioUrl ? <AudioMessage url={block.audioUrl} mimeType={block.mimeType} /> : null}
              {block.content ? <MarkdownMessage content={block.content} compact /> : null}
            </div>
          </details>
        )
      )}
    </div>
  );
}
