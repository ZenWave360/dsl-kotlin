/**
 * TypeScript interfaces mirroring the JSON serialization of
 * io.zenwave360.language.eventflow.view.FlowViewModel (and its dependencies).
 *
 * No behaviour — structure only. Communication between Kotlin and TypeScript
 * is done by passing JSON objects whose shape is defined here.
 */

// ============================================================================
// io.zenwave360.language.source
// ============================================================================

/** Precise location in a source file. Lines and columns are 1-based. */
export interface SourceRef {
  file: string;
  line: number;
  column: number;
}

// ============================================================================
// io.zenwave360.language.eventflow.view — enums
// ============================================================================

/** Semantic type of a node in an event flow. */
export enum FlowNodeType {
  START = "START",
  COMMAND = "COMMAND",
  EVENT = "EVENT",
  READ_MODEL = "READ_MODEL",
  POLICY = "POLICY"
}

/** Semantic meaning of a relationship between two flow nodes. */
export enum FlowEdgeType {
  CAUSATION = "CAUSATION",
  CALL = "CALL",
  OUTCOME_HANDLER = "OUTCOME_HANDLER",
  TRIGGER = "TRIGGER",
  CONDITIONAL = "CONDITIONAL",
  ERROR = "ERROR"
}

/** Layout direction produced by the layout engine. */
export enum Direction {
  LR = "LR",
  TB = "TB"
}

// ============================================================================
// io.zenwave360.language.eventflow.view — value types
// ============================================================================

/** Absolute (x, y) position in the canvas. */
export interface Point {
  x: number;
  y: number;
}

/** Width and height of a node. */
export interface Dimensions {
  width: number;
  height: number;
}

/** Axis-aligned bounding box. */
export interface FlowBounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

// ============================================================================
// io.zenwave360.language.eventflow.view — main types
// ============================================================================

/**
 * A node in an event-driven flow.
 *
 * Semantic properties are always present. Layout properties (position,
 * dimensions) are null until the layout engine has been applied.
 */
export interface FlowNode {
  id: string;
  type: FlowNodeType;
  label: string;
  system: string | null;
  service: string | null;
  sourceRef: SourceRef;
  /** Marks this event as a terminal outcome when present. */
  endOutcomeLabels: string[] | null;
  /** What starts this flow: timer, actor, or system. Present for START nodes. */
  startKind: "timer" | "actor" | "system" | null;
  /** Name of the actor that starts this flow, when one is declared. */
  actor: string | null;
  /** `@title` of a policy; replaces its text. Present for POLICY nodes that declare it. */
  title: string | null;
  /** What a policy waits on: actor (name in `actor`) or timer (text in `timer`). Absent when neither. */
  policyKind: "actor" | "timer" | null;
  /** Timer text of a policy with `@timer`/`@time`. */
  timer: string | null;
  /** Id of the lane band this node is drawn in. Null when the flow is not drawn in lanes. */
  lane: string | null;
  /** Null until layout is applied. */
  position: Point | null;
  /** Null until layout is applied. */
  dimensions: Dimensions | null;
}

/** Directed relationship between two flow nodes. */
export interface FlowEdge {
  id: string;
  source: string;
  target: string;
  type: FlowEdgeType;
  label: string | null;
  /** Outcome-node synthesis belongs to renderers; this model stores edge metadata only. */
  outcome: string | null;
  sourceRef: SourceRef | null;
}

/** Visual grouping of nodes that belong to the same system (swim lane). */
export interface FlowSystemGroupView {
  systemName: string;
  bounds: FlowBounds;
}

/**
 * A full-width horizontal band of a flow drawn in lanes (one per system or per service).
 * The last band holds the nodes with no system (Systems) or no service (Services) and has no `system`.
 */
export interface FlowLane {
  id: string;
  kind: "system" | "service";
  label: string;
  /** The system of a service band; null otherwise. */
  system: string | null;
  x: number;
  y: number;
  width: number;
  height: number;
  /** 0-based position from the top: order of first appearance in the flow, the unassigned band last. */
  order: number;
}

/** Metadata describing the algorithm used to position the flow. */
export interface LayoutMetadata {
  engine: string;
  direction: Direction;
  rankSpacing: number;
  nodeSpacing: number;
}

/**
 * Unified view model for an event-driven flow diagram.
 *
 * Before layout: nodes and edges are populated; layout, bounds, and
 * systemGroups are null.
 * After layout: all fields are populated and every node has position and
 * dimensions set.
 */
export interface FlowViewModel {
  /** Schema version, e.g. "zfl.eventflow.view@1". */
  schema: string;
  nodes: FlowNode[];
  edges: FlowEdge[];
  /** Null until layout is applied. */
  layout: LayoutMetadata | null;
  /** Null until layout is applied. */
  bounds: FlowBounds | null;
  /** Null until layout is applied. */
  systemGroups: FlowSystemGroupView[] | null;
  /** Bands, top to bottom, when the flow is drawn in lanes (systems or services). Null otherwise. */
  lanes: FlowLane[] | null;
}

