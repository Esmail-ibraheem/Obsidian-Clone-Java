import { WidgetType } from "@codemirror/view";

/** A rendered, clickable wikilink. Click opens (or creates) the target note. */
export class WikiLinkWidget extends WidgetType {
  constructor(
    readonly display: string,
    readonly target: string,
    readonly resolved: boolean,
    readonly onOpen: (target: string, split: boolean) => void,
  ) {
    super();
  }

  eq(other: WikiLinkWidget): boolean {
    return (
      other.display === this.display &&
      other.target === this.target &&
      other.resolved === this.resolved
    );
  }

  toDOM(): HTMLElement {
    const span = document.createElement("span");
    span.className = "cm-wikilink" + (this.resolved ? "" : " cm-wikilink-unresolved");
    span.textContent = this.display;
    span.setAttribute("role", "link");
    span.addEventListener("mousedown", (event) => {
      event.preventDefault();
      event.stopPropagation();
      this.onOpen(this.target, event.ctrlKey || event.metaKey);
    });
    return span;
  }
}

/** An inline image embed (`![[image.png]]`). */
export class ImageEmbedWidget extends WidgetType {
  constructor(
    readonly src: string,
    readonly alt: string,
  ) {
    super();
  }

  eq(other: ImageEmbedWidget): boolean {
    return other.src === this.src && other.alt === this.alt;
  }

  toDOM(): HTMLElement {
    const img = document.createElement("img");
    img.className = "cm-embed-image";
    img.src = this.src;
    img.alt = this.alt;
    return img;
  }
}
