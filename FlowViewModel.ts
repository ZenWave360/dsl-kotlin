/**
 * TypeScript representation of FlowViewModel and related types
 * Converted from Kotlin source files in io.zenwave360.language.eventflow
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
// FlowIR - Canonical IR Types
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
  CONDITIONAL = "CONDITIONAL"
}

/**
 * A node in an event-driven flow (IR representation).
 */
export interface FlowNode {
  id: string;
  type: FlowNodeType;
  label: string;
  system: string | null;
  service: string | null;
  sourceRef: SourceRef;
}

/**
 * Directed relationship between two flow nodes (IR representation).
 */
export interface FlowEdge {
  id: string;
  source: string;
  target: string;
  type: FlowEdgeType;
  label?: string | null;
  sourceRef?: SourceRef | null;
}

/**
 * Canonical, language-agnostic representation of an event-driven flow.
 * This model is semantic, deterministic, and layout-agnostic.
 */
export interface FlowIR {
  nodes: FlowNode[];
  edges: FlowEdge[];
}

// ============================================================================
// FlowViewModel - View/Layout Types
// ============================================================================

export interface Point {
  x: number;
  y: number;
}

export interface Dimensions {
  width: number;
  height: number;
}

export interface FlowNodeView {
  id: string;
  type: FlowNodeType;
  label: string;
  position: Point;
  dimensions: Dimensions;
  system: string | null;
  service: string | null;
  sourceRef: SourceRef;
}

export interface FlowEdgeView {
  id: string;
  source: string;
  target: string;
  type: FlowEdgeType;
  label: string | null;
  sourceRef: SourceRef | null;
}

export interface FlowBounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface FlowSystemGroupView {
  systemName: string;
  bounds: FlowBounds;
}

export enum Direction {
  LR = "LR",
  TB = "TB"
}

export interface LayoutMetadata {
  engine: string;
  direction: Direction;
  rankSpacing: number;
  nodeSpacing: number;
}

export interface FlowViewModel {
  schema: string;
  nodes: FlowNodeView[];
  edges: FlowEdgeView[];
  systemGroups: FlowSystemGroupView[];
  layout: LayoutMetadata;
  bounds: FlowBounds;
}

/**
 * Factory function to create a FlowViewModel with default values
 */
export function createFlowViewModel(
  partial: Partial<FlowViewModel> & {
    nodes: FlowNodeView[];
    edges: FlowEdgeView[];
    systemGroups: FlowSystemGroupView[];
    layout: LayoutMetadata;
    bounds: FlowBounds;
  }
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

