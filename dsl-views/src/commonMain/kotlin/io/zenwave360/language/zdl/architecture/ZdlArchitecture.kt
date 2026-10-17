package io.zenwave360.language.zdl.architecture

/** A ZDL file given to the architecture reader. [path] identifies the file; it is never read from disk. */
data class ZdlSource(val path: String, val content: String)

/**
 * A module of the architecture: one ZDL file (a microservice or one module of a monolith).
 *
 * A [referenced] module has no source in the set: some ZDL in the set points to it through a `zdl client`.
 * It only holds the events and calls that were referenced.
 */
data class ArchModule(
    /** PascalCase, usable as a Mermaid node id. */
    val id: String,
    /** kebab-case, usable as a file name. */
    val slug: String,
    /** Display name. */
    val name: String,
    /** Normalized path of the ZDL file. */
    val path: String,
    val basePackage: String?,
    val referenced: Boolean,
)

enum class EventKind { INTERNAL, EXTERNAL }

/**
 * An event owned by [module] (a module id). External when the event has `@asyncapi` with a `topic`.
 * Events of a referenced module are [EventKind.INTERNAL]: its ZDL is not available to tell.
 */
data class ArchEvent(
    val module: String,
    val name: String,
    val kind: EventKind,
    val channel: String? = null,
    val topic: String? = null,
)

/** An external event outside every module: consumed through `@asyncapi(api: A, channel: C)` of an unknown provider. */
data class ArchForeignEvent(
    /** Channel id, also the label. */
    val channel: String,
    /** The API name used in `api: A`, shown as "Defined in". */
    val api: String,
)

/** Reference to an event: [module] is the owning module id, or null for a [ArchForeignEvent] whose channel is [name]. */
data class EventRef(val module: String?, val name: String)

enum class ConsumptionVia { LISTENER, ASYNCAPI }

data class ArchConsumption(
    val event: EventRef,
    /** Module id of the consumer. */
    val consumer: String,
    /** `Service.method` (or `Aggregate.command`) that consumes the event. */
    val method: String,
    val via: ConsumptionVia,
)

data class ArchCall(
    /** Module id of the caller. */
    val caller: String,
    /** Module id of the callee. */
    val callee: String,
    /** The command (method) name only, e.g. `reserveStock`. */
    val command: String,
    /** The full target as written, e.g. `InventoryService.reserveStock`. */
    val target: String,
    /** `Service.method` (or `Aggregate.command`) that declares the call. */
    val declaredOn: String,
)

/**
 * A service method or aggregate command of [module] (a module id). [service] is the service name, or the
 * aggregate name for a command declared inside an `aggregate` block. [events] are the events it emits
 * (`withEvents`, alternatives flattened, in order). A [query] changes nothing (see `ZdlCommand.query`).
 */
data class ArchCommand(
    val module: String,
    val service: String,
    val method: String,
    val events: List<String>,
    val query: Boolean,
)

/** Immutable architecture of a set of ZDL modules. Lists are sorted: modules by id, events by module and name. */
data class ZdlArchitecture(
    val modules: List<ArchModule>,
    val events: List<ArchEvent>,
    val foreignEvents: List<ArchForeignEvent>,
    val consumptions: List<ArchConsumption>,
    val calls: List<ArchCall>,
    /** `file: message`; the reader never throws. */
    val problems: List<String>,
    /** Commands of the non-referenced modules, in declaration order within each module (modules by id). */
    val commands: List<ArchCommand> = emptyList(),
) {
    fun module(id: String): ArchModule? = modules.firstOrNull { it.id == id }

    fun event(ref: EventRef): ArchEvent? = events.firstOrNull { it.module == ref.module && it.name == ref.name }

    fun commandsOf(moduleId: String): List<ArchCommand> = commands.filter { it.module == moduleId }

    fun eventsOf(moduleId: String): List<ArchEvent> = events.filter { it.module == moduleId }

    fun consumptionsOf(event: EventRef): List<ArchConsumption> = consumptions.filter { it.event == event }

    /** Distinct consumer module ids of the event, sorted. */
    fun consumersOf(event: EventRef): List<String> = consumptionsOf(event).map { it.consumer }.distinct().sorted()

    /** Module-owned events that nobody consumes. */
    fun eventsWithoutConsumers(): List<ArchEvent> =
        events.filter { e -> consumptions.none { it.event == EventRef(e.module, e.name) } }
}
