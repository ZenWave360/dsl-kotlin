# ZDL/ZFL Formatter Specification

## Purpose

This document defines the first formatter specification for the `ZDL` and `ZFL` languages.

The formatter is intended primarily for `LSP format document`.

The design goal is a deterministic, syntax-driven formatter that preserves author intent as much as possible while normalizing whitespace consistently.

## Scope

This first version is intentionally limited.

Included:

- full-document formatting
- valid documents only
- whitespace normalization
- indentation normalization
- preservation of comments
- preservation of blank-line count
- preservation of declaration order
- preservation of import order
- preservation of quote style
- preservation of empty-block shape
- preservation of single-line versus multiline authored structures

Not included:

- range formatting
- malformed-document formatting
- semantic normalization
- sorting
- punctuation repair
- line wrapping
- long-line splitting
- canonicalization of equivalent syntax

## Non-Goals

The formatter must not:

- reorder imports
- reorder declarations
- reorder annotations
- reorder options
- change quote style
- collapse equivalent syntactic forms
- infer missing punctuation
- rewrite content from the semantic model
- aggressively reflow multiline content

## Source of Truth

The formatter must be syntax-driven.

The semantic model is explicitly not the source of truth.

The source of truth is:

1. the `ANTLR4` parse tree
2. the original token stream, including hidden-channel trivia

Comments and blank lines are treated as first-class formatting input and must be preserved from the original token stream/trivia stream.

The architectural requirement is:

- preserve concrete syntax trivia faithfully
- format from syntax, not semantics

## Formatting Contract

### Determinism

The formatter should be as deterministic as possible.

Hard idempotence is desirable but is not required as a formal guarantee for version 1.

The implementation should still aim for:

- same input text + same formatter version + same settings => same output
- repeated formatting should converge quickly, ideally in one pass

### Technical Style

There is one non-configurable formatting style, except for line width configuration which exists for future use but is not used yet for line splitting.

### Indentation

- indentation uses spaces
- nested blocks increase indentation by `4` spaces

### Horizontal Spacing

Horizontal spacing is normalized.

This includes:

- normalizing repeated spaces between tokens down to a single required separator
- normalizing spacing around braces where appropriate
- normalizing spacing between keywords, identifiers, annotations, and punctuation where the grammar expects separation

This does not include:

- changing spaces inside string literals
- changing spaces inside comment text
- changing quote delimiters

### Blank Lines

Blank-line count must be preserved.

Rules:

- preserve the authored number of blank lines between declarations and blocks
- preserve blank lines between comments and declarations
- preserve blank lines inside blocks
- trim trailing whitespace at line end
- do not preserve trailing blank lines at the end of file beyond the project’s standard final newline policy

### Comments

Comments are critical and must be preserved exactly in content and relative position.

Rules:

- preserve all comments
- preserve comment text exactly
- preserve comment order exactly
- preserve blank lines around comments
- comments move with the containing block when indentation changes
- indentation may be adjusted to match the surrounding block
- internal comment text must not be rewritten

This applies to:

- line comments
- block comments
- javadocs/doc comments
- suffix comments, if the syntax permits them

### Empty Blocks

Empty blocks must be preserved as authored.

Examples:

- `start X {}` stays `start X {}`
- `start X {` newline `}` stays multiline

The formatter must not expand or collapse empty blocks.

### Single-Line Versus Multiline Authored Forms

The formatter preserves authored structural shape.

Rules:

- arrays stay single-line if authored single-line
- objects stay single-line if authored single-line
- config-like values stay single-line if authored single-line
- multiline authored structures remain multiline

The formatter may fix indentation inside multiline structures but must not rewrite a single-line form into multiline or the reverse.

### Block Spacing

For version 1, block spacing should be minimally normalized.

Rules:

- preserve authored brace placement where possible
- do not move `{` to a new line if it was on the declaration line
- do not force same-line braces if the author wrote multiline braces
- normalize indentation within the block body
- preserve blank lines around block boundaries

This is intentionally conservative to avoid damaging author intent.

## Syntax Ownership Model

To preserve comments and blank lines correctly, the formatter must define trivia ownership.

Recommended model:

- tokens remain in original lexical order
- each token or syntax node can observe surrounding trivia
- formatting decisions are made by syntax-node handlers
- trivia is emitted in original sequence, with only whitespace normalization applied where allowed

Recommended trivia categories:

- leading spaces
- leading line breaks
- leading comments
- trailing spaces
- trailing comments

The formatter should prefer preserving token position semantics over trying to "reattach" comments logically.

If a comment cannot be attached to a syntax node unambiguously, its original token-stream position wins.

## Formatter Behavior by Category

### Imports

- preserve import order
- normalize horizontal spacing inside import syntax
- preserve blank lines between imports exactly as authored
- preserve quote style inside import values

### Declarations

- preserve declaration order
- normalize indentation inside declaration bodies
- preserve surrounding comments and blank lines

### Annotations and Options

- preserve order exactly
- preserve line structure exactly where practical
- normalize only required horizontal spacing

### Fields and Properties

- preserve field order
- normalize indentation
- normalize repeated inter-token spaces
- preserve comments and blank lines

### Flow Blocks

Examples:

- `flow`
- `start`
- `when`
- `end`
- `systems`
- `service`
- `commands`
- `events`

Rules:

- preserve declaration order
- preserve authored blank lines
- preserve comment placement
- normalize indentation and horizontal spacing

### End Outcomes in ZFL

- preserve authored outcome order
- preserve authored labels exactly
- preserve authored event order
- do not sort or rewrite outcome names

### Relationships and Nested Structures in ZDL

- preserve authored structure shape
- normalize indentation and token spacing only
- avoid canonical rewrites in version 1

## Error Handling

Version 1 only supports formatting valid documents.

If parsing fails or the concrete syntax structure is insufficiently reliable, the formatter should:

- return a formatting failure
- avoid partial destructive rewrites

Malformed-document best-effort formatting is explicitly deferred.

## Output Policy

The formatter emits a full replacement document for LSP `textDocument/formatting`.

Range formatting is out of scope for version 1.

## Proposed Architecture

### Pipeline

1. tokenize source text with `ANTLR4`
2. parse source text into `ANTLR4` parse tree
3. retain original token/trivia stream
4. walk syntax tree with node-specific formatting rules
5. emit formatted text using original token order and preserved trivia

### Preferred Internal Representation

Recommended internal components:

- `ANTLR4` syntax tree nodes
- original token list
- trivia spans between tokens
- formatting writer that controls:
  - indentation depth
  - normalized spaces
  - normalized line starts
  - trailing whitespace trimming

### Why Not the Semantic Model

The semantic model loses concrete syntax details that are required here:

- comment placement
- blank-line count
- quote choice
- exact authored block shape
- token-level trivia

Therefore, semantic formatting would violate core preservation requirements.

## Testing Strategy

Version 1 should include the following test categories.

### Golden File Tests

For representative ZDL and ZFL files:

- input file
- expected formatted output

These should include comment-heavy and blank-line-heavy examples.

### Idempotence Tests

For each valid sample:

- `format(input)` produces output
- `format(output)` should equal `output`

This is highly recommended even if not marketed as a formal hard guarantee.

### Parse-Format-Parse Equivalence

For each valid sample:

- parse original
- format
- parse formatted result
- assert that parse remains valid
- assert important syntax structure is preserved

This is syntax equivalence, not semantic normalization.

### Preservation Tests

Dedicated tests should verify:

- comments preserved exactly
- blank-line count preserved
- quote style preserved
- import order preserved
- declaration order preserved
- empty blocks preserved exactly
- single-line authored arrays remain single-line

### Negative Tests

For malformed inputs:

- verify formatter fails safely
- verify no destructive partial rewrite path is used

## Open Decisions

These should be confirmed before implementation:

1. final newline policy
2. exact horizontal-spacing rules around punctuation for each grammar construct
3. whether any constructs require alignment behavior

Current recommendation:

- indentation width: `4`
- final newline: exactly one newline at EOF
- no alignment columns in version 1

## Implementation Recommendation

Start with a conservative formatter.

Priority order:

1. trivia-preserving infrastructure
2. indentation normalization
3. horizontal spacing normalization
4. EOF and trailing-whitespace cleanup
5. syntax-specific refinements for ZDL
6. syntax-specific refinements for ZFL

Do not start with aggressive pretty-printing.

The first formatter should behave more like a safe structural normalizer than a code rewriter.
