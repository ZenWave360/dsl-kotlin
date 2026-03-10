/**
 * TypeScript representation of FlowViewModel and related types
 * Converted from Kotlin source files in io.zenwave360.language.eventflow.view
 */

/**
 * Represents a precise location in a source file.
 * Lines and columns are 1-based.
 */
export interface SourceRef {
  file: string;
  line: number;
  column: number;
}

// ============================================================================
// Shared semantic enum types
// ============================================================================

/**
 * Semantic types of nodes in an event flow.
 */
export enum FlowNodeType {
  START = "START",
  COMMAND = "COMMAND",
  EVENT = "EVENT",
  POLICY = "POLICY",
  END = "END"
}

/**
 * Semantic meaning of a relationship between nodes.
 */
export enum FlowEdgeType {
  CAUSATION = "CAUSATION",
  TRIGGER = "TRIGGER",
  CONDITIONAL = "CONDITIONAL",
  ERROR = "ERROR"
}

// ============================================================================
// Unified FlowViewModel types (semantic + optional layout)
// ============================================================================

export interface Point {
  x: number;
  y: number;
}

export interface Dimensions {
  width: number;
  height: number;
}

/**
 * A node in an event-driven flow.
 *
 * Semantic properties are always populated. Layout properties (position,
 * dimensions) are null until the layout engine has been applied.
 */
export interface FlowNode {
  id: string;
  type: FlowNodeType;
  label: string;
  system: string | null;
  service: string | null;
  sourceRef: SourceRef;
  /** Absolute position (x, y) in the canvas. Null until layout is applied. */
  position?: Point | null;
  /** Width and height of the node. Null until layout is applied. */
  dimensions?: Dimensions | null;
}

/**
 * Directed relationship between two flow nodes.
 */
export interface FlowEdge {
  id: string;
  source: string;
  target: string;
  type: FlowEdgeType;
  label?: string | null;
  sourceRef?: SourceRef | null;
}

export interface FlowBounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

/**
 * Visual grouping of nodes by system (swim lane).
 * Each system gets its own horizontal lane in the timeline layout.
 */
export interface FlowSystemGroupView {
  /** Name of the system (e.g., "Subscription", "Payments", "Billing") */
  systemName: string;
  /** Bounding box that encompasses all nodes in this system */
  bounds: FlowBounds;
}

export enum Direction {
  LR = "LR",
  TB = "TB"
}

/**
 * Layout metadata describing how the flow was positioned.
 */
export interface LayoutMetadata {
  /** Layout engine identifier. Current value: "zfl-timeline" */
  engine: string;
  /** Layout direction: LR (left-to-right) or TB (top-to-bottom) */
  direction: Direction;
  /** Horizontal spacing between timeline positions (x-axis) */
  rankSpacing: number;
  /** Vertical spacing between nodes within the same lane (y-axis) */
  nodeSpacing: number;
}

/**
 * Unified view model for rendering an event-driven flow diagram.
 *
 * Before layout is applied: nodes and edges are populated, layout/bounds/systemGroups are null.
 * After layout is applied: all fields are populated and nodes have position/dimensions set.
 */
export interface FlowViewModel {
  /** Schema version identifier (e.g., "zfl.eventflow.view@1") */
  schema: string;
  /** All nodes in the flow (with optional positions after layout) */
  nodes: FlowNode[];
  /** All edges connecting the nodes */
  edges: FlowEdge[];
  /** Layout algorithm metadata. Null until layout is applied. */
  layout?: LayoutMetadata | null;
  /** Overall bounding box of the entire flow diagram. Null until layout is applied. */
  bounds?: FlowBounds | null;
  /** System groupings (swim lanes). Null until layout is applied. */
  systemGroups?: FlowSystemGroupView[] | null;
}

/**
 * Factory function to create a FlowViewModel with default values
 */
export function createFlowViewModel(
  partial: Partial<FlowViewModel> & { nodes: FlowNode[]; edges: FlowEdge[] }
): FlowViewModel {
  return {
    schema: "zfl.eventflow.view@1",
    ...partial
  };
}

// ============================================================================
// ZFL Semantic Model Types
// ============================================================================

export enum Severity {
  INFO = "INFO",
  WARNING = "WARNING",
  ERROR = "ERROR"
}

export interface ZflSemanticDiagnostic {
  message: string;
  severity: Severity;
  sourceRef: SourceRef | null;
}

export interface ZflActor {
  name: string;
  sourceRef: SourceRef | null;
}

export interface ZflCommand {
  name: string;
  system: string | null;
  service: string | null;
  actor: string | null;
  sourceRef: SourceRef;
}

export interface ZflEvent {
  name: string;
  description: string | null;
  system: string | null;
  service: string | null;
  isError: boolean;
  sourceRef: SourceRef;
}

export interface ZflPolicy {
  description: string;
  triggers: string[];
  condition: string | null;
  command: string;
  events: string[];
  sourceRef: SourceRef;
}

export interface ZflStart {
  description: string;
  name: string;
  actor: string | null;
  timer: string | null;
  system: string | null;
  sourceRef: SourceRef;
}

export interface ZflEnd {
  completed: string[];
  suspended: string[];
  cancelled: string[];
  sourceRef: SourceRef;
}

export interface ZflFlow {
  name: string;
  description: string;
  starts: ZflStart[];
  policies: ZflPolicy[];
  commands: ZflCommand[];
  events: ZflEvent[];
  end: ZflEnd;
}

export interface ZflService {
  name: string;
  boundedContext: boolean;
}

export interface ZflSystem {
  name: string;
  services: Record<string, ZflService>;
}

export interface ZflSemanticModel {
  flows: ZflFlow[];
  systems: Record<string, ZflSystem>;
  actors: Record<string, ZflActor>;
  diagnostics: ZflSemanticDiagnostic[];
}

