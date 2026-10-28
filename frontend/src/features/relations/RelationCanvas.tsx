import { Maximize2 } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  NODE_HEIGHT,
  NODE_WIDTH,
  type GraphLayout,
  type LayoutNode
} from "./relationLayout";

const MIN_SCALE = 0.02;
const MAX_SCALE = 2.5;
const MAX_HOVER_NODES = 4000;

const palette = {
  edge: "rgba(133, 114, 232, 0.55)",
  edgeInferred: "rgba(195, 149, 77, 0.6)",
  node: "#faf8ff",
  nodeInferred: "#fff9ed",
  nodeStroke: "#dcd5f4",
  nodeStrokeInferred: "#e5cfaa",
  nodeStrokeActive: "#6650d9",
  dot: "#8572e8",
  dotInferred: "#c3954d",
  label: "#19171f"
};

interface ViewState {
  scale: number;
  x: number;
  y: number;
}

interface RelationCanvasProps {
  layout: GraphLayout;
  ariaLabel: string;
  fitLabel: string;
  onNodeClick: (fileId: string) => void;
  onHoverChange: (node: LayoutNode | null) => void;
}

export default function RelationCanvas({
  layout,
  ariaLabel,
  fitLabel,
  onNodeClick,
  onHoverChange
}: RelationCanvasProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [view, setView] = useState<ViewState>({ scale: 1, x: 0, y: 0 });
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [hoverId, setHoverId] = useState<string | null>(null);

  const viewRef = useRef(view);
  viewRef.current = view;
  const sizeRef = useRef(size);
  sizeRef.current = size;
  const hoverRef = useRef<string | null>(null);
  hoverRef.current = hoverId;
  const fittedRef = useRef<GraphLayout | null>(null);
  const dragRef = useRef<{
    pointerId: number;
    startX: number;
    startY: number;
    originX: number;
    originY: number;
    moved: boolean;
  } | null>(null);

  const nodeById = useMemo(
    () => new Map(layout.nodes.map((node) => [node.id, node])),
    [layout]
  );
  const hasSize = size.width > 0 && size.height > 0;
  const interactiveHover = layout.nodes.length <= MAX_HOVER_NODES;

  const fit = useCallback(() => {
    const current = sizeRef.current;
    if (!current.width || !current.height) {
      return;
    }
    const worldWidth = Math.max(layout.width, NODE_WIDTH);
    const worldHeight = Math.max(layout.height, NODE_HEIGHT);
    const scale = Math.min(
      current.width / (worldWidth + 80),
      current.height / (worldHeight + 80),
      1
    );
    const nextScale = Math.max(scale, MIN_SCALE);
    setView({
      scale: nextScale,
      x: (current.width - worldWidth * nextScale) / 2,
      y: (current.height - worldHeight * nextScale) / 2
    });
  }, [layout]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }
    const measure = () => {
      const rect = container.getBoundingClientRect();
      setSize({ width: rect.width, height: rect.height });
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(container);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!hasSize || fittedRef.current === layout) {
      return;
    }
    fittedRef.current = layout;
    fit();
  }, [hasSize, layout, fit]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }
    const onWheel = (event: WheelEvent) => {
      event.preventDefault();
      const rect = container.getBoundingClientRect();
      const pointerX = event.clientX - rect.left;
      const pointerY = event.clientY - rect.top;
      const current = viewRef.current;
      const ratio = Math.min(
        MAX_SCALE,
        Math.max(MIN_SCALE, current.scale * Math.exp(-event.deltaY * 0.0015))
      ) / current.scale;
      setView({
        scale: current.scale * ratio,
        x: pointerX - (pointerX - current.x) * ratio,
        y: pointerY - (pointerY - current.y) * ratio
      });
    };
    container.addEventListener("wheel", onWheel, { passive: false });
    return () => container.removeEventListener("wheel", onWheel);
  }, []);

  const nodeAt = useCallback(
    (clientX: number, clientY: number): LayoutNode | null => {
      const container = containerRef.current;
      if (!container) {
        return null;
      }
      const rect = container.getBoundingClientRect();
      const current = viewRef.current;
      const worldX = (clientX - rect.left - current.x) / current.scale;
      const worldY = (clientY - rect.top - current.y) / current.scale;
      for (let index = layout.nodes.length - 1; index >= 0; index--) {
        const node = layout.nodes[index];
        if (
          worldX >= node.x &&
          worldX <= node.x + NODE_WIDTH &&
          worldY >= node.y &&
          worldY <= node.y + NODE_HEIGHT
        ) {
          return node;
        }
      }
      return null;
    },
    [layout]
  );

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !hasSize) {
      return;
    }
    const context = canvas.getContext("2d");
    if (!context) {
      return;
    }
    const ratio = window.devicePixelRatio || 1;
    const pixelWidth = Math.round(size.width * ratio);
    const pixelHeight = Math.round(size.height * ratio);
    if (canvas.width !== pixelWidth || canvas.height !== pixelHeight) {
      canvas.width = pixelWidth;
      canvas.height = pixelHeight;
    }
    context.setTransform(ratio, 0, 0, ratio, 0, 0);
    context.clearRect(0, 0, size.width, size.height);

    const { scale, x: offsetX, y: offsetY } = view;
    const toScreenX = (value: number) => value * scale + offsetX;
    const toScreenY = (value: number) => value * scale + offsetY;
    const worldLeft = -offsetX / scale;
    const worldTop = -offsetY / scale;
    const worldRight = (size.width - offsetX) / scale;
    const worldBottom = (size.height - offsetY) / scale;

    context.lineCap = "round";
    for (const edge of layout.edges) {
      const source = nodeById.get(edge.source);
      const target = nodeById.get(edge.target);
      if (!source || !target) {
        continue;
      }
      const startX = source.x + NODE_WIDTH;
      const startY = source.y + NODE_HEIGHT / 2;
      const endX = target.x;
      const endY = target.y + NODE_HEIGHT / 2;
      if (
        Math.max(startX, endX) < worldLeft ||
        Math.min(startX, endX) > worldRight ||
        Math.max(startY, endY) < worldTop ||
        Math.min(startY, endY) > worldBottom
      ) {
        continue;
      }
      context.strokeStyle = edge.inferred ? palette.edgeInferred : palette.edge;
      context.lineWidth = edge.count > 1 ? 1.8 : 1;
      const bend = Math.max(Math.abs(endX - startX) * 0.45, 24);
      const screenEndX = toScreenX(endX);
      const screenEndY = toScreenY(endY);
      context.beginPath();
      context.moveTo(toScreenX(startX), toScreenY(startY));
      context.bezierCurveTo(
        toScreenX(startX + bend),
        toScreenY(startY),
        toScreenX(endX - bend),
        screenEndY,
        screenEndX,
        screenEndY
      );
      context.stroke();
      if (scale >= 0.45) {
        const head = 5;
        context.fillStyle = edge.inferred ? palette.edgeInferred : palette.edge;
        context.beginPath();
        context.moveTo(screenEndX, screenEndY);
        context.lineTo(screenEndX - head, screenEndY - head * 0.5);
        context.lineTo(screenEndX - head, screenEndY + head * 0.5);
        context.closePath();
        context.fill();
      }
    }

    const showLabels = scale >= 0.55;
    for (const node of layout.nodes) {
      if (
        node.x + NODE_WIDTH < worldLeft ||
        node.x > worldRight ||
        node.y + NODE_HEIGHT < worldTop ||
        node.y > worldBottom
      ) {
        continue;
      }
      const screenX = toScreenX(node.x);
      const screenY = toScreenY(node.y);
      const screenWidth = NODE_WIDTH * scale;
      const screenHeight = NODE_HEIGHT * scale;
      if (screenWidth < 4 || screenHeight < 4) {
        context.fillStyle = node.inferred ? palette.dotInferred : palette.dot;
        context.beginPath();
        context.arc(
          screenX + screenWidth / 2,
          screenY + screenHeight / 2,
          1.6,
          0,
          Math.PI * 2
        );
        context.fill();
        continue;
      }
      const radius = Math.min(6, screenWidth / 6);
      context.beginPath();
      context.moveTo(screenX + radius, screenY);
      context.arcTo(screenX + screenWidth, screenY, screenX + screenWidth, screenY + screenHeight, radius);
      context.arcTo(screenX + screenWidth, screenY + screenHeight, screenX, screenY + screenHeight, radius);
      context.arcTo(screenX, screenY + screenHeight, screenX, screenY, radius);
      context.arcTo(screenX, screenY, screenX + screenWidth, screenY, radius);
      context.closePath();
      context.fillStyle = node.inferred ? palette.nodeInferred : palette.node;
      context.fill();
      const active = node.id === hoverId;
      context.strokeStyle = active
        ? palette.nodeStrokeActive
        : node.inferred
          ? palette.nodeStrokeInferred
          : palette.nodeStroke;
      context.lineWidth = active ? 1.8 : 1;
      context.stroke();

      if (showLabels && screenWidth >= 90 && screenHeight >= 18) {
        context.save();
        context.beginPath();
        context.rect(screenX + 4, screenY, screenWidth - 8, screenHeight);
        context.clip();
        context.fillStyle = palette.label;
        context.font =
          "600 11px ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif";
        context.textBaseline = "middle";
        context.fillText(node.fileName, screenX + 8, screenY + screenHeight / 2);
        context.restore();
      }
    }
  }, [hasSize, hoverId, layout, nodeById, size.height, size.width, view]);

  const handlePointerDown = (event: React.PointerEvent<HTMLCanvasElement>) => {
    event.currentTarget.setPointerCapture(event.pointerId);
    dragRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      originX: viewRef.current.x,
      originY: viewRef.current.y,
      moved: false
    };
  };

  const handlePointerMove = (event: React.PointerEvent<HTMLCanvasElement>) => {
    const drag = dragRef.current;
    if (drag && drag.pointerId === event.pointerId) {
      const deltaX = event.clientX - drag.startX;
      const deltaY = event.clientY - drag.startY;
      if (Math.abs(deltaX) > 3 || Math.abs(deltaY) > 3) {
        drag.moved = true;
      }
      if (drag.moved) {
        setView({
          scale: viewRef.current.scale,
          x: drag.originX + deltaX,
          y: drag.originY + deltaY
        });
        return;
      }
    }
    if (!interactiveHover) {
      return;
    }
    const node = nodeAt(event.clientX, event.clientY);
    const nextId = node ? node.id : null;
    if (nextId !== hoverRef.current) {
      hoverRef.current = nextId;
      setHoverId(nextId);
      onHoverChange(node);
    }
  };

  const handlePointerUp = (event: React.PointerEvent<HTMLCanvasElement>) => {
    const drag = dragRef.current;
    dragRef.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    if (drag && drag.moved) {
      return;
    }
    const node = nodeAt(event.clientX, event.clientY);
    if (node) {
      onNodeClick(node.id);
    }
  };

  const handlePointerLeave = () => {
    if (hoverRef.current !== null) {
      hoverRef.current = null;
      setHoverId(null);
      onHoverChange(null);
    }
  };

  return (
    <div className="relation-canvas" ref={containerRef}>
      <canvas
        ref={canvasRef}
        className="relation-canvas-surface"
        role="img"
        aria-label={ariaLabel}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerLeave={handlePointerLeave}
      />
      <button
        type="button"
        className="relation-canvas-fit"
        onClick={fit}
        title={fitLabel}
        aria-label={fitLabel}
      >
        <Maximize2 size={13} />
      </button>
    </div>
  );
}
